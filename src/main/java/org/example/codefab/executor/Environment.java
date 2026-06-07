package org.example.codefab.executor;

import org.example.codefab.error.RuntimeError;
import org.example.codefab.token.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime variable environment backed by a list of slot-indexed {@link Frame}s.
 *
 * <p>Layout: {@code scopes.get(0)} = outermost (global / function root),
 * {@code scopes.get(scopes.size()-1)} = current (innermost) scope.
 *
 * <p><b>Static binding (Checker가 산출한 위치 주입):</b> Checker가 각 변수에 대해
 * 미리 계산한 {@code depth}(스코프 거리)와 {@code slot}(스코프 내 인덱스)으로
 * {@link #getAt}/{@link #setAt}이 이름 해싱 없이 배열 인덱스로 즉시 접근한다 (O(1)).
 * 각 {@code Frame}은 변수를 <em>선언 순서대로</em> 저장하므로, Checker가 선언 순서로
 * 부여한 slot과 런타임 저장 위치가 정확히 일치한다.
 *
 * <p><b>이름 기반 fallback:</b> Checker를 거치지 않은 AST(테스트의 손 AST 등)는
 * depth/slot이 미해석(-1)이라 {@link #get}/{@link #assign}이 스코프 체인을 이름으로
 * 탐색한다.
 *
 * <p><b>클로저:</b> {@code new Environment(closure)}는 closure의 프레임 리스트를
 * 얕게 복사(프레임 객체는 공유)하고 새 함수 본문 프레임을 덧붙인다. 공유 프레임을 통해
 * 가변 클로저 의미가 추가 장부 없이 보존된다.
 */
public class Environment {

    /** 한 스코프: 변수를 선언 순서(slot)대로 저장한다. */
    private static final class Frame {
        private final List<String> names  = new ArrayList<>();
        private final List<Object> values = new ArrayList<>();

        /** 현재 스코프에 선언. 이미 있으면 덮어쓰고, 없으면 다음 슬롯에 추가. */
        void define(String name, Object value) {
            int i = names.indexOf(name);
            if (i >= 0) values.set(i, value);
            else { names.add(name); values.add(value); }
        }

        int     slotOf(String name)   { return names.indexOf(name); }
        boolean has(String name)      { return names.contains(name); }
        Object  getSlot(int slot)     { return values.get(slot); }
        void    setSlot(int slot, Object v) { values.set(slot, v); }

        /** 디버그/조회용 이름→값 뷰 (선언 순서 유지). */
        Map<String, Object> asMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < names.size(); i++) m.put(names.get(i), values.get(i));
            return m;
        }
    }

    private final List<Frame> scopes;

    /** Creates a new global environment (single global scope). */
    public Environment() {
        scopes = new ArrayList<>();
        scopes.add(new Frame());
    }

    /**
     * Creates a function-call environment: shallow-copies the closure's frame list
     * (frames shared by reference) and appends a fresh frame for the function body.
     */
    public Environment(Environment closure) {
        scopes = new ArrayList<>(closure.scopes);
        scopes.add(new Frame());
    }

    /** Push a new scope (called at block/for-loop entry). */
    public void pushScope() { scopes.add(new Frame()); }

    /** Pop the current scope (called at block/for-loop exit). */
    public void popScope()  { scopes.remove(scopes.size() - 1); }

    /** Declare a variable in the current (innermost) scope. */
    public void define(String name, Object value) {
        scopes.get(scopes.size() - 1).define(name, value);
    }

    // ── Static binding (O(1) depth+slot access — Checker가 주입한 위치) ──────────

    /** Reads the variable at the pre-computed (depth, slot) — pure index access, O(1). */
    public Object getAt(int depth, int slot) {
        return scopes.get(scopes.size() - 1 - depth).getSlot(slot);
    }

    /** Writes the variable at the pre-computed (depth, slot) — pure index access, O(1). */
    public void setAt(int depth, int slot, Object value) {
        scopes.get(scopes.size() - 1 - depth).setSlot(slot, value);
    }

    // ── Fallback chain-walk (depth == -1: Checker 미해석 AST) ───────────────────

    /** Reads a variable by walking scopes from innermost to outermost. */
    public Object get(Token name) {
        String key = name.origin();
        for (int i = scopes.size() - 1; i >= 0; i--) {
            int slot = scopes.get(i).slotOf(key);
            if (slot >= 0) return scopes.get(i).getSlot(slot);
        }
        throw new RuntimeError(name, "Undefined variable '" + key + "'.");
    }

    /** Assigns a variable by walking scopes from innermost to outermost. */
    public void assign(Token name, Object value) {
        String key = name.origin();
        for (int i = scopes.size() - 1; i >= 0; i--) {
            int slot = scopes.get(i).slotOf(key);
            if (slot >= 0) { scopes.get(i).setSlot(slot, value); return; }
        }
        throw new RuntimeError(name, "Undefined variable '" + key + "'.");
    }

    // ── Debug accessors ───────────────────────────────────────────────────────

    /** Returns true if the name exists anywhere in the scope chain. */
    public boolean has(String name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).has(name)) return true;
        }
        return false;
    }

    /** Walks the scope chain and returns the value, or null if not found. */
    public Object getByName(String name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            int slot = scopes.get(i).slotOf(name);
            if (slot >= 0) return scopes.get(i).getSlot(slot);
        }
        return null;
    }

    /** Returns a read-only name→value view of the current (innermost) scope. */
    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(scopes.get(scopes.size() - 1).asMap());
    }

    /**
     * Returns all scopes as name→value maps, ordered outermost→innermost
     * (index 0 = global / function root, last index = current scope).
     * Used by the Debugger to iterate the full scope chain.
     */
    public List<Map<String, Object>> allScopes() {
        List<Map<String, Object>> view = new ArrayList<>(scopes.size());
        for (Frame f : scopes) view.add(f.asMap());
        return Collections.unmodifiableList(view);
    }
}
