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

    // ════════════════════════════════════════════════════════════════════════
    // 3. 심화 테스트 — Advanced scenarios
    // ════════════════════════════════════════════════════════════════════════

    // ── Complex arithmetic ────────────────────────────────────────────────────

    @Test void complexArithmeticPrecedence() {
        // 2 + 3 * 4 - 6 / 2 = 2 + 12 - 3 = 11
        assertEquals("11", run("print 2 + 3 * 4 - 6 / 2;").stdout());
    }

    @Test void nestedGrouping() {
        // (2 + 3) * (4 - 1) = 5 * 3 = 15
        assertEquals("15", run("print (2 + 3) * (4 - 1);").stdout());
    }

    // ── Logical operators (short-circuit) ─────────────────────────────────────

    @Test void logicalAndBothTrue() {
        assertEquals("yes", run("if (1 < 2 and 3 < 4) { print \"yes\"; } else { print \"no\"; }").stdout());
    }

    @Test void logicalAndFirstFalse_shortCircuit() {
        assertEquals("no", run("if (1 > 2 and 3 < 4) { print \"yes\"; } else { print \"no\"; }").stdout());
    }

    @Test void logicalOrFirstTrue_shortCircuit() {
        assertEquals("yes", run("if (1 < 2 or 1 > 2) { print \"yes\"; } else { print \"no\"; }").stdout());
    }

    @Test void logicalOrBothFalse() {
        assertEquals("no", run("if (1 > 2 or 3 > 4) { print \"yes\"; } else { print \"no\"; }").stdout());
    }

    // ── Complex scope scenarios ───────────────────────────────────────────────

    @Test void tripleNestedScopeShadowing() {
        // 각 scope마다 다른 x 값
        String src = "var x = 1; { var x = 2; { var x = 3; print x; } print x; } print x;";
        assertEquals("3\n2\n1", run(src).stdout());
    }

    @Test void modifyOuterFromDeepNestedScope() {
        // 3단계 중첩에서 전역 변수 수정
        String src = "var total = 0; { { { total = total + 10; } } } print total;";
        assertEquals("10", run(src).stdout());
    }

    // ── Complex for loop ──────────────────────────────────────────────────────

    @Test void forLoopComputesSum() {
        // 0 + 1 + 2 + 3 + 4 = 10
        String src = "var sum = 0; for (var i = 0; i < 5; i = i + 1) { sum = sum + i; } print sum;";
        assertEquals("10", run(src).stdout());
    }

    @Test void nestedForLoops() {
        // 3 * 3 = 9 반복
        String src = "var count = 0; for (var i = 0; i < 3; i = i + 1) { for (var j = 0; j < 3; j = j + 1) { count = count + 1; } } print count;";
        assertEquals("9", run(src).stdout());
    }

    @Test void forLoopZeroIterations() {
        // 조건이 처음부터 false → body 실행 안 됨
        assertEquals("done", run("for (var i = 0; i < 0; i = i + 1) { print i; } print \"done\";").stdout());
    }

    @Test void forBodyVarShadowsInitVar() {
        // body 안의 i(99)는 init의 i(0, 1)와 별도 scope
        String src = "for (var i = 0; i < 2; i = i + 1) { var i = 99; print i; }";
        assertEquals("99\n99", run(src).stdout());
    }

    @Test void stringConcatBuildInLoop() {
        String src = "var s = \"\"; for (var i = 0; i < 3; i = i + 1) { s = s + \"x\"; } print s;";
        assertEquals("xxx", run(src).stdout());
    }

    // ── if-else chain ─────────────────────────────────────────────────────────

    @Test void ifElseChain() {
        // else { if ... } 로 else-if 구조 표현
        String src = "var x = 5; if (x < 3) { print \"low\"; } else { if (x < 7) { print \"mid\"; } else { print \"high\"; } }";
        assertEquals("mid", run(src).stdout());
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test void uninitializedVarIsNil() {
        assertEquals("nil", run("var a; print a;").stdout());
    }

    @Test void assignmentExpressionReturnsValue() {
        // assignment은 expression이라 값을 반환함
        assertEquals("42", run("var a; print a = 42;").stdout());
    }

    // ── REPL session (complex) ────────────────────────────────────────────────

    @Test void replSessionWithLoop() {
        // 1 + 2 + 3 = 6
        String out = runSession(
                "var result = 0;",
                "for (var i = 1; i < 4; i = i + 1) { result = result + i; }",
                "print result;"
        );
        assertEquals("6", out);
    }

    // ── Additional runtime errors ─────────────────────────────────────────────

    @Test void divisionByZero_runtimeError() {
        var result = run("print 10 / 0;");
        assertTrue(result.hasRuntimeError());
        assertTrue(result.runtimeError().contains("Division by zero"));
    }

    @Test void variableOutOfScope_runtimeError() {
        // 블록 안에서 선언된 변수는 블록 밖에서 접근 불가
        var result = run("{ var a = 1; } print a;");
        assertTrue(result.hasRuntimeError());
        assertTrue(result.runtimeError().contains("Undefined variable 'a'"));
    }

    @Test void assignUndefinedVariable_runtimeError() {
        var result = run("a = 5;");
        assertTrue(result.hasRuntimeError());
        assertTrue(result.runtimeError().contains("Undefined variable 'a'"));
    }

    @Test void subtractStrings_runtimeError() {
        var result = run("print \"a\" - \"b\";");
        assertTrue(result.hasRuntimeError());
        assertTrue(result.runtimeError().contains("Operands must be numbers"));
    }

    @Test void compareNonNumbers_runtimeError() {
        var result = run("print \"a\" < \"b\";");
        assertTrue(result.hasRuntimeError());
        assertTrue(result.runtimeError().contains("Operands must be numbers"));
    }
}
