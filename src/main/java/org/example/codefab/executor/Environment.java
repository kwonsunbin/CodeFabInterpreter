package org.example.codefab.executor;

import org.example.codefab.error.RuntimeError;
import org.example.codefab.token.Token;

import java.util.Collections;
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

    // ── Debug accessors ───────────────────────────────────────────────────────

    /** 이름이 이 스코프 또는 상위 체인에 존재하는지 확인 */
    public boolean has(String name) {
        if (values.containsKey(name)) return true;
        return enclosing != null && enclosing.has(name);
    }

    /** 스코프 체인을 탐색해 변수 값을 반환. 없으면 null */
    public Object getByName(String name) {
        if (values.containsKey(name)) return values.get(name);
        if (enclosing != null) return enclosing.getByName(name);
        return null;
    }

    /** 현재 스코프(직접 선언된 변수만)의 읽기 전용 스냅샷 반환 */
    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(values);
    }

    /** 상위(enclosing) 환경 반환. 전역 스코프이면 null */
    public Environment enclosing() { return enclosing; }
}
