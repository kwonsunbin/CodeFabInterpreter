package org.example.codefab.executor;

import org.example.codefab.error.RuntimeError;
import org.example.codefab.token.Token;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime variable environment with lexical scoping.
 * Each block creates a child Environment with a pointer to its enclosing scope.
 */
public class Environment {

    private final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    /** Global environment constructor. */
    public Environment() { this.enclosing = null; }

    /** Child environment constructor for blocks and for-loops. */
    public Environment(Environment enclosing) { this.enclosing = enclosing; }

    /** Declare a new variable in this scope. */
    public void define(String name, Object value) {
        values.put(name, value);
    }

    /** Read a variable, walking the enclosing chain. */
    public Object get(Token name) {
        if (values.containsKey(name.origin())) return values.get(name.origin());
        if (enclosing != null) return enclosing.get(name);
        throw new RuntimeError(name, "Undefined variable '" + name.origin() + "'.");
    }

    /** Assign to an existing variable in the nearest scope that defines it. */
    public void assign(Token name, Object value) {
        if (values.containsKey(name.origin())) { values.put(name.origin(), value); return; }
        if (enclosing != null) { enclosing.assign(name, value); return; }
        throw new RuntimeError(name, "Undefined variable '" + name.origin() + "'.");
    }
}
