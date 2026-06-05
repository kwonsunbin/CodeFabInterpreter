package org.example.codefab.checker;


import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.example.codefab.token.Token;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

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
     * Returns true if name is reachable as DEFINED from the current scope chain.
     * DECLARING state is excluded — the variable exists but is not yet usable.
     */
    private boolean isVisible(String name) {
        for (Scope scope : scopes) {
            if (scope.has(name) && scope.state(name) == Scope.State.DEFINED) return true;
        }
        return false;
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

    // Stubs — full semantic rules for functions/arrays will be added when
    // the corresponding Parser support lands.
    @Override
    public Void visitFunction(Stmt.Function stmt) { return null; }

    @Override
    public Void visitReturn(Stmt.Return stmt) { return null; }

    // ── Expression scanner ────────────────────────────────────────────────────
    // Walks expression subtrees only to enforce variable-scope rules.
    // No runtime evaluation is performed here.

    private void scanExpr(Expr expr) {
        switch (expr) {
            case Expr.Variable v -> {
                String name = v.name.origin();
                Scope current = scopes.peek();
                // Rule 2: self-reference — variable read during its own initializer
                if (current.has(name) && current.state(name) == Scope.State.DECLARING) {
                    result.addError(v.name.line(), "Can't read local variable in initializer.");
                    return; // self-ref already reported; skip undeclared check
                }
                // Rule 3: undeclared variable read (includes forward ref, block scope violation)
                if (!isVisible(name)) {
                    result.addError(v.name.line(), "Undefined variable '" + name + "'.");
                }
            }
            case Expr.Assign a -> {
                // Rule 4: assignment to undeclared variable
                if (!isVisible(a.name.origin())) {
                    result.addError(a.name.line(), "Undefined variable '" + a.name.origin() + "'.");
                }
                scanExpr(a.value);
            }
            case Expr.Binary b     -> { scanExpr(b.left); scanExpr(b.right); }
            case Expr.Logical l    -> { scanExpr(l.left); scanExpr(l.right); }
            case Expr.Comparison c -> { scanExpr(c.left); scanExpr(c.right); }
            case Expr.Unary u      -> scanExpr(u.operand);
            case Expr.Grouping g   -> scanExpr(g.expression);
            case Expr.Call c       -> { scanExpr(c.callee); for (Expr a : c.arguments) scanExpr(a); }
            case Expr.ArrayLiteral al -> { for (Expr e : al.elements) scanExpr(e); }
            case Expr.ArrayIndex ai -> { scanExpr(ai.target); scanExpr(ai.index); }
            case Expr.Literal ignored -> {}
        }
    }

    // ── Internal dispatch ─────────────────────────────────────────────────────

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }
}
