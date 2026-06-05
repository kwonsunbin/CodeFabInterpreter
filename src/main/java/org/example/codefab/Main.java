package org.example.codefab;

import org.example.codefab.assembler.Assembler;
import org.example.codefab.checker.Checker;
import org.example.codefab.executor.Executor;
import org.example.codefab.log.Logger;
import org.example.codefab.shell.FileRunner;
import org.example.codefab.shell.Pipeline;
import org.example.codefab.shell.Shell;

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

        Logger    log      = new Logger(verbose);
        Assembler assembler = new Assembler();
        Checker   checker  = new Checker();
        Executor  executor = new Executor(log);
        Pipeline  pipeline = new Pipeline(assembler, checker, executor, log, System.out);

        List<String> effectiveArgs = argList.stream()
                .filter(a -> !a.equals("--verbose"))
                .toList();

        if (effectiveArgs.size() >= 2 && effectiveArgs.get(0).equals("run")) {
            new FileRunner(pipeline, System.out).run(effectiveArgs.get(1));

        } else if (effectiveArgs.size() >= 2 && effectiveArgs.get(0).equals("debug")) {
            // debug 모드는 Executor 훅 연동 후 구현 예정
            System.out.println("[debug] 디버그 모드는 준비 중입니다: " + effectiveArgs.get(1));

        } else {
            System.out.println("CodeFab Interpreter — type 'exit' to quit.");
            new Shell(pipeline, System.in, System.out).run();
        }
    }
}
