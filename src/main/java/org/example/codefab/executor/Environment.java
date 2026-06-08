package org.example.codefab.executor;

import org.example.codefab.error.RuntimeError;
import org.example.codefab.token.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checker와 Executor가 <b>함께 사용하는 단일 스코프 환경</b>.
 *
 * <p>예전에는 Checker가 {@code Scope}(이름→선언상태), Executor가 {@code Environment}
 * (이름→값)라는 유사한 두 클래스를 따로 두었다. 이 둘을 하나로 합쳐, Pipeline이 생성한
 * 같은 Environment 인스턴스를 Checker가 분석에 쓰고 Executor가 주입받아 실행에 쓴다.
 *
 * <p>각 스코프는 두 가지를 보관한다:
 * <ul>
 *   <li>{@code values} — 이름→값 (런타임에 Executor가 채움)</li>
 *   <li>{@code defined} — "초기화까지 끝나 사용 가능한 이름" 집합 (Checker의 2단계 선언/정의용)</li>
 * </ul>
 *
 * <p>스코프 체인은 리스트로 표현한다: index 0 = 최외곽(전역), 마지막 = 현재(최내곽).
 */
public class Environment {

    /** 한 스코프: 값 맵 + (Checker용) 정의 완료 이름 집합. */
    private static final class Scope {
        final Map<String, Object> values  = new HashMap<>();
        final Set<String>         defined = new HashSet<>();
    }

    private final List<Scope> scopes;

    /** 전역 환경 생성 (전역 스코프 하나). */
    public Environment() {
        scopes = new ArrayList<>();
        scopes.add(new Scope());
    }

    /** 클로저 환경: 바깥 환경의 스코프 리스트를 얕게 복사(스코프 객체 공유)하고 새 스코프를 덧붙인다. */
    public Environment(Environment enclosing) {
        scopes = new ArrayList<>(enclosing.scopes);
        scopes.add(new Scope());
    }

    private Scope current() { return scopes.get(scopes.size() - 1); }

    // ── 스코프 진입/이탈 (블록·for 진입 시) ─────────────────────────────────────
    public void pushScope() { scopes.add(new Scope()); }
    public void popScope()  { scopes.remove(scopes.size() - 1); }

    // ── 런타임: 값 저장/조회 (Executor) ────────────────────────────────────────

    /** 현재 스코프에 변수를 정의한다 (값 저장 + 정의 완료 표시). */
    public void define(String name, Object value) {
        current().values.put(name, value);
        current().defined.add(name);
    }

    /** 변수 읽기 — 현재→외곽 순으로 체인을 탐색. 없으면 RuntimeError. */
    public Object get(Token name) {
        String key = name.origin();
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).values.containsKey(key)) return scopes.get(i).values.get(key);
        }
        throw new RuntimeError(name, "Undefined variable '" + key + "'.");
    }

    /** 변수 할당 — 이름을 가진 가장 가까운 스코프에 덮어쓴다. 없으면 RuntimeError. */
    public void assign(Token name, Object value) {
        String key = name.origin();
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).values.containsKey(key)) { scopes.get(i).values.put(key, value); return; }
        }
        throw new RuntimeError(name, "Undefined variable '" + key + "'.");
    }

    // ── 정적 바인딩 (Checker가 계산한 depth로 스코프를 한 번에 지정) ─────────────
    // depth 0 = 현재(최내곽), N = N칸 바깥. 스코프 탐색을 건너뛰어 O(1)로 스코프에 도달한다.

    /** Checker가 미리 계산한 depth의 스코프에서 변수를 읽는다. */
    public Object getAt(int depth, String name) {
        return scopes.get(scopes.size() - 1 - depth).values.get(name);
    }

    /** Checker가 미리 계산한 depth의 스코프에 변수를 기록한다. */
    public void setAt(int depth, String name, Object value) {
        scopes.get(scopes.size() - 1 - depth).values.put(name, value);
    }

    // ── 정적 분석: 2단계 선언/정의 (Checker) ──────────────────────────────────

    /** 1단계: 현재 스코프에 이름을 선언(아직 사용 불가 — 초기화식 분석 중). */
    public void declare(String name) {
        current().values.put(name, null); // 자리만 확보 (값은 런타임에 채움)
    }

    /** 2단계: 초기화식 분석이 끝나 사용 가능하다고 표시. */
    public void markDefined(String name) {
        current().defined.add(name);
    }

    /** 현재 스코프에 이미 이 이름이 선언되어 있는가 (같은 스코프 중복 선언 검사). */
    public boolean existsInCurrentScope(String name) {
        return current().values.containsKey(name);
    }

    /** 현재 스코프에 선언됐지만 아직 정의(사용 가능) 전인가 (자기 참조 검사: var a = a;). */
    public boolean isDeclaringInCurrentScope(String name) {
        return current().values.containsKey(name) && !current().defined.contains(name);
    }

    /**
     * 현재 스코프에서 name이 사용 가능(defined)하게 되기까지의 거리(depth).
     * 0 = 현재, N = N칸 바깥. 어디에도 없으면 -1. (미정의 검사 + Executor용 depth 산출 겸용)
     */
    public int distanceOf(String name) {
        int depth = 0;
        for (int i = scopes.size() - 1; i >= 0; i--, depth++) {
            if (scopes.get(i).defined.contains(name)) return depth;
        }
        return -1;
    }

    // ── 클로저 캡처 ───────────────────────────────────────────────────────────

    /**
     * 현재 스코프 체인의 스냅샷을 담은 독립 Environment를 반환. 함수 클로저 캡처 전용.
     * <p>
     * 반환된 환경의 scopes 리스트는 this와 독립적이므로, 이후 this에 대한
     * pushScope/popScope가 반환값에 영향을 주지 않는다.
     * (단, 공유된 Scope 객체 내 values 변경은 양쪽 모두에 반영된다.)
     */
    public Environment captureForClosure() {
        Environment snapshot = new Environment();
        snapshot.scopes.clear();
        snapshot.scopes.addAll(this.scopes); // 리스트 복사; Scope 객체는 공유
        return snapshot;
    }

    // ── 디버그 접근자 (Debugger) ───────────────────────────────────────────────

    public boolean has(String name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).values.containsKey(name)) return true;
        }
        return false;
    }

    public Object getByName(String name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).values.containsKey(name)) return scopes.get(i).values.get(name);
        }
        return null;
    }

    /** 현재(최내곽) 스코프의 읽기 전용 이름→값 뷰. */
    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(current().values);
    }

    /** 전 스코프를 외곽→내곽 순(index 0 = 전역)으로 반환. Debugger가 전체 체인을 순회할 때 사용. */
    public List<Map<String, Object>> allScopes() {
        List<Map<String, Object>> view = new ArrayList<>(scopes.size());
        for (Scope s : scopes) view.add(Collections.unmodifiableMap(s.values));
        return Collections.unmodifiableList(view);
    }
}
