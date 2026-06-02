package org.example.codefab.shell;

import org.example.codefab.assembler.Assembler;
import org.example.codefab.ast.Stmt;
import org.example.codefab.checker.CheckResult;
import org.example.codefab.checker.Checker;
import org.example.codefab.checker.Diagnostic;
import org.example.codefab.error.CodeFabError;
import org.example.codefab.executor.Executor;
import org.example.codefab.log.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.List;

/**
 * Prompt Shell: interactive REPL.
 *
 * Prompts ">>>" for new input and "..." while a submission is incomplete
 * (unbalanced parentheses or braces). Once complete, runs the 3-stage pipeline:
 *   Assembler → Checker → Executor
 *
 * Errors from any stage are printed and the loop continues — the REPL never dies.
 */
public class Shell {

    private static final String PRIMARY      = ">>> ";
    private static final String CONTINUATION = "... ";

    private final Assembler assembler;
    private final Checker   checker;
    private final Executor  executor;
    private final Logger    log;
    private final BufferedReader reader;
    private final PrintStream    out;

    public Shell(Assembler assembler, Checker checker, Executor executor,
                 Logger log, InputStream in, PrintStream out) {
        this.assembler = assembler;
        this.checker   = checker;
        this.executor  = executor;
        this.log       = log;
        this.reader    = new BufferedReader(new InputStreamReader(in));
        this.out       = out;
    }

    public void run() {
        // TODO: REPL loop — print prompt, read lines into SubmissionBuffer,
        //       call runPipeline when buffer is complete, handle "exit"/"quit"
        throw new UnsupportedOperationException("TODO: implement run");
    }

    private void runPipeline(String source) {
        // TODO: assemble → check (print warnings, bail on errors) → execute
        //       catch CodeFabError and log it
        throw new UnsupportedOperationException("TODO: implement runPipeline");
    }
}
