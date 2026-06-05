package org.example.codefab.executor;

import java.util.List;

/** Marker interface for all first-class callable values (functions). */
public interface CodeFabCallable {
    int arity();
    Object call(Executor executor, List<Object> arguments);
}
