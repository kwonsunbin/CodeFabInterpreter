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
        SubmissionBuffer buf = new SubmissionBuffer();
        try {
            while (true) {
                out.print(buf.isEmpty() ? PRIMARY : CONTINUATION);
                out.flush();

                String line = reader.readLine();
                if (line == null) break;

                String trimmed = line.strip();
                if (buf.isEmpty() && (trimmed.equals("exit") || trimmed.equals("quit"))) break;
                if (buf.isEmpty() && trimmed.isEmpty()) continue;

                buf.append(line);

                if (buf.isComplete()) {
                    runPipeline(buf.text());
                    buf.clear();
                }
            }
        } catch (java.io.IOException e) {
            out.println("I/O error: " + e.getMessage());
        }
    }

    private void runPipeline(String source) {
        try {
            List<Stmt> program = assembler.assemble(source);
            CheckResult cr = checker.check(program);

            for (Diagnostic d : cr.warnings) {
                out.println(d);
            }

            if (!cr.ok()) {
                for (Diagnostic d : cr.errors) {
                    out.println(d);
                }
                return;
            }

            executor.run(program);
        } catch (CodeFabError e) {
            log.error(e);
            out.println(e.getMessage());
        }
    }
}
