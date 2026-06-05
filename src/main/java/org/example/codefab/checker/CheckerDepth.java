package org.example.codefab.checker;

import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.example.codefab.token.Token;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * CheckerDepth: Checker와 동일한 정적 분석을 수행하면서,
 * 변수 참조(Variable, Assign)에 스코프 거리(depth)를 기록한다.
 * Executor가 depth를 이용해 Environment를 직접 찾아가도록 하는 정적 바인딩을 지원한다.
 * <p>
 * depth 의미: 0 = 현재 스코프, N = N단계 상위 스코프
 * 미발견(-1)인 경우 에러로 기록되며 depth는 기본값 -1을 유지한다.
 */
public class CheckerDepth implements Stmt.Visitor<Void> {

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

    /**
     * 현재 스코프 체인에서 name이 DEFINED 상태로 선언된 스코프까지의 거리를 반환한다.
     * DECLARING 상태는 제외 — 초기화 중인 변수는 아직 사용 불가.
     *
     * @return 0-based 거리, 미발견 시 -1
     */
    private int findScopeDistance(String name) {
        int distance = 0;
        for (Scope scope : scopes) {
            if (scope.has(name) && scope.state(name) == Scope.State.DEFINED) return distance;
            distance++;
        }
        return -1;
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

    // ── Expression scanner ────────────────────────────────────────────────────

    private void scanExpr(Expr expr) {
        switch (expr) {
            case Expr.Variable v -> {
                String name = v.name.origin();
                Scope current = scopes.peek();
                if (current.has(name) && current.state(name) == Scope.State.DECLARING) {
                    result.addError(v.name.line(), "Can't read local variable in initializer.");
                    return;
                }
                int depth = findScopeDistance(name);
                if (depth < 0) {
                    result.addError(v.name.line(), "Undefined variable '" + name + "'.");
                } else {
                    v.depth = depth;
                }
            }
            case Expr.Assign a -> {
                int depth = findScopeDistance(a.name.origin());
                if (depth < 0) {
                    result.addError(a.name.line(), "Undefined variable '" + a.name.origin() + "'.");
                } else {
                    a.depth = depth;
                }
                scanExpr(a.value);
            }
            case Expr.Binary b     -> { scanExpr(b.left); scanExpr(b.right); }
            case Expr.Logical l    -> { scanExpr(l.left); scanExpr(l.right); }
            case Expr.Comparison c -> { scanExpr(c.left); scanExpr(c.right); }
            case Expr.Unary u      -> scanExpr(u.operand);
            case Expr.Grouping g   -> scanExpr(g.expression);
            case Expr.Literal ignored -> {}
        }
    }

    // ── Internal dispatch ─────────────────────────────────────────────────────

    private void execute(Stmt stmt) { stmt.accept(this); }
}
