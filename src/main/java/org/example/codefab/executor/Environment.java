package org.example.codefab.executor;

import org.example.codefab.error.RuntimeError;
import org.example.codefab.token.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime variable environment backed by an ArrayList of scope Maps.
 *
 * <p>Layout: {@code scopes.get(0)} = outermost (global or function root),
 * {@code scopes.get(scopes.size()-1)} = current (innermost) scope.
 *
 * <p>Depth semantics (set by Checker):
 * <ul>
 *   <li>depth 0 = current scope (innermost)</li>
 *   <li>depth 1 = one scope up, etc.</li>
 * </ul>
 * {@code getAt} / {@code setAt} resolve to {@code scopes.get(size - 1 - depth)} — O(1).
 *
 * <p>Closure capture: {@code new Environment(closure)} shallow-copies the closure's
 * scope list and appends a fresh function-body scope.  Shared Map entries remain
 * visible across all environments that reference them, giving correct mutable-closure
 * semantics with no extra bookkeeping.
 */
public class Environment {

    private final List<Map<String, Object>> scopes;

    /** Creates a new global environment (single global scope). */
    public Environment() {
        scopes = new ArrayList<>();
        scopes.add(new HashMap<>());
    }

    /**
     * Creates a function-call environment: shallow-copies the closure's scope stack
     * and appends a new scope for the function body.
     */
    public Environment(Environment closure) {
        scopes = new ArrayList<>(closure.scopes);
        scopes.add(new HashMap<>());
    }

    /** Push a new scope (called at block/for-loop entry). */
    public void pushScope() { scopes.add(new HashMap<>()); }

    /** Pop the current scope (called at block/for-loop exit). */
    public void popScope()  { scopes.remove(scopes.size() - 1); }

    /** Declare a variable in the current (innermost) scope. */
    public void define(String name, Object value) {
        scopes.get(scopes.size() - 1).put(name, value);
    }

    // ── Static binding (O(1) depth-indexed access) ────────────────────────────

    /** Reads the variable at the pre-computed scope depth (O(1)). */
    public Object getAt(int depth, String name) {
        return scopes.get(scopes.size() - 1 - depth).get(name);
    }

    /** Writes the variable at the pre-computed scope depth (O(1)). */
    public void setAt(int depth, String name, Object value) {
        scopes.get(scopes.size() - 1 - depth).put(name, value);
    }

    // ── Fallback chain-walk (depth == -1, should be unreachable after Checker) ─

    /** Reads a variable by walking scopes from innermost to outermost. */
    public Object get(Token name) {
        String key = name.origin();
        for (int i = scopes.size() - 1; i >= 0; i--) {
            Map<String, Object> scope = scopes.get(i);
            if (scope.containsKey(key)) return scope.get(key);
        }
        throw new RuntimeError(name, "Undefined variable '" + key + "'.");
    }

    /** Assigns a variable by walking scopes from innermost to outermost. */
    public void assign(Token name, Object value) {
        String key = name.origin();
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(key)) {
                scopes.get(i).put(key, value);
                return;
            }
        }
        throw new RuntimeError(name, "Undefined variable '" + key + "'.");
    }

    // ── Debug accessors ───────────────────────────────────────────────────────

    /** Returns true if the name exists anywhere in the scope chain. */
    public boolean has(String name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name)) return true;
        }
        return false;
    }

    /** Walks the scope chain and returns the value, or null if not found. */
    public Object getByName(String name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name)) return scopes.get(i).get(name);
        }
        return null;
    }

    /** Returns a read-only view of the current (innermost) scope's variables. */
    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(scopes.get(scopes.size() - 1));
    }

    /**
     * Returns all scopes as an unmodifiable list, ordered outermost→innermost
     * (index 0 = global / function root, last index = current scope).
     * Used by Debugger to iterate the full scope chain without pointer chasing.
     */
    public List<Map<String, Object>> allScopes() {
        return Collections.unmodifiableList(scopes);
    }
}
