package org.example.codefab.checker;

import org.example.codefab.ast.Expr;

/**
 * Rule: a variable's initializer must not reference itself (var a = a;).
 */
class SelfReferenceRule implements SemanticRule {

    @Override
    public void onVariableRead(Expr.Variable expr, ScopeStack scopeStack, CheckResult result) {
        if (scopeStack.has(expr.name.origin())
                && scopeStack.state(expr.name.origin()) == Scope.State.DECLARING) {
            result.addError(expr.name.line(), "Can't read local variable in initializer.");
        }
    }
}
