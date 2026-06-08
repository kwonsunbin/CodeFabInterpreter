package org.example.codefab.executor;

import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.example.codefab.checker.Checker;
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
 * 두 가지를 검증한다:
 * <ul>
 *   <li><b>상수 폴딩</b>: Checker가 미리 계산한 {@code Expr.foldedValue}가 있으면
 *       Executor가 자식을 평가하지 않고 즉시 반환한다 (런타임 계산 0회).</li>
 *   <li><b>스코프 통합/주입</b>: Checker와 Executor가 하나의 {@link Environment}를
 *       공유하면, Checker가 분석한 전역 스코프 위에서 Executor가 그대로 실행된다.</li>
 * </ul>
 * 손 AST에 foldedValue를 직접 세팅하는 방식이 Checker에 대한 Test Double 역할을 한다.
 */
class OptimizationIntegrationTest {

    private Executor executor;

    @BeforeEach
    void setUp() { executor = new Executor(new Logger(false)); }

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

    private Token tok(TokenType type, String origin) { return new Token(type, origin, null, 1); }
    private Token varTok(String name)                { return new Token(TokenType.IDENTIFIER, name, null, 1); }
    private Expr.Literal num(double v)               { return new Expr.Literal(v); }

    // ════════════════════════════════════════════════════════════════════════
    // 1. 상수 폴딩 — foldedValue가 있으면 자식을 평가하지 않고 즉시 반환
    //    (런타임 계산 0회 검증: 자식이 평가되면 터지는 폭탄식을 자식으로 둔다)
    // ════════════════════════════════════════════════════════════════════════

    /** 평가되는 순간 RuntimeError를 던지는 식(미정의 변수 읽기). 폴딩되면 평가되지 않는다. */
    private Expr bomb() { return new Expr.Variable(varTok("UNDEFINED_BOOM")); }

    @Test void folding_binary_usesFoldedValue_andSkipsChildren() {
        var b = new Expr.Binary(bomb(), tok(TokenType.PLUS, "+"), bomb());
        b.foldedValue = 42.0;
        assertEquals("42", exec(List.of(new Stmt.Print(b))));
    }

    @Test void folding_unary_usesFoldedValue_andSkipsChild() {
        var u = new Expr.Unary(tok(TokenType.MINUS, "-"), bomb());
        u.foldedValue = -7.0;
        assertEquals("-7", exec(List.of(new Stmt.Print(u))));
    }

    @Test void folding_grouping_usesFoldedValue_andSkipsChild() {
        var g = new Expr.Grouping(bomb());
        g.foldedValue = 5.0;
        assertEquals("5", exec(List.of(new Stmt.Print(g))));
    }

    @Test void folding_comparison_usesFoldedValue_andSkipsChildren() {
        var c = new Expr.Comparison(bomb(), tok(TokenType.LESS, "<"), bomb());
        c.foldedValue = true;
        assertEquals("true", exec(List.of(new Stmt.Print(c))));
    }

    @Test void folding_logical_usesFoldedValue_andSkipsChildren() {
        var lo = new Expr.Logical(bomb(), tok(TokenType.AND, "and"), bomb());
        lo.foldedValue = false;
        assertEquals("false", exec(List.of(new Stmt.Print(lo))));
    }

    @Test void folding_absent_fallsBackToEvaluation() {
        // foldedValue가 없으면 정상 평가: 1 + 2 = 3
        var b = new Expr.Binary(num(1.0), tok(TokenType.PLUS, "+"), num(2.0));
        assertEquals("3", exec(List.of(new Stmt.Print(b))));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. 스코프 통합 — Checker가 생성한 Environment를 Executor가 주입받아 활용
    // ════════════════════════════════════════════════════════════════════════

    @Test void sharedEnvironment_checkerDeclaresGlobals_executorRunsOnSameEnv() {
        // 같은 Environment를 Checker와 Executor가 공유한다.
        var shared      = new Environment();
        var checker     = new Checker(shared);
        var sharedExec  = new Executor(new Logger(false), shared);

        // var a = 1; print a;  (손 AST)
        var aTok    = varTok("a");
        var decl    = new Stmt.Var(aTok, num(1.0));
        var print   = new Stmt.Print(new Expr.Variable(aTok));
        var program = List.<Stmt>of(decl, print);

        // Checker가 먼저 분석(전역에 a 선언) — 에러 없어야 함
        assertTrue(checker.check(program).ok());

        // 같은 Environment 위에서 Executor 실행 → "1"
        var baos = new ByteArrayOutputStream();
        var saved = System.out;
        System.setOut(new PrintStream(baos));
        try { sharedExec.run(program); }
        finally { System.out.flush(); System.setOut(saved); }
        assertEquals("1", baos.toString().replace("\r\n", "\n").strip());

        // 실행이 채운 값이 공유 Environment에 그대로 보인다
        assertEquals(1.0, shared.getByName("a"));
    }
}
