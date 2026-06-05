package org.example.codefab.checker;


import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.example.codefab.token.Token;
import org.example.codefab.token.TokenType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checker Unit: static semantic analysis via recursive DFS (Visitor pattern).
 * Collects ALL diagnostics in one pass — does not throw.
 * <p>
 * Semantic rules implemented (detectable without execution):
 * 1. Duplicate variable declaration in the same block scope.
 * 2. Self-reference in initializer (var a = a;).
 * 3. Use of undeclared variable (read context).
 * 4. Assignment to undeclared variable (write context).
 * <p>
 * Runtime errors (type mismatch, division by zero) are intentionally left
 * to Executor — they require actual values and cannot be detected statically.
 */
public class Checker implements Stmt.Visitor<Void> {

    // Persistent global scope — survives across REPL submissions so
    // previously declared globals are visible and re-declaration is caught.
    private final Scope globalScope = new Scope();
    private final Deque<Scope> scopes = new ArrayDeque<>();

    private CheckResult result;

    private int functionDepth = 0;
    private final Map<String, Integer> functionArities = new HashMap<>();
    private final Set<String>          arrayNames      = new HashSet<>();
    private final Map<String, Integer> arraySizes      = new HashMap<>();

    /**
     * Entry point — returns all collected diagnostics.
     */
    public CheckResult check(List<Stmt> program) {
        result = new CheckResult();
        if (scopes.isEmpty()) scopes.push(globalScope);

        for (Stmt stmt : program) {
            execute(stmt);
        }

        return result;
    }

    // ── Scope management ─────────────────────────────────────────────────────

    private void beginScope() {
        scopes.push(new Scope());
    }

    private void endScope() {
        scopes.pop();
    }

    /**
     * Phase 1 of two-phase declare/define: marks name as DECLARING.
     */
    private void declare(Token name) {
        Scope scope = scopes.peek();

        if (scope.has(name.origin())) {
            result.addError(name.line(), "Already a variable with this name in this scope.");
            return;
        }
        scope.declare(name.origin());
    }

    /**
     * Phase 2: marks name as DEFINED (initializer fully resolved).
     */
    private void define(Token name) {
        scopes.peek().define(name.origin());
    }

    /**
     * Returns the scope distance to the nearest DEFINED binding of name,
     * or -1 if name is not visible. Distance 0 = current scope, 1 = one hop up, etc.
     * DECLARING state is excluded — the variable exists but is not yet usable.
     */
    private int findScopeDistance(String name) {
        int distance = 0;
        for (Scope scope : scopes) {
            if (scope.has(name) && scope.state(name) == Scope.State.DEFINED) return distance;
            distance++;
        }
        return -1;
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
    // Enforces variable-scope rules, records depth on Variable/Assign nodes,
    // and pre-computes constant expressions via constant folding.
    // Returns the compile-time value if the expression is fully constant, null otherwise.

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
                String name    = v.name.origin();
                Scope  current = scopes.peek();
                // Rule 2: self-reference — variable read during its own initializer
                if (current.has(name) && current.state(name) == Scope.State.DECLARING) {
                    result.addError(v.name.line(), "Can't read local variable in initializer.");
                    yield null;
                }
                // Rule 3 + static binding
                int depth = findScopeDistance(name);
                if (depth < 0) {
                    result.addError(v.name.line(), "Undefined variable '" + name + "'.");
                } else {
                    v.depth = depth;
                }
                yield null;
            }

            case Expr.Assign a -> {
                // Rule 4 + static binding
                int depth = findScopeDistance(a.name.origin());
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
                int    depth = findScopeDistance(name);
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
                int    depth = findScopeDistance(name);
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
