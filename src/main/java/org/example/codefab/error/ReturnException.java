package org.example.codefab.error;

/**
 * {@code return} 문을 만났을 때 호출 지점까지 스택을 되감기 위한 제어 흐름 신호.
 * <p>
 * 실제 오류가 아니므로 {@link CodeFabError}를 상속하지 않는다.
 * 메시지·스택트레이스를 생성하지 않아 성능 오버헤드가 없다.
 */
public class ReturnException extends RuntimeException {

    public final Object value;

    public ReturnException(Object value) {
        super(null, null, false, false); // 메시지/스택트레이스 억제 (성능)
        this.value = value;
    }
}
