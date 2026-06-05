package org.example.codefab;

import org.example.codefab.assembler.Assembler;
import org.example.codefab.checker.Checker;
import org.example.codefab.checker.CheckerDepth;
import org.example.codefab.checker.CheckerFold;
import org.example.codefab.executor.Executor;
import org.example.codefab.log.Logger;
import org.example.codefab.shell.Pipeline;
import org.example.codefab.shell.Shell;

import java.util.Arrays;

/**
 * Entry point for the CodeFab interpreter.
 * Usage:
 *   ./gradlew run --console=plain -q           # interactive REPL
 *   ./gradlew run --console=plain -q --args="--verbose"   # with lifecycle logs
 */
public class Main {

    public static void main(String[] args) {
        boolean verbose = Arrays.asList(args).contains("--verbose");

        Logger       log          = new Logger(verbose);
        Assembler    assembler    = new Assembler();
        Checker      checker      = new Checker();
        CheckerDepth checkerDepth = new CheckerDepth();
        CheckerFold  checkerFold  = new CheckerFold();
        Executor     executor     = new Executor(log);

        System.out.println("CodeFab Interpreter — type 'exit' to quit.");
        Pipeline pipeline = new Pipeline(assembler, checker, checkerDepth, checkerFold, executor, log, System.out);
        new Shell(pipeline, System.in, System.out).run();
    }
}
