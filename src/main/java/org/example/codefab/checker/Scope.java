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

    private final Map<String, State> names = new HashMap<>();

    boolean has(String name)      { return names.containsKey(name); }
    State   state(String name)    { return names.get(name); }

    void declare(String name)     { names.put(name, State.DECLARING); }
    void define(String name)      { names.put(name, State.DEFINED); }
}
