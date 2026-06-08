package org.example.codefab.executor;

import java.util.List;

/** 호출 가능한 런타임 값(함수)의 공통 인터페이스. */
public interface CodeFabCallable {

    /** 선언된 매개변수 개수. */
    int arity();

    /** 인자를 받아 함수를 실행하고 반환값을 돌려준다 (반환문이 없으면 null). */
    Object call(Executor executor, List<Object> arguments);
}
