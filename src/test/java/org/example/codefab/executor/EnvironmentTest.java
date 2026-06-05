package org.example.codefab.executor;

import org.example.codefab.error.RuntimeError;
import org.example.codefab.token.Token;
import org.example.codefab.token.TokenType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Red-phase unit tests for Environment (손 AST, 독립 실행 가능).
 * Environment.define / get / assign 세 메서드 전체를 커버한다.
 */
class EnvironmentTest {

    private Token tok(String name) {
        return new Token(TokenType.IDENTIFIER, name, null, 1);
    }

    // ── define / get ──────────────────────────────────────────────────────────

    @Test void defineAndGet() {
        var env = new Environment();
        env.define("x", 42.0);
        assertEquals(42.0, env.get(tok("x")));
    }

    @Test void defineNullValue() {
        // var x; (초기값 없음) → null 저장 후 조회
        var env = new Environment();
        env.define("x", null);
        assertNull(env.get(tok("x")));
    }

    // ── assign ────────────────────────────────────────────────────────────────

    @Test void assignUpdatesValue() {
        var env = new Environment();
        env.define("x", 1.0);
        env.assign(tok("x"), 99.0);
        assertEquals(99.0, env.get(tok("x")));
    }

    // ── enclosing scope chain ─────────────────────────────────────────────────

    @Test void getFromEnclosingScope() {
        // inner 스코프에서 outer 변수 읽기
        var outer = new Environment();
        outer.define("x", "outerVal");
        var inner = new Environment(outer);
        assertEquals("outerVal", inner.get(tok("x")));
    }

    @Test void assignInEnclosingScope() {
        // inner에서 assign → outer의 값이 바뀌어야 한다
        var outer = new Environment();
        outer.define("x", 1.0);
        var inner = new Environment(outer);
        inner.assign(tok("x"), 2.0);
        assertEquals(2.0, outer.get(tok("x")));
    }

    @Test void shadowingInChildScope() {
        // child에서 같은 이름을 define → 각 스코프 독립 유지
        var outer = new Environment();
        outer.define("x", "outer");
        var inner = new Environment(outer);
        inner.define("x", "inner");
        assertEquals("inner", inner.get(tok("x")));
        assertEquals("outer", outer.get(tok("x")));
    }

    // ── error cases ───────────────────────────────────────────────────────────

    @Test void getUndefinedThrowsRuntimeError() {
        var env = new Environment();
        assertThrows(RuntimeError.class, () -> env.get(tok("notDefined")));
    }

    @Test void assignUndefinedThrowsRuntimeError() {
        var env = new Environment();
        assertThrows(RuntimeError.class, () -> env.assign(tok("notDefined"), 1.0));
    }
}
