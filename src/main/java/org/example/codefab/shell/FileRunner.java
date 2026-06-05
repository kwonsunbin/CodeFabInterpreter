package org.example.codefab.shell;

import org.example.codefab.ast.Stmt;
import org.example.codefab.checker.CheckResult;
import org.example.codefab.checker.Diagnostic;
import org.example.codefab.error.CodeFabError;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

/**
 * File mode: reads a .txt script and runs it through the pipeline.
 * Prints errors with line numbers and exits (non-zero) on any failure.
 */
public class FileRunner {

    private final Pipeline    pipeline;
    private final PrintStream out;

    public FileRunner(Pipeline pipeline, PrintStream out) {
        this.pipeline = pipeline;
        this.out      = out;
    }

    public void run(String filePath) {
        String source;
        try {
            source = Files.readString(Path.of(filePath));
        } catch (NoSuchFileException e) {
            out.println("[error] 파일을 찾을 수 없습니다: " + filePath);
            System.exit(1);
            return;
        } catch (IOException e) {
            out.println("[error] 파일 읽기 오류: " + e.getMessage());
            System.exit(1);
            return;
        }

        try {
            List<Stmt> program = pipeline.assembler().assemble(source);
            CheckResult cr = pipeline.checker().check(program);

            for (Diagnostic d : cr.warnings) out.println(d);
            if (!cr.ok()) {
                for (Diagnostic d : cr.errors) out.println(d);
                System.exit(1);
                return;
            }

            pipeline.executor().run(program);
        } catch (CodeFabError e) {
            pipeline.log().error(e);
            out.println(e.userMessage());
            System.exit(1);
        }
    }
}
