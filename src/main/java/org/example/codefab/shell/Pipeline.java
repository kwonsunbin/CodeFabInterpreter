package org.example.codefab.shell;

import org.example.codefab.assembler.Assembler;
import org.example.codefab.ast.Stmt;
import org.example.codefab.checker.CheckResult;
import org.example.codefab.checker.Checker;
import org.example.codefab.checker.Diagnostic;
import org.example.codefab.error.CodeFabError;
import org.example.codefab.executor.Executor;
import org.example.codefab.log.Logger;

import java.io.PrintStream;
import java.util.List;

/**
 * 3-stage pipeline: Assembler → Checker → Executor.
 * Extracted from Shell to separate execution logic from REPL I/O.
 */
public class Pipeline {

    private final Assembler  assembler;
    private final Checker    checker;
    private final Executor   executor;
    private final Logger     log;
    private final PrintStream out;

    public Pipeline(Assembler assembler, Checker checker, Executor executor,
                    Logger log, PrintStream out) {
        this.assembler = assembler;
        this.checker   = checker;
        this.executor  = executor;
        this.log       = log;
        this.out       = out;
    }

    public void run(String source) {
        try {
            List<Stmt> program = assembler.assemble(source);
            CheckResult cr = checker.check(program);

            printDiagnostics(cr.warnings);

            if (!cr.ok()) {
                printDiagnostics(cr.errors);
                return;
            }

            executor.run(program);
        } catch (CodeFabError e) {
            log.error(e);
            out.println(e.getMessage());
        }
    }

    private void printDiagnostics(List<Diagnostic> diagnostics) {
        for (Diagnostic d : diagnostics) out.println(d);
    }

    // ── Accessors (file / debug modes) ────────────────────────────────────────

    public Assembler  assembler() { return assembler; }
    public Checker    checker()   { return checker; }
    public Executor   executor()  { return executor; }
    public Logger     log()       { return log; }
    public PrintStream out()      { return out; }
}
