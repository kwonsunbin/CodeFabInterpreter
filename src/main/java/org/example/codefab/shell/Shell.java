package org.example.codefab.shell;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

/**
 * Prompt Shell: interactive REPL.
 *
 * Prompts ">>>" for new input and "..." while a submission is incomplete
 * (unbalanced parentheses or braces). Delegates execution to Pipeline.
 */
public class Shell {

    private static final String PRIMARY      = ">>> ";
    private static final String CONTINUATION = "... ";

    private final Pipeline       pipeline;
    private final BufferedReader reader;
    private final PrintStream    out;

    public Shell(Pipeline pipeline, InputStream in, PrintStream out) {
        this.pipeline = pipeline;
        this.reader   = new BufferedReader(new InputStreamReader(in));
        this.out      = out;
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
                if (buf.isEmpty() && isExitCommand(trimmed)) break;
                if (buf.isEmpty() && trimmed.isEmpty()) continue;

                buf.append(line);

                if (buf.isComplete()) {
                    pipeline.run(buf.text());
                    buf.clear();
                }
            }
        } catch (java.io.IOException e) {
            out.println("I/O error: " + e.getMessage());
        }
    }

    private boolean isExitCommand(String trimmed) {
        return trimmed.equals("exit") || trimmed.equals("quit");
    }
}
