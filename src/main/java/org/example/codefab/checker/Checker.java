package org.example.codefab.checker;


import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.example.codefab.token.Token;

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

    private final ScopeStack scopeStack = new ScopeStack();
    private CheckResult result;

    /**
     * Entry point — returns all collected diagnostics.
     */
    public CheckResult check(List<Stmt> program) {
        result = new CheckResult();
        scopeStack.initialize();

        for (Stmt stmt : program) {
            execute(stmt);
        }

        return result;
    }

    // ── Rule helpers ──────────────────────────────────────────────────────────

    /**
     * Phase 1 of two-phase declare/define: marks name as DECLARING.
     */
    private void declare(Token name) {
        if (scopeStack.has(name.origin())) {
            result.addError(name.line(), "Already a variable with this name in this scope.");
            return;
        }
        scopeStack.declare(name.origin());
    }

    /**
     * Phase 2: marks name as DEFINED (initializer fully resolved).
     */
    private void define(Token name) {
        scopeStack.define(name.origin());
    }

    // ── Statement visitors (DFS) ──────────────────────────────────────────────

    @Override
    public Void visitVar(Stmt.Var stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) evaluate(stmt.initializer);
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitIf(Stmt.If stmt) {
        evaluate(stmt.condition);
        execute(stmt.thenBranch);
        if (stmt.elseBranch != null) execute(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitFor(Stmt.For stmt) {
        scopeStack.beginScope();
        if (stmt.initializer != null) execute(stmt.initializer);
        if (stmt.condition   != null) evaluate(stmt.condition);
        if (stmt.increment   != null) evaluate(stmt.increment);
        execute(stmt.body);
        scopeStack.endScope();
        return null;
    }

    @Override
    public Void visitPrint(Stmt.Print stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitBlock(Stmt.Block stmt) {
        scopeStack.beginScope();
        for (Stmt s : stmt.statements) execute(s);
        scopeStack.endScope();
        return null;
    }

    @Override
    public Void visitExpression(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    // ── Expression visitors (DFS) ─────────────────────────────────────────────

    @Override
    public Void visitVariable(Expr.Variable expr) {
        // Self-reference check: if the name is DECLARING in the current scope,
        // the variable's own initializer is referencing it before it's defined.
        if (scopeStack.has(expr.name.origin())
                && scopeStack.state(expr.name.origin()) == Scope.State.DECLARING) {
            result.addError(expr.name.line(),
                    "Can't read local variable in initializer.");
        }
        return null;
    }

    @Override
    public Void visitBinary(Expr.Binary expr) {
        evaluate(expr.left);
        evaluate(expr.right);
        return null;
    }

    @Override
    public Void visitLogical(Expr.Logical expr) {
        evaluate(expr.left);
        evaluate(expr.right);
        return null;
    }

    @Override
    public Void visitComparison(Expr.Comparison expr) {
        evaluate(expr.left);
        evaluate(expr.right);
        return null;
    }

    @Override
    public Void visitUnary(Expr.Unary expr) {
        evaluate(expr.operand);
        return null;
    }

    @Override
    public Void visitGrouping(Expr.Grouping expr) {
        evaluate(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteral(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitAssign(Expr.Assign expr) {
        evaluate(expr.value);
        return null;
    }

    // ── Internal dispatch ─────────────────────────────────────────────────────

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    private void evaluate(Expr expr) {
        expr.accept(this);
    }
}
