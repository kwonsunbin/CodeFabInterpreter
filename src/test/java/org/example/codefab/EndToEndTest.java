package org.example.codefab;

import org.example.codefab.assembler.Assembler;
import org.example.codefab.ast.Stmt;
import org.example.codefab.checker.CheckResult;
import org.example.codefab.checker.Checker;
import org.example.codefab.error.CodeFabError;
import org.example.codefab.executor.Executor;
import org.example.codefab.log.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end semantic tests based on the user's test script.
 * Each test instantiates a fresh pipeline with captured stdout so
 * print output can be asserted without killing the process on errors.
 */
class EndToEndTest {

    // ── Test harness ──────────────────────────────────────────────────────────

    record Result(String stdout, List<String> checkErrors, String runtimeError) {
        boolean hasCheckError()   { return !checkErrors.isEmpty(); }
        boolean hasRuntimeError() { return runtimeError != null; }
    }

    private Assembler assembler;
    private Checker   checker;
    private Executor  executor;

    @BeforeEach void setUp() {
        assembler = new Assembler();
        checker   = new Checker();
        Logger log = new Logger(false);
        executor  = new Executor(log);
    }

    /** Run a single source string through the full pipeline, return captured output. */
    private Result run(String source) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream saved = System.out;
        System.setOut(new PrintStream(baos));

        List<String> checkErrors = List.of();
        String runtimeError = null;
        try {
            List<Stmt> program = assembler.assemble(source);
            CheckResult cr = checker.check(program);
            if (!cr.ok()) {
                checkErrors = cr.errors.stream().map(d -> d.message()).toList();
                return new Result("", checkErrors, null);
            }
            executor.run(program);
        } catch (CodeFabError e) {
            runtimeError = e.getMessage();
        } finally {
            System.out.flush();
            System.setOut(saved);
        }

        return new Result(baos.toString().replace("\r\n", "\n").strip(), checkErrors, runtimeError);
    }

    /** Run multiple source strings sequentially on the SAME checker/executor
     *  (simulates a REPL session with persistent state). */
    private String runSession(String... sources) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream saved = System.out;
        System.setOut(new PrintStream(baos));
        try {
            for (String source : sources) {
                List<Stmt> program = assembler.assemble(source);
                CheckResult cr = checker.check(program);
                if (!cr.ok()) continue;
                executor.run(program);
            }
        } finally {
            System.out.flush();
            System.setOut(saved);
        }
        return baos.toString().replace("\r\n", "\n").strip();
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. 정상동작 테스트 — Basic functionality
    // ════════════════════════════════════════════════════════════════════════

    // ── Arithmetic & operator precedence ─────────────────────────────────────

    @Test void additionAndMultiplication_7() {
        assertEquals("7", run("print 1 + 2 * 3;").stdout());
    }

    @Test void groupedAdditionThenMultiply_9() {
        assertEquals("9", run("print (1 + 2) * 3;").stdout());
    }

    @Test void subtractionLeftAssoc_3() {
        assertEquals("3", run("print 10 - 4 - 3;").stdout());
    }

    @Test void divisionLeftAssoc_2() {
        assertEquals("2", run("print 8 / 2 / 2;").stdout());
    }

    @Test void unaryMinus_neg1() {
        assertEquals("-1", run("print -3 + 2;").stdout());
    }

    // ── Comparison ────────────────────────────────────────────────────────────

    @Test void lessThan_true() {
        assertEquals("true", run("print 1 < 2;").stdout());
    }

    @Test void greaterThan_false() {
        assertEquals("false", run("print 3 > 5;").stdout());
    }

    @Test void equalEqual_sameNumbers_true() {
        assertEquals("true", run("print 5 == 5;").stdout());
    }

    @Test void equalEqual_differentNumbers_false() {
        assertEquals("false", run("print 1 == 2;").stdout());
    }

    @Test void bangEqual_differentNumbers_true() {
        assertEquals("true", run("print 1 != 2;").stdout());
    }

    @Test void bangEqual_sameNumbers_false() {
        assertEquals("false", run("print 3 != 3;").stdout());
    }

    @Test void equalEqual_inIfCondition_prints() {
        assertEquals("bbq", run("if (5 == 5) print \"bbq\";").stdout());
    }

    @Test void bangEqual_inIfCondition_skips() {
        assertEquals("", run("if (5 != 5) print \"bbq\";").stdout());
    }

    @Test void equalEqual_strings_true() {
        assertEquals("true", run("print \"hello\" == \"hello\";").stdout());
    }

    @Test void equalEqual_crossType_false() {
        assertEquals("false", run("print 1 == \"1\";").stdout());
    }

    // ── String concatenation ──────────────────────────────────────────────────

    @Test void stringConcat() {
        assertEquals("Hello, CodeFab!", run("print \"Hello, \" + \"CodeFab!\";").stdout());
    }

    // ── Number formatting ─────────────────────────────────────────────────────

    @Test void integerPrintsWithoutDotZero() {
        assertEquals("5", run("print 5;").stdout());
    }

    @Test void integralDoublePrintsWithoutDotZero() {
        assertEquals("5", run("print 5.0;").stdout());
    }

    @Test void floatPreservesDecimal() {
        assertEquals("3.14", run("print 3.14;").stdout());
    }

    // ── Boolean literals ──────────────────────────────────────────────────────

    @Test void booleanTruePrints() {
        assertEquals("true", run("print true;").stdout());
    }

    @Test void booleanFalsePrints() {
        assertEquals("false", run("print false;").stdout());
    }

    // ── Variables & assignment ────────────────────────────────────────────────

    @Test void varDeclAndArithmetic() {
        assertEquals("30", run("var a = 10; var b = 20; print a + b;").stdout());
    }

    @Test void reassignment() {
        assertEquals("15", run("var a = 10; a = a + 5; print a;").stdout());
    }

    // ── Block scoping & shadowing ─────────────────────────────────────────────

    @Test void blockShadowing() {
        String out = run("var x = \"global\"; { var x = \"inner\"; print x; } print x;").stdout();
        assertEquals("inner\nglobal", out);
    }

    @Test void mutatingEnclosingVar() {
        assertEquals("1", run("var count = 0; { count = count + 1; } print count;").stdout());
    }

    @Test void nestedScopeReadsOuter() {
        String out = run("var outer = \"A\"; { var inner = \"B\"; { print outer + inner; } }").stdout();
        assertEquals("AB", out);
    }

    // ── if / else ─────────────────────────────────────────────────────────────

    @Test void ifTruePrints() {
        assertEquals("bbq", run("if (true) print \"bbq\";").stdout());
    }

    @Test void ifFalseElsePrints() {
        assertEquals("kfc", run("if (false) print \"no\"; else print \"kfc\";").stdout());
    }

    @Test void danglingElseBindsToInnerIf() {
        // if (true) if (false) print "kfc"; else print "bbq"; → prints "bbq"
        assertEquals("bbq", run("if (true) if (false) print \"kfc\"; else print \"bbq\";").stdout());
    }

    // ── for loop ─────────────────────────────────────────────────────────────

    @Test void forLoopPrints0to2() {
        String out = run("for (var j = 0; j < 3; j = j + 1) { print j; }").stdout();
        assertEquals("0\n1\n2", out);
    }

    // ── REPL session (persistent state) ──────────────────────────────────────

    @Test void replSessionPersistsVars() {
        // Simulates: >>> var a = 5; >>> var b = 10; >>> print a + b;
        String out = runSession("var a = 5;", "var b = 10;", "print a + b;");
        assertEquals("15", out);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. 에러 검출 테스트 — Error detection
    // ════════════════════════════════════════════════════════════════════════

    // ── Syntax (Parse) errors ─────────────────────────────────────────────────

    @Test void missingSemicolon_parseError() {
        var ex = assertThrows(org.example.codefab.error.ParseError.class,
                              () -> assembler.assemble("print 1 + 2"));
        assertTrue(ex.getMessage().contains("';'"));
    }

    @Test void missingClosingParen_parseError() {
        var ex = assertThrows(org.example.codefab.error.ParseError.class,
                              () -> assembler.assemble("print (1 + 2;"));
        assertTrue(ex.getMessage().contains("')'"));
    }

    @Test void invalidAssignmentTarget_parseError() {
        var ex = assertThrows(org.example.codefab.error.ParseError.class,
                              () -> assembler.assemble("var a = 1; var b = 2; a + b = 3;"));
        assertTrue(ex.getMessage().contains("Invalid assignment target"));
    }

    @Test void expectExpression_parseError() {
        var ex = assertThrows(org.example.codefab.error.ParseError.class,
                              () -> assembler.assemble("print * 5;"));
        assertTrue(ex.getMessage().contains("expression"));
    }

    // ── Checker (static semantic) errors ─────────────────────────────────────

    @Test void selfReferenceInInitializer_checkError() {
        var result = run("{ var a = a; }");
        assertTrue(result.hasCheckError());
        assertTrue(result.checkErrors().get(0).contains("Can't read local variable"));
    }

    @Test void duplicateDeclInSameScope_checkError() {
        var result = run("{ var a = \"hi\"; var a = 3; }");
        assertTrue(result.hasCheckError());
        assertTrue(result.checkErrors().get(0).contains("Already a variable"));
    }

    // ── Runtime errors ────────────────────────────────────────────────────────

    @Test void undefinedVariable_runtimeError() {
        var result = run("print notDefined;");
        assertTrue(result.hasRuntimeError());
        assertTrue(result.runtimeError().contains("Undefined variable 'notDefined'"));
    }

    @Test void numberPlusString_runtimeError() {
        var result = run("print 1 + \"HI\";");
        assertTrue(result.hasRuntimeError());
        assertTrue(result.runtimeError().contains("Operands must be two numbers or two strings"));
    }

    @Test void unaryMinusOnString_runtimeError() {
        var result = run("print -\"FabCoding\";");
        assertTrue(result.hasRuntimeError());
        assertTrue(result.runtimeError().contains("타입에 대해") && result.runtimeError().contains("연산은 지원하지 않습니다"));
    }
}
