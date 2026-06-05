package org.example.codefab.shell;

import org.example.codefab.assembler.Assembler;
import org.example.codefab.checker.Checker;
import org.example.codefab.executor.Executor;
import org.example.codefab.log.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class PipelineTest {

    private ByteArrayOutputStream outBytes;
    private PrintStream savedOut;
    private Pipeline pipeline;

    @BeforeEach
    void setUp() {
        outBytes = new ByteArrayOutputStream();
        savedOut = System.out;
        System.setOut(new PrintStream(outBytes));

        Logger log = new Logger(false);
        pipeline = new Pipeline(new Assembler(), new Checker(),
                new Executor(log), log, System.out);
    }

    @AfterEach
    void tearDown() {
        System.out.flush();
        System.setOut(savedOut);
    }

    private String output() { return outBytes.toString().strip(); }

    @Test
    void printStatement() {
        pipeline.run("print 42;");
        assertEquals("42", output());
    }

    @Test
    void arithmeticExpression() {
        pipeline.run("print 1 + 2;");
        assertEquals("3", output());
    }

    @Test
    void varDeclarationAndPrint() {
        pipeline.run("var x = 10; print x;");
        assertEquals("10", output());
    }

    @Test
    void checkerErrorStopsExecution() {
        pipeline.run("var x = 1; var x = 2;");
        assertTrue(output().contains("check:error"), "checker error output should appear");
    }

    @Test
    void runtimeErrorPrintsMessage() {
        pipeline.run("print 1 + \"a\";");
        assertFalse(output().isEmpty(), "runtime error message should be printed");
    }
}
