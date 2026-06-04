package org.example.codefab.log;

import org.example.codefab.error.CodeFabError;

import java.io.PrintStream;

/**
 * Logger: lifecycle and diagnostic output.
 *
 * Verbose mode gates [exec] start/done messages (useful for debugging).
 * Error messages are always emitted to stderr regardless of verbose mode.
 * print statement output bypasses Logger and goes directly to stdout.
 */
public class Logger {

    private static final String EXEC_START = "[exec] start";
    private static final String EXEC_DONE  = "[exec] done";

    private boolean verbose;
    private final PrintStream err;

    public Logger(boolean verbose) { this(verbose, System.err); }

    public Logger(boolean verbose, PrintStream err) {
        this.verbose = verbose;
        this.err     = err;
    }

    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    public void executionStart()    { if (verbose) err(EXEC_START); }
    public void executionComplete() { if (verbose) err(EXEC_DONE);  }

    /** Always prints error diagnostics to stderr. */
    public void error(CodeFabError e) { err(e.userMessage()); }

    public void diagnostic(String msg) { if (verbose) err(msg); }

    private void err(String msg) { err.println(msg); }
}
