package org.example.codefab.executor;

import org.example.codefab.error.RuntimeError;
import org.example.codefab.token.Token;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime variable environment with lexical scoping.
 * Each block creates a child Environment with a pointer to its enclosing scope.
 *
 * <p>Responsibility boundary:
 * <ul>
 *   <li>Semantic errors (undeclared variable, block scope violation, forward reference)
 *       are detected statically by {@code Checker} before execution begins.</li>
 *   <li>This class throws {@code RuntimeError} for "Undefined variable" only as a
 *       defensive assertion — it should be unreachable when the full pipeline runs.</li>
 *   <li>True runtime errors (type mismatch, division by zero) are raised by
 *       {@code Executor} and remain the sole responsibility of the execution layer.</li>
 * </ul>
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
        String key = name.origin();
        Object val = values.get(key);
        if (val != null || values.containsKey(key))
            return val;
        if (enclosing != null)
            return enclosing.get(name);
        throw new RuntimeError(name, "Undefined variable '" + key + "'.");
    }

    /** Assign to an existing variable in the nearest scope that defines it. */
    public void assign(Token name, Object value) {
        if (values.containsKey(name.origin())) { values.put(name.origin(), value); return; }
        if (enclosing != null) { enclosing.assign(name, value); return; }
        throw new RuntimeError(name, "Undefined variable '" + name.origin() + "'.");
    }

    /** O(1) read: jump directly to the environment 'distance' hops up. */
    public Object getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }

    /** O(1) write: jump directly to the environment 'distance' hops up. */
    public void setAt(int distance, String name, Object value) {
        ancestor(distance).values.put(name, value);
    }

    private Environment ancestor(int distance) {
        Environment env = this;
        for (int i = 0; i < distance; i++) env = env.enclosing;
        return env;
    }
}
