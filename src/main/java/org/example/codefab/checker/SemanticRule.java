package org.example.codefab.checker;

import org.example.codefab.ast.Expr;
import org.example.codefab.token.Token;

/**
 * Extension point for static semantic rules (OCP).
 * Add new rules by implementing this interface and registering in Checker.
 * Checker never needs to be modified to support new rules.
 */
interface SemanticRule {

    /**
     * Called before a variable is declared in the current scope.
     * Returns true to proceed with declaration, false to abort.
     */
    default boolean onDeclare(Token name, ScopeStack scopeStack, CheckResult result) {
        return true;
    }

    /**
     * Called when a variable is read (Expr.Variable visited).
     */
    default void onVariableRead(Expr.Variable expr, ScopeStack scopeStack, CheckResult result) {}
}
