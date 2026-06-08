package org.example.codefab.executor;

/**
 * 함수 본문 실행 중 {@code return}을 만났을 때 호출 지점까지 스택을 되감기 위한
 * 제어 흐름 신호. 실제 오류가 아니므로 메시지·스택트레이스를 생성하지 않는다.
 */
class ReturnException extends RuntimeException {

    final Object value;

    ReturnException(Object value) {
        super(null, null, false, false); // 메시지/스택트레이스 억제 (성능)
        this.value = value;
    }
}
