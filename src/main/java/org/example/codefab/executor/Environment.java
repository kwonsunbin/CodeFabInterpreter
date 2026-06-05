package org.example.codefab.executor;

import org.example.codefab.error.RuntimeError;
import org.example.codefab.token.Token;

import java.util.Collections;
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

    // ── Static binding (scope distance pre-computed by Checker) ────────────────
    // get/assign은 변수를 찾을 때까지 enclosing 체인을 거슬러 오른다(O(depth)).
    // Checker가 미리 계산한 거리(distance)가 있으면, 그 위치로 한 번에 점프해
    // O(1)로 읽고 쓴다. 바인딩 존재는 Checker가 정적으로 보장한다.

    /** distance 만큼 enclosing 체인을 거슬러 올라간 환경 반환 (0 = 현재 스코프). */
    private Environment ancestor(int distance) {
        Environment env = this;
        for (int i = 0; i < distance; i++) env = env.enclosing;
        return env;
    }

    /** 정적으로 해석된 거리에서 변수 값을 즉시 읽는다 (O(1)). */
    public Object getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }

    /** 정적으로 해석된 거리의 변수에 값을 즉시 기록한다 (O(1)). */
    public void setAt(int distance, String name, Object value) {
        ancestor(distance).values.put(name, value);
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
