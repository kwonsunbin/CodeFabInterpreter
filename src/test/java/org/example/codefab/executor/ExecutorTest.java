package org.example.codefab.executor;

import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.example.codefab.error.RuntimeError;
import org.example.codefab.log.Logger;
import org.example.codefab.token.Token;
import org.example.codefab.token.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Red-phase hand-AST tests for Executor (손 AST, Assembler/Lexer/Parser 불필요).
 * <p>
 * 각 테스트는 AST 노드를 직접 생성하여 Executor에 넘긴다.
 * 현재 모든 visitor 메서드가 UnsupportedOperationException 을 던지므로
 * stringify_* 를 제외한 전체 테스트가 Red 상태다.
 */
class ExecutorTest {

    private Executor executor;

    @BeforeEach
    void setUp() {
        executor = new Executor(new Logger(false));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    /**
     * stdout 을 캡처하면서 program 실행, 결과 문자열 반환
     */
    private String exec(List<Stmt> program) {
        var baos = new ByteArrayOutputStream();
        var saved = System.out;
        System.setOut(new PrintStream(baos));
        try {
            executor.run(program);
        } finally {
            System.out.flush();
            System.setOut(saved);
        }
        return baos.toString().replace("\r\n", "\n").strip();
    }

    private Token tok(TokenType type, String origin) {
        return new Token(type, origin, null, 1);
    }

    private Token varTok(String name) {
        return new Token(TokenType.IDENTIFIER, name, null, 1);
    }

    private Expr num(double v)    { return Expr.builder().literalValue(v).build(); }
    private Expr str(String s)    { return Expr.builder().literalValue(s).build(); }
    private Expr bool(boolean b)  { return Expr.builder().literalValue(b).build(); }

    // ════════════════════════════════════════════════════════════════════════
    // 1. Literal & stringify
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void printLiteralInteger() {
        // 5.0 → "5" (.0 없이)
        assertEquals("5", exec(List.of(new Stmt.Print(num(5.0)))));
    }

    @Test
    void printLiteralFloat() {
        // 3.14 → "3.14"
        assertEquals("3.14", exec(List.of(new Stmt.Print(num(3.14)))));
    }

    @Test
    void printLiteralString() {
        assertEquals("hello", exec(List.of(new Stmt.Print(str("hello")))));
    }

    @Test
    void printLiteralTrue() {
        assertEquals("true", exec(List.of(new Stmt.Print(bool(true)))));
    }

    @Test
    void printLiteralFalse() {
        assertEquals("false", exec(List.of(new Stmt.Print(bool(false)))));
    }

    @Test
    void printLiteralNull() {
        // null → "nil"
        assertEquals("nil", exec(List.of(new Stmt.Print(
                Expr.builder().literalValue(null).build()))));
    }

    @Test
    void printGrouping() {
        // (5) → "5"
        assertEquals("5", exec(List.of(new Stmt.Print(
                Expr.builder().expression(num(5.0)).build()))));
    }

    // ── stringify 정적 메서드 (이미 구현됨 → 바로 Green, 사양 문서화 용도) ──

    @Test
    void stringify_integralDouble_noDecimalPoint() {
        assertEquals("5", Executor.stringify(5.0));
        assertEquals("0", Executor.stringify(0.0));
        assertEquals("-3", Executor.stringify(-3.0));
    }

    @Test
    void stringify_floatPreservesDecimal() {
        assertEquals("3.14", Executor.stringify(3.14));
    }

    @Test
    void stringify_null_isNil() {
        assertEquals("nil", Executor.stringify(null));
    }

    @Test
    void stringify_boolean() {
        assertEquals("true", Executor.stringify(true));
        assertEquals("false", Executor.stringify(false));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. Binary 산술
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void printAddition() {
        // print 1 + 2; → "3"
        var expr = Expr.builder().left(num(1.0)).op(tok(TokenType.PLUS, "+")).right(num(2.0)).build();
        assertEquals("3", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void printSubtraction() {
        // print 10 - 4; → "6"
        var expr = Expr.builder().left(num(10.0)).op(tok(TokenType.MINUS, "-")).right(num(4.0)).build();
        assertEquals("6", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void printMultiplication() {
        // print 3 * 4; → "12"
        var expr = Expr.builder().left(num(3.0)).op(tok(TokenType.STAR, "*")).right(num(4.0)).build();
        assertEquals("12", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void printDivision() {
        // print 8 / 2; → "4"
        var expr = Expr.builder().left(num(8.0)).op(tok(TokenType.SLASH, "/")).right(num(2.0)).build();
        assertEquals("4", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void printDivision_fractionalResult() {
        // print 7 / 2; → "3.5"
        var expr = Expr.builder().left(num(7.0)).op(tok(TokenType.SLASH, "/")).right(num(2.0)).build();
        assertEquals("3.5", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void division_byZero_throwsRuntimeError() {
        // print 10 / 0; → RuntimeError (0으로 나누기)
        var expr = Expr.builder().left(num(10.0)).op(tok(TokenType.SLASH, "/")).right(num(0.0)).build();
        var ex = assertThrows(RuntimeError.class,
                () -> exec(List.of(new Stmt.Print(expr))));
        assertTrue(ex.getMessage().contains("Division by zero"),
                "실제 메시지: " + ex.getMessage());
    }

    @Test
    void division_zeroByZero_throwsRuntimeError() {
        // print 0 / 0; → RuntimeError (0으로 나누기)
        var expr = Expr.builder().left(num(0.0)).op(tok(TokenType.SLASH, "/")).right(num(0.0)).build();
        assertThrows(RuntimeError.class, () -> exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void division_nonNumber_throwsRuntimeError() {
        // print "a" / 2; → RuntimeError (타입 불일치)
        var expr = Expr.builder().left(str("a")).op(tok(TokenType.SLASH, "/")).right(num(2.0)).build();
        var ex = assertThrows(RuntimeError.class,
                () -> exec(List.of(new Stmt.Print(expr))));
        assertTrue(ex.getMessage().contains("타입과") && ex.getMessage().contains("연산은 지원하지 않습니다"),
                "실제 메시지: " + ex.getMessage());
    }

    @Test
    void printStringConcat() {
        // print "Hello" + " World"; → "Hello World"
        var expr = Expr.builder().left(str("Hello")).op(tok(TokenType.PLUS, "+")).right(str(" World")).build();
        assertEquals("Hello World", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void numberPlusStringThrowsRuntimeError() {
        // print 1 + "hi"; → RuntimeError (타입 불일치)
        var expr = Expr.builder().left(num(1.0)).op(tok(TokenType.PLUS, "+")).right(str("hi")).build();
        var ex = assertThrows(RuntimeError.class,
                () -> exec(List.of(new Stmt.Print(expr))));
        assertTrue(ex.getMessage().contains("Operands must be two numbers or two strings"),
                "실제 메시지: " + ex.getMessage());
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. Unary
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void printUnaryMinus() {
        // print -5; → "-5"
        var expr = Expr.builder().op(tok(TokenType.MINUS, "-")).operand(num(5.0)).build();
        assertEquals("-5", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void unaryMinusOnStringThrowsRuntimeError() {
        // print -"str"; → RuntimeError
        var expr = Expr.builder().op(tok(TokenType.MINUS, "-")).operand(str("str")).build();
        var ex = assertThrows(RuntimeError.class,
                () -> exec(List.of(new Stmt.Print(expr))));
        assertTrue(ex.getMessage().contains("타입에 대해") && ex.getMessage().contains("연산은 지원하지 않습니다"),
                "실제 메시지: " + ex.getMessage());
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. Comparison
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void printLessThanTrue() {
        // print 1 < 2; → "true"
        var expr = Expr.builder().left(num(1.0)).op(tok(TokenType.LESS, "<")).right(num(2.0)).build();
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void printGreaterThanFalse() {
        // print 3 > 5; → "false"
        var expr = Expr.builder().left(num(3.0)).op(tok(TokenType.GREATER, ">")).right(num(5.0)).build();
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void equalEqual_sameNumbers_returnsTrue() {
        // print 5 == 5; → "true"
        var expr = new Expr.Comparison(num(5.0), tok(TokenType.EQUAL_EQUAL, "=="), num(5.0));
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void equalEqual_differentNumbers_returnsFalse() {
        // print 1 == 2; → "false"
        var expr = new Expr.Comparison(num(1.0), tok(TokenType.EQUAL_EQUAL, "=="), num(2.0));
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void bangEqual_differentNumbers_returnsTrue() {
        // print 1 != 2; → "true"
        var expr = new Expr.Comparison(num(1.0), tok(TokenType.BANG_EQUAL, "!="), num(2.0));
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void bangEqual_sameNumbers_returnsFalse() {
        // print 3 != 3; → "false"
        var expr = new Expr.Comparison(num(3.0), tok(TokenType.BANG_EQUAL, "!="), num(3.0));
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void equalEqual_sameStrings_returnsTrue() {
        // print "hello" == "hello"; → "true"
        var expr = new Expr.Comparison(str("hello"), tok(TokenType.EQUAL_EQUAL, "=="), str("hello"));
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void equalEqual_differentStrings_returnsFalse() {
        // print "a" == "b"; → "false"
        var expr = new Expr.Comparison(str("a"), tok(TokenType.EQUAL_EQUAL, "=="), str("b"));
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void equalEqual_sameBooleans_returnsTrue() {
        // print true == true; → "true"
        var expr = new Expr.Comparison(bool(true), tok(TokenType.EQUAL_EQUAL, "=="), bool(true));
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void equalEqual_nullAndNull_returnsTrue() {
        // print nil == nil; → "true"
        var expr = new Expr.Comparison(
                new Expr.Literal(null), tok(TokenType.EQUAL_EQUAL, "=="), new Expr.Literal(null));
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void equalEqual_nullAndNumber_returnsFalse() {
        // print nil == 1; → "false"
        var expr = new Expr.Comparison(
                new Expr.Literal(null), tok(TokenType.EQUAL_EQUAL, "=="), num(1.0));
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void equalEqual_crossType_returnsFalse() {
        // print 1 == "1"; → "false"
        var expr = new Expr.Comparison(num(1.0), tok(TokenType.EQUAL_EQUAL, "=="), str("1"));
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. Variables & assignment
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void varDeclAndPrint() {
        // var x = 10; print x; → "10"
        var xTok = varTok("x");
        var decl  = new Stmt.Var(xTok, num(10.0));
        var print = new Stmt.Print(Expr.builder().name(xTok).build());
        assertEquals("10", exec(List.of(decl, print)));
    }

    @Test
    void varDeclNoInitPrintsNil() {
        // var x; print x; → "nil"
        var xTok  = varTok("x");
        var decl  = new Stmt.Var(xTok, null);
        var print = new Stmt.Print(Expr.builder().name(xTok).build());
        assertEquals("nil", exec(List.of(decl, print)));
    }

    @Test
    void varReassignmentAndPrint() {
        // var a = 0; a = 5; print a; → "5"
        var aTok   = varTok("a");
        var decl   = new Stmt.Var(aTok, num(0.0));
        var assign = new Stmt.Expression(
                Expr.builder().name(aTok).value(num(5.0)).build());
        var print  = new Stmt.Print(Expr.builder().name(aTok).build());
        assertEquals("5", exec(List.of(decl, assign, print)));
    }

    @Test
    void undefinedVariableThrowsRuntimeError() {
        // print notDefined; → RuntimeError
        var expr = Expr.builder().name(varTok("notDefined")).build();
        var ex = assertThrows(RuntimeError.class,
                () -> exec(List.of(new Stmt.Print(expr))));
        assertTrue(ex.getMessage().contains("Undefined variable 'notDefined'"),
                "실제 메시지: " + ex.getMessage());
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. Block 스코프
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void blockScopeShadowing() {
        // var x = "global"; { var x = "inner"; print x; } print x;
        // → "inner\nglobal"
        var xTok       = varTok("x");
        var outerDecl  = new Stmt.Var(xTok, str("global"));
        var innerDecl  = new Stmt.Var(xTok, str("inner"));
        var innerPrint = new Stmt.Print(Expr.builder().name(xTok).build());
        var block      = new Stmt.Block(List.of(innerDecl, innerPrint));
        var outerPrint = new Stmt.Print(Expr.builder().name(xTok).build());
        assertEquals("inner\nglobal", exec(List.of(outerDecl, block, outerPrint)));
    }

    @Test
    void mutatingEnclosingVarInBlock() {
        // var count = 0; { count = count + 1; } print count; → "1"
        var countTok = varTok("count");
        var decl  = new Stmt.Var(countTok, num(0.0));
        var incr  = Expr.builder()
                .left(Expr.builder().name(countTok).build())
                .op(tok(TokenType.PLUS, "+"))
                .right(num(1.0)).build();
        var assign = new Stmt.Expression(
                Expr.builder().name(countTok).value(incr).build());
        var block  = new Stmt.Block(List.of(assign));
        var print  = new Stmt.Print(Expr.builder().name(countTok).build());
        assertEquals("1", exec(List.of(decl, block, print)));
    }

    @Test
    void nestedScopeReadsOuter() {
        // var outer = "A"; { var inner = "B"; { print outer + inner; } }
        // → "AB"
        var outerTok  = varTok("outer");
        var innerTok  = varTok("inner");
        var declOuter = new Stmt.Var(outerTok, str("A"));
        var declInner = new Stmt.Var(innerTok, str("B"));
        var concat    = Expr.builder()
                .left(Expr.builder().name(outerTok).build())
                .op(tok(TokenType.PLUS, "+"))
                .right(Expr.builder().name(innerTok).build()).build();
        var deepBlock  = new Stmt.Block(List.of(new Stmt.Print(concat)));
        var outerBlock = new Stmt.Block(List.of(declInner, deepBlock));
        assertEquals("AB", exec(List.of(declOuter, outerBlock)));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 7. if / else
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void ifTruePrints() {
        // if (true) print "yes"; → "yes"
        var stmt = new Stmt.If(bool(true), new Stmt.Print(str("yes")), null);
        assertEquals("yes", exec(List.of(stmt)));
    }

    @Test
    void ifFalseSkips() {
        // if (false) print "no"; → ""
        var stmt = new Stmt.If(bool(false), new Stmt.Print(str("no")), null);
        assertEquals("", exec(List.of(stmt)));
    }

    @Test
    void ifFalseElsePrints() {
        // if (false) print "no"; else print "yes"; → "yes"
        var stmt = new Stmt.If(bool(false),
                new Stmt.Print(str("no")),
                new Stmt.Print(str("yes")));
        assertEquals("yes", exec(List.of(stmt)));
    }

    @Test
    void danglingElseBindsToNearest() {
        // if (true) if (false) print "kfc"; else print "bbq"; → "bbq"
        var inner = new Stmt.If(bool(false),
                new Stmt.Print(str("kfc")),
                new Stmt.Print(str("bbq")));
        var outer = new Stmt.If(bool(true), inner, null);
        assertEquals("bbq", exec(List.of(outer)));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 8. for 루프
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void forLoopPrints0to2() {
        // for (var j = 0; j < 3; j = j + 1) { print j; } → "0\n1\n2"
        var jTok = varTok("j");
        var init = new Stmt.Var(jTok, num(0.0));
        var cond = Expr.builder()
                .left(Expr.builder().name(jTok).build())
                .op(tok(TokenType.LESS, "<"))
                .right(num(3.0)).build();
        var incr = Expr.builder()
                .name(jTok)
                .value(Expr.builder()
                        .left(Expr.builder().name(jTok).build())
                        .op(tok(TokenType.PLUS, "+"))
                        .right(num(1.0)).build()).build();
        var body    = new Stmt.Block(List.of(new Stmt.Print(Expr.builder().name(jTok).build())));
        var forStmt = new Stmt.For(init, cond, incr, body);
        assertEquals("0\n1\n2", exec(List.of(forStmt)));
    }

    @Test
    void forLoopVarScopedToLoop() {
        // for 변수(j)가 루프 바깥에서 보이지 않아야 한다
        var jTok = varTok("j");
        var init = new Stmt.Var(jTok, num(0.0));
        var cond = Expr.builder()
                .left(Expr.builder().name(jTok).build())
                .op(tok(TokenType.LESS, "<"))
                .right(num(1.0)).build();
        var incr = Expr.builder()
                .name(jTok)
                .value(Expr.builder()
                        .left(Expr.builder().name(jTok).build())
                        .op(tok(TokenType.PLUS, "+"))
                        .right(num(1.0)).build()).build();
        var body    = new Stmt.Block(List.of());
        var forStmt = new Stmt.For(init, cond, incr, body);
        // 루프 실행 후 j를 읽으면 UndefinedVariable RuntimeError
        var readJ = new Stmt.Print(Expr.builder().name(jTok).build());
        assertThrows(RuntimeError.class, () -> exec(List.of(forStmt, readJ)));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 9. Logical (단락 평가)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void logicalAndBothTrue() {
        // if (true and true) print "yes"; → "yes"
        var and  = Expr.builder().left(bool(true)).op(tok(TokenType.AND, "and")).right(bool(true)).build();
        var stmt = new Stmt.If(and, new Stmt.Print(str("yes")), null);
        assertEquals("yes", exec(List.of(stmt)));
    }

    @Test
    void logicalAndShortCircuit() {
        // if (false and true) print "yes"; → "" (우변 평가 안 됨)
        var and  = Expr.builder().left(bool(false)).op(tok(TokenType.AND, "and")).right(bool(true)).build();
        var stmt = new Stmt.If(and, new Stmt.Print(str("yes")), null);
        assertEquals("", exec(List.of(stmt)));
    }

    @Test
    void logicalOrSecondTrue() {
        // if (false or true) print "yes"; → "yes"
        var or   = Expr.builder().left(bool(false)).op(tok(TokenType.OR, "or")).right(bool(true)).build();
        var stmt = new Stmt.If(or, new Stmt.Print(str("yes")), null);
        assertEquals("yes", exec(List.of(stmt)));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 10. Line Coverage 100% — 미커버 라인 보완
    // ════════════════════════════════════════════════════════════════════════

    // ── visitUnary: BANG (line 119) + isTruthy non-Boolean (line 186) ────────

    @Test
    void unaryBang_onFalse_returnsTrue() {
        // !false → true   (line 119: BANG 케이스)
        var expr = Expr.builder().op(tok(TokenType.BANG, "!")).operand(bool(false)).build();
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void unaryBang_onNumber_isTruthy_returnsTrue_line186() {
        // !5.0 → false
        var expr = Expr.builder().op(tok(TokenType.BANG, "!")).operand(num(5.0)).build();
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    // ── visitUnary: default → RuntimeError (line 120) ────────────────────────

    @Test
    void unaryUnknownOperator_throwsRuntimeError() {
        // PLUS는 unary switch의 default → RuntimeError (line 120)
        var expr = Expr.builder().op(tok(TokenType.PLUS, "+")).operand(num(1.0)).build();
        assertThrows(RuntimeError.class, () -> exec(List.of(new Stmt.Print(expr))));
    }

    // ── visitBinary: default → RuntimeError (line 137) ───────────────────────

    @Test
    void binaryUnknownOperator_throwsRuntimeError() {
        // GREATER는 Binary switch의 default → RuntimeError (line 137)
        // Builder가 GREATER를 Comparison으로 분기하므로 생성자로 직접 "잘못된" 조합 생성
        var expr = new Expr.Binary(num(1.0), tok(TokenType.GREATER, ">"), num(2.0));
        assertThrows(RuntimeError.class, () -> exec(List.of(new Stmt.Print(expr))));
    }

    // ── visitComparison: GREATER_EQUAL (line 148) ────────────────────────────

    @Test
    void comparison_greaterEqual_true() {
        // 5 >= 5 → true  (line 148)
        var expr = Expr.builder().left(num(5.0)).op(tok(TokenType.GREATER_EQUAL, ">=")).right(num(5.0)).build();
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void comparison_greaterEqual_false() {
        // 3 >= 5 → false  (line 148)
        var expr = Expr.builder().left(num(3.0)).op(tok(TokenType.GREATER_EQUAL, ">=")).right(num(5.0)).build();
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    // ── visitComparison: LESS_EQUAL (line 150) ───────────────────────────────

    @Test
    void comparison_lessEqual_true() {
        // 3 <= 5 → true  (line 150)
        var expr = Expr.builder().left(num(3.0)).op(tok(TokenType.LESS_EQUAL, "<=")).right(num(5.0)).build();
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void comparison_lessEqual_false() {
        // 5 <= 3 → false  (line 150)
        var expr = Expr.builder().left(num(5.0)).op(tok(TokenType.LESS_EQUAL, "<=")).right(num(3.0)).build();
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    // ── visitComparison: default → RuntimeError (line 151) ───────────────────

    @Test
    void comparisonUnknownOperator_throwsRuntimeError() {
        // PLUS는 Comparison switch의 default → RuntimeError (line 151)
        // Builder가 PLUS를 Binary로 분기하므로 생성자로 직접 "잘못된" 조합 생성
        var expr = new Expr.Comparison(num(1.0), tok(TokenType.PLUS, "+"), num(2.0));
        assertThrows(RuntimeError.class, () -> exec(List.of(new Stmt.Print(expr))));
    }

    // ── checkNumberOperands: throw (line 196) ────────────────────────────────

    @Test
    void binaryMinus_withString_throwsRuntimeError() {
        // "str" - 5 → checkNumberOperands throw
        var expr = Expr.builder().left(str("str")).op(tok(TokenType.MINUS, "-")).right(num(5.0)).build();
        var ex = assertThrows(RuntimeError.class, () -> exec(List.of(new Stmt.Print(expr))));
        assertTrue(ex.getMessage().contains("타입과") && ex.getMessage().contains("연산은 지원하지 않습니다"));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 11. 타입별 RuntimeError 메시지 검증
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void booleanMultiplication_throwsWithTypeName() {
        // true * false → "boolean 타입과 boolean 타입에 대해 '*' 연산은 지원하지 않습니다."
        var expr = Expr.builder().left(bool(true)).op(tok(TokenType.STAR, "*")).right(bool(false)).build();
        var ex = assertThrows(RuntimeError.class, () -> exec(List.of(new Stmt.Print(expr))));
        assertTrue(ex.getMessage().contains("boolean 타입과 boolean 타입"),
                "실제 메시지: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("'*' 연산은 지원하지 않습니다"),
                "실제 메시지: " + ex.getMessage());
    }

    @Test
    void numberMinusString_throwsWithTypeName() {
        // 3 - "hello" → "number 타입과 string 타입에 대해 '-' 연산은 지원하지 않습니다."
        var expr = Expr.builder().left(num(3.0)).op(tok(TokenType.MINUS, "-")).right(str("hello")).build();
        var ex = assertThrows(RuntimeError.class, () -> exec(List.of(new Stmt.Print(expr))));
        assertTrue(ex.getMessage().contains("number 타입과 string 타입"),
                "실제 메시지: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("'-' 연산은 지원하지 않습니다"),
                "실제 메시지: " + ex.getMessage());
    }

    @Test
    void unaryMinus_onBoolean_throwsWithTypeName() {
        // -true → "boolean 타입에 대해 '-' 연산은 지원하지 않습니다."
        var expr = Expr.builder().op(tok(TokenType.MINUS, "-")).operand(bool(true)).build();
        var ex = assertThrows(RuntimeError.class, () -> exec(List.of(new Stmt.Print(expr))));
        assertTrue(ex.getMessage().contains("boolean 타입에 대해"),
                "실제 메시지: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("'-' 연산은 지원하지 않습니다"),
                "실제 메시지: " + ex.getMessage());
    }

    @Test
    void nullMultiplication_throwsWithTypeName() {
        // null * 5 → "null 타입과 number 타입에 대해 '*' 연산은 지원하지 않습니다."
        var expr = Expr.builder().left(Expr.builder().literalValue(null).build())
                .op(tok(TokenType.STAR, "*")).right(num(5.0)).build();
        var ex = assertThrows(RuntimeError.class, () -> exec(List.of(new Stmt.Print(expr))));
        assertTrue(ex.getMessage().contains("null 타입과 number 타입"),
                "실제 메시지: " + ex.getMessage());
    }

    // ════════════════════════════════════════════════════════════════════════
    // 12. 문자열 대소 비교 (사전순, lexicographic)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void stringGreaterThan_true() {
        // "b" > "a" → true
        var expr = new Expr.Comparison(str("b"), tok(TokenType.GREATER, ">"), str("a"));
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void stringGreaterThan_false() {
        // "a" > "b" → false
        var expr = new Expr.Comparison(str("a"), tok(TokenType.GREATER, ">"), str("b"));
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void stringGreaterThan_equalStrings_false() {
        // "abc" > "abc" → false
        var expr = new Expr.Comparison(str("abc"), tok(TokenType.GREATER, ">"), str("abc"));
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void stringGreaterEqual_equal_true() {
        // "abc" >= "abc" → true
        var expr = new Expr.Comparison(str("abc"), tok(TokenType.GREATER_EQUAL, ">="), str("abc"));
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void stringGreaterEqual_greater_true() {
        // "b" >= "a" → true
        var expr = new Expr.Comparison(str("b"), tok(TokenType.GREATER_EQUAL, ">="), str("a"));
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void stringGreaterEqual_less_false() {
        // "a" >= "b" → false
        var expr = new Expr.Comparison(str("a"), tok(TokenType.GREATER_EQUAL, ">="), str("b"));
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void stringLessThan_true() {
        // "apple" < "banana" → true
        var expr = new Expr.Comparison(str("apple"), tok(TokenType.LESS, "<"), str("banana"));
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void stringLessThan_false() {
        // "z" < "a" → false
        var expr = new Expr.Comparison(str("z"), tok(TokenType.LESS, "<"), str("a"));
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void stringLessEqual_equal_true() {
        // "hi" <= "hi" → true
        var expr = new Expr.Comparison(str("hi"), tok(TokenType.LESS_EQUAL, "<="), str("hi"));
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void stringLessEqual_less_true() {
        // "a" <= "b" → true
        var expr = new Expr.Comparison(str("a"), tok(TokenType.LESS_EQUAL, "<="), str("b"));
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void stringLessEqual_greater_false() {
        // "z" <= "a" → false
        var expr = new Expr.Comparison(str("z"), tok(TokenType.LESS_EQUAL, "<="), str("a"));
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void stringVsNumber_greaterThan_comparesByStringify() {
        // "hello" > 5 → stringify(5)="5", "hello".compareTo("5") > 0 → true
        var expr = new Expr.Comparison(str("hello"), tok(TokenType.GREATER, ">"), num(5.0));
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test
    void numberVsString_lessThan_comparesByStringify() {
        // 3 < "abc" → stringify(3)="3", "3".compareTo("abc") < 0 → true
        var expr = new Expr.Comparison(num(3.0), tok(TokenType.LESS, "<"), str("abc"));
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }
}
