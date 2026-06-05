package org.example.codefab.checker;

import org.example.codefab.token.Token;

/**
 * Rule: duplicate variable declaration in the same scope is an error.
 */
class DuplicateDeclarationRule implements SemanticRule {

    @Override
    public boolean onDeclare(Token name, ScopeStack scopeStack, CheckResult result) {
        if (scopeStack.has(name.origin())) {
            result.addError(name.line(), "Already a variable with this name in this scope.");
            return false;
        }
        return true;
    }
}
