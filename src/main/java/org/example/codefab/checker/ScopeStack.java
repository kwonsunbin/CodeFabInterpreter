package org.example.codefab.checker;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Manages the lexical scope stack for static analysis.
 * Extracted from Checker to separate scope-management responsibility (SRP).
 */
class ScopeStack {

    private final Scope globalScope = new Scope();
    private final Deque<Scope> scopes = new ArrayDeque<>();

    void initialize() {
        if (scopes.isEmpty()) scopes.push(globalScope);
    }

    void beginScope() { scopes.push(new Scope()); }
    void endScope()   { scopes.pop(); }

    void declare(String name) { scopes.peek().declare(name); }
    void define(String name)  { scopes.peek().define(name); }

    boolean     has(String name)   { return scopes.peek().has(name); }
    Scope.State state(String name) { return scopes.peek().state(name); }
}
