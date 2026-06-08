package org.example.codefab.checker;


import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.example.codefab.executor.Environment;
import org.example.codefab.token.Token;
import org.example.codefab.token.TokenType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checker Unit: static semantic analysis via recursive DFS (Visitor pattern).
 * Collects ALL diagnostics in one pass — does not throw.
 * <p>
 * Scope tracking은 Executor와 <b>동일한 {@link Environment}</b>를 사용한다.
 * Pipeline이 생성한 전역 Environment를 Checker가 주입받아 변수 선언/가시성을 분석하고,
 * 같은 인스턴스를 Executor가 주입받아 런타임 값을 채운다. (예전의 별도 {@code Scope}
 * 클래스를 제거하고 두 단계가 하나의 스코프 구조를 공유하도록 통합.)
 * <p>
 * Semantic rules implemented (detectable without execution):
 * 1. Duplicate variable declaration in the same block scope.
 * 2. Self-reference in initializer (var a = a;).
 * 3. Use of undeclared variable (read context).
 * 4. Assignment to undeclared variable (write context).
 * 5. Function rules: top-level return, call arity, calling a non-function.
 * 6. Array rules: indexing a non-array, non-numeric / out-of-bounds index, bad size.
 * <p>
 * Runtime errors (type mismatch, division by zero) are intentionally left
 * to Executor — they require actual values and cannot be detected statically.
 */
public class Checker implements Stmt.Visitor<Void> {

    // 전역 스코프는 이 Environment 안에서 REPL 세션 동안 지속된다.
    private final Environment env;

    private CheckResult result;

    // 함수/배열 의미 검사용 메타데이터 (REPL 세션 동안 Checker 인스턴스에 지속).
    private int functionDepth = 0;
    private final Map<String, Integer> functionArities = new HashMap<>();
    private final Set<String>          arrayNames      = new HashSet<>();
    private final Map<String, Integer> arraySizes      = new HashMap<>();

    /** 독립 실행/테스트용 — 자체 Environment 생성. */
    public Checker() { this(new Environment()); }

    /** Pipeline이 Executor와 공유하는 Environment를 주입한다. */
    public Checker(Environment env) { this.env = env; }

    /**
     * Entry point — returns all collected diagnostics.
     */
    public CheckResult check(List<Stmt> program) {
        result = new CheckResult();
        for (Stmt stmt : program) {
            execute(stmt);
        }
        return result;
    }

    // ── Scope management (Environment 위임) ────────────────────────────────────

    private void beginScope() { env.pushScope(); }
    private void endScope()   { env.popScope(); }

    /** Phase 1: 현재 스코프에 선언 (이미 있으면 중복 선언 에러). */
    private void declare(Token name) {
        if (env.existsInCurrentScope(name.origin())) {
            result.addError(name.line(), "Already a variable with this name in this scope.");
            return;
        }
        env.declare(name.origin());
    }

    /** Phase 2: 초기화식 분석 완료 → 사용 가능으로 표시. */
    private void define(Token name) {
        env.markDefined(name.origin());
    }

    // ── Statement visitors (DFS) ──────────────────────────────────────────────

    @Override
    public Void visitVar(Stmt.Var stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) scanExpr(stmt.initializer);
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitIf(Stmt.If stmt) {
        scanExpr(stmt.condition);
        execute(stmt.thenBranch);
        if (stmt.elseBranch != null) execute(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitFor(Stmt.For stmt) {
        beginScope();
        if (stmt.initializer != null) execute(stmt.initializer);
        if (stmt.condition   != null) scanExpr(stmt.condition);
        if (stmt.increment   != null) scanExpr(stmt.increment);
        execute(stmt.body);
        endScope();
        return null;
    }

    @Override
    public Void visitPrint(Stmt.Print stmt) {
        scanExpr(stmt.expression);
        return null;
    }

    @Override
    public Void visitBlock(Stmt.Block stmt) {
        beginScope();
        for (Stmt s : stmt.statements) execute(s);
        endScope();
        return null;
    }

    @Override
    public Void visitExpression(Stmt.Expression stmt) {
        scanExpr(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunction(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);
        functionArities.put(stmt.name.origin(), stmt.params.size());
        beginScope();
        functionDepth++;
        for (Token param : stmt.params) {
            declare(param);
            define(param);
        }
        for (Stmt s : stmt.body) execute(s);
        functionDepth--;
        endScope();
        return null;
    }

    @Override
    public Void visitReturn(Stmt.Return stmt) {
        if (functionDepth == 0) {
            result.addError(stmt.keyword.line(), "Can't return from top-level code.");
        }
        if (stmt.value != null) scanExpr(stmt.value);
        return null;
    }

    @Override
    public Void visitArrayDecl(Stmt.ArrayDecl stmt) {
        declare(stmt.name);
        if (stmt.size instanceof Expr.Literal lit && !(lit.value instanceof Double)) {
            result.addError(stmt.name.line(), "Array size must be a number.");
        }
        scanExpr(stmt.size);
        arrayNames.add(stmt.name.origin());
        if (stmt.size instanceof Expr.Literal lit && lit.value instanceof Double d) {
            arraySizes.put(stmt.name.origin(), d.intValue());
        } else {
            arraySizes.put(stmt.name.origin(), -1);
        }
        define(stmt.name);
        return null;
    }

    // ── Expression scanner ────────────────────────────────────────────────────
    // 변수 스코프 규칙을 검사하고, 런타임 이전에 100% 확정되는 상수식을 미리 계산한다.
    // 상수면 그 값을, 아니면 null을 반환한다.

    private Object scanExpr(Expr expr) {
        return switch (expr) {

            case Expr.Literal l -> l.value;

            case Expr.Grouping g -> {
                Object inner = scanExpr(g.expression);
                if (inner != null) g.foldedValue = inner;
                yield inner;
            }

            case Expr.Unary u -> {
                Object operand = scanExpr(u.operand);
                Object folded  = foldUnary(u.op, operand);
                if (folded != null) u.foldedValue = folded;
                yield folded;
            }

            case Expr.Binary b -> {
                Object left   = scanExpr(b.left);
                Object right  = scanExpr(b.right);
                Object folded = foldBinary(b.op, left, right);
                if (folded != null) b.foldedValue = folded;
                yield folded;
            }

            case Expr.Comparison c -> {
                Object left   = scanExpr(c.left);
                Object right  = scanExpr(c.right);
                Object folded = foldComparison(c.op, left, right);
                if (folded != null) c.foldedValue = folded;
                yield folded;
            }

            case Expr.Logical lo -> {
                Object left  = scanExpr(lo.left);
                Object right = scanExpr(lo.right); // 스코프 검사를 위해 항상 순회
                if (left != null) {
                    if (lo.op.type() == TokenType.OR  &&  isTruthy(left)) { lo.foldedValue = left;  yield left; }
                    if (lo.op.type() == TokenType.AND && !isTruthy(left)) { lo.foldedValue = left;  yield left; }
                }
                if (left != null && right != null) { lo.foldedValue = right; yield right; }
                yield null;
            }

            case Expr.Variable v -> {
                String name = v.name.origin();
                // Rule 2: self-reference — 자기 초기화식에서 자신을 읽음
                if (env.isDeclaringInCurrentScope(name)) {
                    result.addError(v.name.line(), "Can't read local variable in initializer.");
                    yield null;
                }
                // Rule 3: 미정의 변수 읽기 + 정적 바인딩 depth 기록
                int depth = env.distanceOf(name);
                if (depth < 0) {
                    result.addError(v.name.line(), "Undefined variable '" + name + "'.");
                } else {
                    v.depth = depth;
                }
                yield null;
            }

            case Expr.Assign a -> {
                // Rule 4: 미정의 변수 할당 + 정적 바인딩 depth 기록
                int depth = env.distanceOf(a.name.origin());
                if (depth < 0) {
                    result.addError(a.name.line(), "Undefined variable '" + a.name.origin() + "'.");
                } else {
                    a.depth = depth;
                }
                scanExpr(a.value);
                yield null;
            }

            case Expr.Call c -> {
                scanExpr(c.callee);
                for (Expr arg : c.arguments) scanExpr(arg);
                // Rule 5: 함수 호출 검사 (해석된 변수를 호출 대상으로 할 때만)
                if (c.callee instanceof Expr.Variable v && v.depth >= 0) {
                    String name = v.name.origin();
                    if (arrayNames.contains(name)) {
                        result.addError(v.name.line(), "'" + name + "' is an array, not a function.");
                    } else if (!functionArities.containsKey(name)) {
                        result.addError(v.name.line(), "'" + name + "' is not a function.");
                    } else {
                        int expected = functionArities.get(name);
                        int actual   = c.arguments.size();
                        if (expected != actual) {
                            result.addError(c.paren.line(),
                                "'" + name + "' expects " + expected + " argument(s) but got " + actual + ".");
                        }
                    }
                }
                yield null;
            }

            case Expr.ArrayGet ag -> {
                String name  = ag.name.origin();
                int    depth = env.distanceOf(name);
                if (depth < 0) {
                    result.addError(ag.name.line(), "Undefined variable '" + name + "'.");
                } else if (!arrayNames.contains(name)) {
                    result.addError(ag.name.line(), "'" + name + "' is not an array.");
                }
                checkArrayIndex(ag.name, ag.index, name, depth);
                yield null;
            }

            case Expr.ArraySet as -> {
                String name  = as.name.origin();
                int    depth = env.distanceOf(name);
                if (depth < 0) {
                    result.addError(as.name.line(), "Undefined variable '" + name + "'.");
                } else if (!arrayNames.contains(name)) {
                    result.addError(as.name.line(), "'" + name + "' is not an array.");
                }
                checkArrayIndex(as.name, as.index, name, depth);
                scanExpr(as.value);
                yield null;
            }
        };
    }

    // ── Constant fold helpers ─────────────────────────────────────────────────

    private Object foldUnary(Token op, Object operand) {
        if (operand == null) return null;
        return switch (op.type()) {
            case MINUS -> operand instanceof Double d  ? -d  : null;
            case BANG  -> operand instanceof Boolean b ? !b  : null;
            default    -> null;
        };
    }

    private Object foldBinary(Token op, Object left, Object right) {
        if (left == null || right == null) return null;
        return switch (op.type()) {
            case PLUS  -> {
                if (left instanceof Double l && right instanceof Double r) yield l + r;
                if (left instanceof String l && right instanceof String r) yield l + r;
                yield null;
            }
            case MINUS -> left instanceof Double l && right instanceof Double r ? l - r : null;
            case STAR  -> left instanceof Double l && right instanceof Double r ? l * r : null;
            case SLASH -> {
                if (!(left instanceof Double l && right instanceof Double r)) yield null;
                if (r == 0) yield null; // 0 나누기: 런타임 에러로 위임
                yield l / r;
            }
            default -> null;
        };
    }

    private Object foldComparison(Token op, Object left, Object right) {
        if (!(left instanceof Double l && right instanceof Double r)) return null;
        return switch (op.type()) {
            case GREATER       -> l > r;
            case GREATER_EQUAL -> l >= r;
            case LESS          -> l < r;
            case LESS_EQUAL    -> l <= r;
            default            -> null;
        };
    }

    private boolean isTruthy(Object value) {
        if (value == null)              return false;
        if (value instanceof Boolean b) return b;
        return true;
    }

    /** 배열 인덱스 정적 검사: 숫자/비음 정수 여부, 리터럴이면 범위 초과 여부. */
    private void checkArrayIndex(Token nameToken, Expr indexExpr, String name, int depth) {
        scanExpr(indexExpr);
        if (!(indexExpr instanceof Expr.Literal lit)) return;
        if (!(lit.value instanceof Double)) {
            result.addError(nameToken.line(), "Array index must be a number.");
            return;
        }
        double idx = (Double) lit.value;
        if (idx < 0 || idx != Math.floor(idx)) {
            result.addError(nameToken.line(), "Array index must be a non-negative integer.");
            return;
        }
        if (depth < 0 || !arrayNames.contains(name)) return;
        Integer size = arraySizes.get(name);
        if (size != null && size >= 0 && (int) idx >= size) {
            result.addError(nameToken.line(),
                "Array index " + (int) idx + " is out of bounds for array of size " + size + ".");
        }
    }

    // ── Internal dispatch ─────────────────────────────────────────────────────

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }
}
