package org.example.codefab.executor;

/**
 * Thrown by Executor.visitReturn to unwind the call stack back to visitCall.
 * Not a real error — purely a control-flow signal.
 */
class ReturnException extends RuntimeException {
    final Object value;

    ReturnException(Object value) {
        super(null, null, true, false); // suppress message/stack for performance
        this.value = value;
    }
}
