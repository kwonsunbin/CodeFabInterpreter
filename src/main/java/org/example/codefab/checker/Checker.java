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
 * Rules implemented:
 * 1. Duplicate variable declaration in the same block scope.
 * 2. Self-reference in initializer (var a = a;).
 * <p>
 * To add new rules: implement the relevant visitXxx methods and call
 * result.addError() / result.addWarning() as needed.
 */
public class Checker implements Stmt.Visitor<Void>, Expr.Visitor<Void> {

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

        for (int i = 0; i < program.size(); i++) {
            Stmt stmt = program.get(i);
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
        // TODO: check for duplicate, then scope.declare()
        Scope scope = scopes.isEmpty() ? globalScope :
                scopes.peek();

        if (scope.has(name.origin())) {
            result.addError(name.line(), "Already a variable with this name in this scope.");
            return;
        }
        scope.declare(name.origin());
//        throw new UnsupportedOperationException("TODO: implement declare");
    }

    /**
     * Phase 2: marks name as DEFINED (initializer fully resolved).
     */
    private void define(Token name) {
        scopes.peek().define(name.origin());
    }

    // ── Statement visitors (DFS) ──────────────────────────────────────────────

    @Override
    public Void visitVar(Stmt.Var stmt) {
        // TODO: two-phase declare → evaluate initializer → define
        declare(stmt.name);
        if (stmt.initializer != null) evaluate(stmt.initializer);
        define(stmt.name);
        return null;
//        throw new UnsupportedOperationException("TODO: implement visitVar");
    }

    @Override
    public Void visitIf(Stmt.If stmt) {
        // TODO
        throw new UnsupportedOperationException("TODO: implement visitIf");
    }

    @Override
    public Void visitFor(Stmt.For stmt) {
        // TODO: open scope for for-init, walk all clauses and body, close scope
        throw new UnsupportedOperationException("TODO: implement visitFor");
    }

    @Override
    public Void visitPrint(Stmt.Print stmt) {
        // TODO
        throw new UnsupportedOperationException("TODO: implement visitPrint");
    }

    @Override
    public Void visitBlock(Stmt.Block stmt) {
        // TODO: beginScope, walk statements, endScope
        beginScope();
        for (Stmt s : stmt.statements) execute(s);
        endScope();
        return null;
//        throw new UnsupportedOperationException("TODO: implement visitBlock");
    }

    @Override
    public Void visitExpression(Stmt.Expression stmt) {
        // TODO
        throw new UnsupportedOperationException("TODO: implement visitExpression");
    }

    // ── Expression visitors (DFS) ─────────────────────────────────────────────

    @Override
    public Void visitVariable(Expr.Variable expr) {
        // TODO: detect self-reference (DECLARING state in current scope)
        // Self-reference check: if the name is DECLARING in the current scope,
        // the variable's own initializer is referencing it before it's defined.
        Scope current = scopes.peek();
        if (current.has(expr.name.origin())
                && current.state(expr.name.origin()) == Scope.State.DECLARING) {
            result.addError(expr.name.line(),
                    "Can't read local variable in initializer.");
        }
        return null;
//        throw new UnsupportedOperationException("TODO: implement visitVariable");
    }

    @Override
    public Void visitBinary(Expr.Binary expr) {
        // TODO
        throw new UnsupportedOperationException("TODO: implement visitBinary");
    }

    @Override
    public Void visitLogical(Expr.Logical expr) {
        // TODO
        throw new UnsupportedOperationException("TODO: implement visitLogical");
    }

    @Override
    public Void visitComparison(Expr.Comparison expr) {
        // TODO
        throw new UnsupportedOperationException("TODO: implement visitComparison");
    }

    @Override
    public Void visitUnary(Expr.Unary expr) {
        // TODO
        throw new UnsupportedOperationException("TODO: implement visitUnary");
    }

    @Override
    public Void visitGrouping(Expr.Grouping expr) {
        // TODO
        throw new UnsupportedOperationException("TODO: implement visitGrouping");
    }

    @Override
    public Void visitLiteral(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitAssign(Expr.Assign expr) {
        // TODO
        throw new UnsupportedOperationException("TODO: implement visitAssign");
    }

    // ── Internal dispatch ─────────────────────────────────────────────────────

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    private void evaluate(Expr expr) {
        expr.accept(this);
    }
}
