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
 *
 * 각 테스트는 AST 노드를 직접 생성하여 Executor에 넘긴다.
 * 현재 모든 visitor 메서드가 UnsupportedOperationException 을 던지므로
 * stringify_* 를 제외한 전체 테스트가 Red 상태다.
 */
class ExecutorTest {

    private Executor executor;

    @BeforeEach void setUp() {
        executor = new Executor(new Logger(false));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    /** stdout 을 캡처하면서 program 실행, 결과 문자열 반환 */
    private String exec(List<Stmt> program) {
        var baos  = new ByteArrayOutputStream();
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

    private Expr.Literal num(double v)   { return new Expr.Literal(v); }
    private Expr.Literal str(String s)   { return new Expr.Literal(s); }
    private Expr.Literal bool(boolean b) { return new Expr.Literal(b); }

    // ════════════════════════════════════════════════════════════════════════
    // 1. Literal & stringify
    // ════════════════════════════════════════════════════════════════════════

    @Test void printLiteralInteger() {
        // 5.0 → "5" (.0 없이)
        assertEquals("5", exec(List.of(new Stmt.Print(num(5.0)))));
    }

    @Test void printLiteralFloat() {
        // 3.14 → "3.14"
        assertEquals("3.14", exec(List.of(new Stmt.Print(num(3.14)))));
    }

    @Test void printLiteralString() {
        assertEquals("hello", exec(List.of(new Stmt.Print(str("hello")))));
    }

    @Test void printLiteralTrue() {
        assertEquals("true", exec(List.of(new Stmt.Print(bool(true)))));
    }

    @Test void printLiteralFalse() {
        assertEquals("false", exec(List.of(new Stmt.Print(bool(false)))));
    }

    @Test void printLiteralNull() {
        // null → "nil"
        assertEquals("nil", exec(List.of(new Stmt.Print(new Expr.Literal(null)))));
    }

    @Test void printGrouping() {
        // (5) → "5"
        assertEquals("5", exec(List.of(new Stmt.Print(new Expr.Grouping(num(5.0))))));
    }

    // ── stringify 정적 메서드 (이미 구현됨 → 바로 Green, 사양 문서화 용도) ──

    @Test void stringify_integralDouble_noDecimalPoint() {
        assertEquals("5",   Executor.stringify(5.0));
        assertEquals("0",   Executor.stringify(0.0));
        assertEquals("-3",  Executor.stringify(-3.0));
    }

    @Test void stringify_floatPreservesDecimal() {
        assertEquals("3.14", Executor.stringify(3.14));
    }

    @Test void stringify_null_isNil() {
        assertEquals("nil", Executor.stringify(null));
    }

    @Test void stringify_boolean() {
        assertEquals("true",  Executor.stringify(true));
        assertEquals("false", Executor.stringify(false));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. Binary 산술
    // ════════════════════════════════════════════════════════════════════════

    @Test void printAddition() {
        // print 1 + 2; → "3"
        var expr = new Expr.Binary(num(1.0), tok(TokenType.PLUS, "+"), num(2.0));
        assertEquals("3", exec(List.of(new Stmt.Print(expr))));
    }

    @Test void printSubtraction() {
        // print 10 - 4; → "6"
        var expr = new Expr.Binary(num(10.0), tok(TokenType.MINUS, "-"), num(4.0));
        assertEquals("6", exec(List.of(new Stmt.Print(expr))));
    }

    @Test void printMultiplication() {
        // print 3 * 4; → "12"
        var expr = new Expr.Binary(num(3.0), tok(TokenType.STAR, "*"), num(4.0));
        assertEquals("12", exec(List.of(new Stmt.Print(expr))));
    }

    @Test void printDivision() {
        // print 8 / 2; → "4"
        var expr = new Expr.Binary(num(8.0), tok(TokenType.SLASH, "/"), num(2.0));
        assertEquals("4", exec(List.of(new Stmt.Print(expr))));
    }

    @Test void printStringConcat() {
        // print "Hello" + " World"; → "Hello World"
        var expr = new Expr.Binary(str("Hello"), tok(TokenType.PLUS, "+"), str(" World"));
        assertEquals("Hello World", exec(List.of(new Stmt.Print(expr))));
    }

    @Test void numberPlusStringThrowsRuntimeError() {
        // print 1 + "hi"; → RuntimeError (타입 불일치)
        var expr = new Expr.Binary(num(1.0), tok(TokenType.PLUS, "+"), str("hi"));
        var ex = assertThrows(RuntimeError.class,
                () -> exec(List.of(new Stmt.Print(expr))));
        assertTrue(ex.getMessage().contains("Operands must be two numbers or two strings"),
                "실제 메시지: " + ex.getMessage());
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. Unary
    // ════════════════════════════════════════════════════════════════════════

    @Test void printUnaryMinus() {
        // print -5; → "-5"
        var expr = new Expr.Unary(tok(TokenType.MINUS, "-"), num(5.0));
        assertEquals("-5", exec(List.of(new Stmt.Print(expr))));
    }

    @Test void unaryMinusOnStringThrowsRuntimeError() {
        // print -"str"; → RuntimeError
        var expr = new Expr.Unary(tok(TokenType.MINUS, "-"), str("str"));
        var ex = assertThrows(RuntimeError.class,
                () -> exec(List.of(new Stmt.Print(expr))));
        assertTrue(ex.getMessage().contains("Operand must be a number"),
                "실제 메시지: " + ex.getMessage());
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. Comparison
    // ════════════════════════════════════════════════════════════════════════

    @Test void printLessThanTrue() {
        // print 1 < 2; → "true"
        var expr = new Expr.Comparison(num(1.0), tok(TokenType.LESS, "<"), num(2.0));
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test void printGreaterThanFalse() {
        // print 3 > 5; → "false"
        var expr = new Expr.Comparison(num(3.0), tok(TokenType.GREATER, ">"), num(5.0));
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. Variables & assignment
    // ════════════════════════════════════════════════════════════════════════

    @Test void varDeclAndPrint() {
        // var x = 10; print x; → "10"
        var xTok  = varTok("x");
        var decl  = new Stmt.Var(xTok, num(10.0));
        var print = new Stmt.Print(new Expr.Variable(xTok));
        assertEquals("10", exec(List.of(decl, print)));
    }

    @Test void varDeclNoInitPrintsNil() {
        // var x; print x; → "nil"
        var xTok  = varTok("x");
        var decl  = new Stmt.Var(xTok, null);
        var print = new Stmt.Print(new Expr.Variable(xTok));
        assertEquals("nil", exec(List.of(decl, print)));
    }

    @Test void varReassignmentAndPrint() {
        // var a = 0; a = 5; print a; → "5"
        var aTok   = varTok("a");
        var decl   = new Stmt.Var(aTok, num(0.0));
        var assign = new Stmt.Expression(new Expr.Assign(aTok, num(5.0)));
        var print  = new Stmt.Print(new Expr.Variable(aTok));
        assertEquals("5", exec(List.of(decl, assign, print)));
    }

    @Test void undefinedVariableThrowsRuntimeError() {
        // print notDefined; → RuntimeError
        var expr = new Expr.Variable(varTok("notDefined"));
        var ex = assertThrows(RuntimeError.class,
                () -> exec(List.of(new Stmt.Print(expr))));
        assertTrue(ex.getMessage().contains("Undefined variable 'notDefined'"),
                "실제 메시지: " + ex.getMessage());
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. Block 스코프
    // ════════════════════════════════════════════════════════════════════════

    @Test void blockScopeShadowing() {
        // var x = "global"; { var x = "inner"; print x; } print x;
        // → "inner\nglobal"
        var xTok       = varTok("x");
        var outerDecl  = new Stmt.Var(xTok, str("global"));
        var innerDecl  = new Stmt.Var(xTok, str("inner"));
        var innerPrint = new Stmt.Print(new Expr.Variable(xTok));
        var block      = new Stmt.Block(List.of(innerDecl, innerPrint));
        var outerPrint = new Stmt.Print(new Expr.Variable(xTok));
        assertEquals("inner\nglobal", exec(List.of(outerDecl, block, outerPrint)));
    }

    @Test void mutatingEnclosingVarInBlock() {
        // var count = 0; { count = count + 1; } print count; → "1"
        var countTok = varTok("count");
        var decl     = new Stmt.Var(countTok, num(0.0));
        var incr     = new Expr.Binary(new Expr.Variable(countTok),
                                       tok(TokenType.PLUS, "+"), num(1.0));
        var assign   = new Stmt.Expression(new Expr.Assign(countTok, incr));
        var block    = new Stmt.Block(List.of(assign));
        var print    = new Stmt.Print(new Expr.Variable(countTok));
        assertEquals("1", exec(List.of(decl, block, print)));
    }

    @Test void nestedScopeReadsOuter() {
        // var outer = "A"; { var inner = "B"; { print outer + inner; } }
        // → "AB"
        var outerTok = varTok("outer");
        var innerTok = varTok("inner");
        var declOuter = new Stmt.Var(outerTok, str("A"));
        var declInner = new Stmt.Var(innerTok, str("B"));
        var concat    = new Expr.Binary(new Expr.Variable(outerTok),
                                        tok(TokenType.PLUS, "+"),
                                        new Expr.Variable(innerTok));
        var deepBlock  = new Stmt.Block(List.of(new Stmt.Print(concat)));
        var outerBlock = new Stmt.Block(List.of(declInner, deepBlock));
        assertEquals("AB", exec(List.of(declOuter, outerBlock)));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 7. if / else
    // ════════════════════════════════════════════════════════════════════════

    @Test void ifTruePrints() {
        // if (true) print "yes"; → "yes"
        var stmt = new Stmt.If(bool(true), new Stmt.Print(str("yes")), null);
        assertEquals("yes", exec(List.of(stmt)));
    }

    @Test void ifFalseSkips() {
        // if (false) print "no"; → ""
        var stmt = new Stmt.If(bool(false), new Stmt.Print(str("no")), null);
        assertEquals("", exec(List.of(stmt)));
    }

    @Test void ifFalseElsePrints() {
        // if (false) print "no"; else print "yes"; → "yes"
        var stmt = new Stmt.If(bool(false),
                new Stmt.Print(str("no")),
                new Stmt.Print(str("yes")));
        assertEquals("yes", exec(List.of(stmt)));
    }

    @Test void danglingElseBindsToNearest() {
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

    @Test void forLoopPrints0to2() {
        // for (var j = 0; j < 3; j = j + 1) { print j; } → "0\n1\n2"
        var jTok    = varTok("j");
        var init    = new Stmt.Var(jTok, num(0.0));
        var cond    = new Expr.Comparison(new Expr.Variable(jTok),
                                          tok(TokenType.LESS, "<"), num(3.0));
        var incr    = new Expr.Assign(jTok,
                          new Expr.Binary(new Expr.Variable(jTok),
                                          tok(TokenType.PLUS, "+"), num(1.0)));
        var body    = new Stmt.Block(List.of(new Stmt.Print(new Expr.Variable(jTok))));
        var forStmt = new Stmt.For(init, cond, incr, body);
        assertEquals("0\n1\n2", exec(List.of(forStmt)));
    }

    @Test void forLoopVarScopedToLoop() {
        // for 변수(j)가 루프 바깥에서 보이지 않아야 한다
        var jTok    = varTok("j");
        var init    = new Stmt.Var(jTok, num(0.0));
        var cond    = new Expr.Comparison(new Expr.Variable(jTok),
                                          tok(TokenType.LESS, "<"), num(1.0));
        var incr    = new Expr.Assign(jTok,
                          new Expr.Binary(new Expr.Variable(jTok),
                                          tok(TokenType.PLUS, "+"), num(1.0)));
        var body    = new Stmt.Block(List.of());
        var forStmt = new Stmt.For(init, cond, incr, body);
        // 루프 실행 후 j를 읽으면 UndefinedVariable RuntimeError
        var readJ   = new Stmt.Print(new Expr.Variable(jTok));
        assertThrows(RuntimeError.class, () -> exec(List.of(forStmt, readJ)));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 9. Logical (단락 평가)
    // ════════════════════════════════════════════════════════════════════════

    @Test void logicalAndBothTrue() {
        // if (true and true) print "yes"; → "yes"
        var and  = new Expr.Logical(bool(true), tok(TokenType.AND, "and"), bool(true));
        var stmt = new Stmt.If(and, new Stmt.Print(str("yes")), null);
        assertEquals("yes", exec(List.of(stmt)));
    }

    @Test void logicalAndShortCircuit() {
        // if (false and true) print "yes"; → "" (우변 평가 안 됨)
        var and  = new Expr.Logical(bool(false), tok(TokenType.AND, "and"), bool(true));
        var stmt = new Stmt.If(and, new Stmt.Print(str("yes")), null);
        assertEquals("", exec(List.of(stmt)));
    }

    @Test void logicalOrSecondTrue() {
        // if (false or true) print "yes"; → "yes"
        var or   = new Expr.Logical(bool(false), tok(TokenType.OR, "or"), bool(true));
        var stmt = new Stmt.If(or, new Stmt.Print(str("yes")), null);
        assertEquals("yes", exec(List.of(stmt)));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 10. Line Coverage 100% — 미커버 라인 보완
    // ════════════════════════════════════════════════════════════════════════

    // ── visitUnary: BANG (line 119) + isTruthy non-Boolean (line 186) ────────

    @Test void unaryBang_onFalse_returnsTrue() {
        // !false → true   (line 119: BANG 케이스)
        var expr = new Expr.Unary(tok(TokenType.BANG, "!"), bool(false));
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test void unaryBang_onNumber_isTruthy_returnsTrue_line186() {
        // !5.0 → false
        // isTruthy(5.0): null 아님(184) → Boolean 아님(185) → return true(186)
        // BANG 케이스(119)와 isTruthy return true(186) 동시 커버
        var expr = new Expr.Unary(tok(TokenType.BANG, "!"), num(5.0));
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    // ── visitUnary: default → RuntimeError (line 120) ────────────────────────

    @Test void unaryUnknownOperator_throwsRuntimeError() {
        // PLUS는 unary switch의 default → RuntimeError (line 120)
        var expr = new Expr.Unary(tok(TokenType.PLUS, "+"), num(1.0));
        assertThrows(RuntimeError.class, () -> exec(List.of(new Stmt.Print(expr))));
    }

    // ── visitBinary: default → RuntimeError (line 137) ───────────────────────

    @Test void binaryUnknownOperator_throwsRuntimeError() {
        // GREATER는 Binary switch의 default → RuntimeError (line 137)
        var expr = new Expr.Binary(num(1.0), tok(TokenType.GREATER, ">"), num(2.0));
        assertThrows(RuntimeError.class, () -> exec(List.of(new Stmt.Print(expr))));
    }

    // ── visitComparison: GREATER_EQUAL (line 148) ────────────────────────────

    @Test void comparison_greaterEqual_true() {
        // 5 >= 5 → true  (line 148)
        var expr = new Expr.Comparison(num(5.0), tok(TokenType.GREATER_EQUAL, ">="), num(5.0));
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test void comparison_greaterEqual_false() {
        // 3 >= 5 → false  (line 148)
        var expr = new Expr.Comparison(num(3.0), tok(TokenType.GREATER_EQUAL, ">="), num(5.0));
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    // ── visitComparison: LESS_EQUAL (line 150) ───────────────────────────────

    @Test void comparison_lessEqual_true() {
        // 3 <= 5 → true  (line 150)
        var expr = new Expr.Comparison(num(3.0), tok(TokenType.LESS_EQUAL, "<="), num(5.0));
        assertEquals("true", exec(List.of(new Stmt.Print(expr))));
    }

    @Test void comparison_lessEqual_false() {
        // 5 <= 3 → false  (line 150)
        var expr = new Expr.Comparison(num(5.0), tok(TokenType.LESS_EQUAL, "<="), num(3.0));
        assertEquals("false", exec(List.of(new Stmt.Print(expr))));
    }

    // ── visitComparison: default → RuntimeError (line 151) ───────────────────

    @Test void comparisonUnknownOperator_throwsRuntimeError() {
        // PLUS는 Comparison switch의 default → RuntimeError (line 151)
        var expr = new Expr.Comparison(num(1.0), tok(TokenType.PLUS, "+"), num(2.0));
        assertThrows(RuntimeError.class, () -> exec(List.of(new Stmt.Print(expr))));
    }
}
