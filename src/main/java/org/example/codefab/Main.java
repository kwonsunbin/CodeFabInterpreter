package org.example.codefab;

import org.example.codefab.assembler.Assembler;
import org.example.codefab.checker.Checker;
import org.example.codefab.executor.Environment;
import org.example.codefab.executor.Executor;
import org.example.codefab.log.Logger;
import org.example.codefab.shell.FileRunner;
import org.example.codefab.shell.Pipeline;
import org.example.codefab.shell.Shell;
import org.example.codefab.shell.debug.Debugger;

import java.util.Arrays;
import java.util.List;

/**
 * Entry point for the CodeFab interpreter (공장 제어 쉘).
 *
 * Usage:
 *   ./factory                      # REPL — interactive prompt mode
 *   ./factory run <file.txt>       # file mode — run a script
 *   ./factory debug <file.txt>     # debug mode — step through a script
 *   --verbose                      # enable lifecycle logs (any mode)
 */
public class Main {

    public static void main(String[] args) {
        List<String> argList = Arrays.asList(args);
        boolean verbose = argList.contains("--verbose");

        Logger    log       = new Logger(verbose);
        Assembler assembler = new Assembler();
        // Checker가 생성한 전역 Environment를 Executor가 주입받아 그대로 활용한다 (단일 스코프 구조 공유).
        Environment global  = new Environment();
        Checker   checker   = new Checker(global);
        Executor  executor  = new Executor(log, global);
        Pipeline  pipeline  = new Pipeline(assembler, checker, executor, log, System.out);

        List<String> effectiveArgs = argList.stream()
                .filter(a -> !a.equals("--verbose"))
                .toList();

        if (effectiveArgs.size() >= 2 && effectiveArgs.get(0).equals("run")) {
            new FileRunner(pipeline, System.out).run(effectiveArgs.get(1));

        } else if (effectiveArgs.size() >= 2 && effectiveArgs.get(0).equals("debug")) {
            new Debugger(pipeline, System.in, System.out).runFile(effectiveArgs.get(1));

        } else {
            System.out.println("CodeFab Interpreter — type 'exit' to quit.");
            new Shell(pipeline, System.in, System.out).run();
        }
    }
}
