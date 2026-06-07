package org.example.codefab.checker;

import java.util.HashMap;
import java.util.Map;

/**
 * One lexical scope in the Checker's static analysis.
 * Variables pass through DECLARING (initializer being resolved)
 * then DEFINED (fully declared and usable).
 */
class Scope {

    enum State { DECLARING, DEFINED }

    private final Map<String, State>   names = new HashMap<>();
    private final Map<String, Integer> slots = new HashMap<>();
    private int nextSlot = 0;

    boolean has(String name)      { return names.containsKey(name); }
    State   state(String name)    { return names.get(name); }

    void declare(String name) {
        // 선언 순서대로 안정적인 슬롯 인덱스를 부여한다 (Executor의 define 순서와 일치).
        slots.putIfAbsent(name, nextSlot++);
        names.put(name, State.DECLARING);
    }

    void define(String name)      { names.put(name, State.DEFINED); }

    /** 이 스코프 안에서 name의 슬롯 인덱스(선언 순서). 없으면 -1. */
    int slotOf(String name)       { return slots.getOrDefault(name, -1); }
}
