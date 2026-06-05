package org.example.codefab.executor;

import org.example.codefab.ast.Stmt;

/**
 * Observer 인터페이스: Executor가 구문을 실행하기 직전 / 스코프 진입·이탈 시 호출.
 * Debugger가 이 인터페이스를 구현하여 step / next / continue / breakpoint 로직을 수행.
 *
 * default 메서드로 제공되므로 REPL·파일 모드의 기존 동작에는 영향 없음.
 */
public interface ExecutionListener {

    /** 구문 실행 직전 — 정지 여부 판단 및 commandLoop 진입 */
    void onStatement(Stmt stmt);

    /** 새 스코프 진입 직후 (block / for) — next 깊이 추적용 */
    default void onEnterScope() {}

    /** 스코프 이탈 직전 — next 깊이 추적용 */
    default void onExitScope() {}
}
