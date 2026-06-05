package org.example.codefab.checker;

import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.example.codefab.token.Token;
import org.example.codefab.token.TokenType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * CheckerFold: Checker와 동일한 정적 분석 + 상수 폴딩.
 * depth 바인딩은 포함하지 않는다 (CheckerDepth 참고).
 *
 * 상수 폴딩: 리터럴만으로 구성되어 런타임 이전에 값이 100% 확정되는 표현식을
 * Expr.foldedValue에 미리 계산해 기록한다.
 * 변수 참조, 함수 호출, 또는 0 나누기가 포함된 표현식은 폴딩하지 않는다.
 *
 * scanExpr()은 스코프 검사와 폴딩을 단일 AST 순회로 동시에 수행한다.
 * 반환값: 컴파일 타임 확정 값(폴딩 가능) 또는 null(런타임 의존)
 */
public class CheckerFold implements Stmt.Visitor<Void> {

    private final Scope globalScope = new Scope();
    private final Deque<Scope> scopes = new ArrayDeque<>();

    private CheckResult result;

    public CheckResult check(List<Stmt> program) {
        result = new CheckResult();
        if (scopes.isEmpty()) scopes.push(globalScope);

        for (Stmt stmt : program) {
            execute(stmt);
        }

        return result;
    }

    // ── Scope management ─────────────────────────────────────────────────────

    private void beginScope() { scopes.push(new Scope()); }
    private void endScope()   { scopes.pop(); }

    private void declare(Token name) {
        Scope scope = scopes.peek();
        if (scope.has(name.origin())) {
            result.addError(name.line(), "Already a variable with this name in this scope.");
            return;
        }
        scope.declare(name.origin());
    }

    private void define(Token name) {
        scopes.peek().define(name.origin());
    }

    private boolean isVisible(String name) {
        for (Scope scope : scopes) {
            if (scope.has(name) && scope.state(name) == Scope.State.DEFINED) return true;
        }
        return false;
    }

    // ── Statement visitors ────────────────────────────────────────────────────

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
    public Void visitFuncDecl(Stmt.FuncDecl stmt) {
        Scope cur = scopes.peek();
        if (!cur.has(stmt.name.origin())) {
            cur.declare(stmt.name.origin());
            cur.define(stmt.name.origin());
        }
        beginScope();
        for (Token param : stmt.params) {
            Scope s = scopes.peek();
            s.declare(param.origin());
            s.define(param.origin());
        }
        for (Stmt s : stmt.body.statements) execute(s);
        endScope();
        return null;
    }

    @Override
    public Void visitReturn(Stmt.Return stmt) {
        if (stmt.value != null) scanExpr(stmt.value);
        return null;
    }

    // ── Expression scanner + folder ───────────────────────────────────────────
    // 스코프 검사와 상수 폴딩을 단일 순회로 처리한다.
    // 반환값: 컴파일 타임 확정 값 또는 null(런타임 의존)

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
                    if (lo.op.type() == TokenType.OR  &&  isTruthy(left))  { lo.foldedValue = left;  yield left; }
                    if (lo.op.type() == TokenType.AND && !isTruthy(left))  { lo.foldedValue = left;  yield left; }
                }
                if (left != null && right != null) { lo.foldedValue = right; yield right; }
                yield null;
            }

            case Expr.Variable v -> {
                String name    = v.name.origin();
                Scope  current = scopes.peek();
                if (current.has(name) && current.state(name) == Scope.State.DECLARING) {
                    result.addError(v.name.line(), "Can't read local variable in initializer.");
                    yield null;
                }
                if (!isVisible(name)) {
                    result.addError(v.name.line(), "Undefined variable '" + name + "'.");
                }
                yield null; // 런타임 값 — 폴딩 불가
            }

            case Expr.Assign a -> {
                if (!isVisible(a.name.origin())) {
                    result.addError(a.name.line(), "Undefined variable '" + a.name.origin() + "'.");
                }
                scanExpr(a.value);
                yield null; // 부수 효과 있음 — 폴딩 불가
            }

            case Expr.Call c -> {
                scanExpr(c.callee);
                for (Expr arg : c.arguments) scanExpr(arg);
                yield null; // 함수 호출은 런타임 의존 — 폴딩 불가
            }

            case Expr.ArrayLiteral al -> {
                for (Expr e : al.elements) scanExpr(e);
                yield null; // 배열 리터럴은 폴딩 범위 밖
            }

            case Expr.ArrayIndex ai -> {
                scanExpr(ai.target);
                scanExpr(ai.index);
                yield null; // 인덱스 접근은 런타임 의존
            }
        };
    }

    // ── Fold helpers ──────────────────────────────────────────────────────────

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

    // ── Internal dispatch ─────────────────────────────────────────────────────

    private void execute(Stmt stmt) { stmt.accept(this); }
}
