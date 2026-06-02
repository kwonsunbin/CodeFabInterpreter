package org.example.codefab.log;

import org.example.codefab.error.CodeFabError;

/**
 * Logger: lifecycle and diagnostic output.
 *
 * Verbose mode gates [exec] start/done messages (useful for debugging).
 * Error messages are always emitted to stderr regardless of verbose mode.
 * print statement output bypasses Logger and goes directly to stdout.
 */
public class Logger {

    private boolean verbose;

    public Logger(boolean verbose) { this.verbose = verbose; }

    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    public void executionStart()    { /* TODO */ }
    public void executionComplete() { /* TODO */ }

    /** Always prints error diagnostics to stderr. */
    public void error(CodeFabError e) {
        // TODO
    }

    public void diagnostic(String msg) { /* TODO */ }

    private void err(String msg) { System.err.println(msg); }
}
