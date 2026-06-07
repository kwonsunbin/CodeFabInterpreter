package org.example.codefab.executor;

import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
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
 * Checker가 산출하는 최적화 메타데이터를 Executor가 실제로 소비하는지 검증한다.
 * <ul>
 *   <li>정적 바인딩: {@code Variable.depth}+{@code slot} / {@code Assign.depth}+{@code slot} → {@code Environment.getAt/setAt} (이름 해싱 없는 O(1) 인덱스 접근)</li>
 *   <li>상수 폴딩: {@code Expr.foldedValue}가 있으면 자식을 평가하지 않고 즉시 반환</li>
 * </ul>
 *
 * 손 AST에 depth/foldedValue를 직접 세팅해 Checker의 출력을 흉내내는 방식이
 * Checker에 대한 Test Double 역할을 한다 (codefab.txt Chapter 4.3).
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
    private Expr.Literal str(String s)               { return new Expr.Literal(s); }

    /** depth만 세팅(slot은 -1로 미해석) — fallback 경로 테스트용. */
    private Expr.Variable varRead(String name, int depth) {
        var v = new Expr.Variable(varTok(name));
        v.depth = depth;
        return v;
    }

    /** (depth, slot)을 직접 세팅 — 슬롯 기반 정적 바인딩 경로 테스트용. */
    private Expr.Variable varAt(String name, int depth, int slot) {
        var v = new Expr.Variable(varTok(name));
        v.depth = depth;
        v.slot  = slot;
        return v;
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. 정적 바인딩 — (depth, slot)으로 정확한 위치를 즉시 지정
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 같은 이름 x가 global("global", depth2)과 outer 블록("outer", depth1)에 존재.
     * 최내곽 블록에서 x를 (depth, slot=0)으로 직접 읽는다 — 가까운 쪽이 아니라
     * 지정한 depth의 스코프로 즉시 점프함을 검증.
     */
    private String readShadowedXAtDepth(int depth) {
        var xTok       = varTok("x");
        var declGlobal = new Stmt.Var(xTok, str("global"));
        var declOuter  = new Stmt.Var(xTok, str("outer"));
        var innerPrint = new Stmt.Print(varAt("x", depth, 0));
        var innerBlock = new Stmt.Block(List.of(innerPrint));
        var outerBlock = new Stmt.Block(List.of(declOuter, innerBlock));
        return exec(List.of(declGlobal, outerBlock));
    }

    @Test void staticBinding_depth2_jumpsToGlobal_skippingNearer() {
        // 가까운 "outer"(depth 1)를 건너뛰고 global로 즉시 점프
        assertEquals("global", readShadowedXAtDepth(2));
    }

    @Test void staticBinding_depth1_resolvesOuter() {
        assertEquals("outer", readShadowedXAtDepth(1));
    }

    @Test void staticBinding_slotAddressesByIndex_notName() {
        // 한 스코프에 a(slot 0)="A", b(slot 1)="B".
        // Variable의 이름은 "a"지만 slot=1로 지정 → 이름이 아니라 슬롯으로 "B"를 읽음.
        var aTok  = varTok("a");
        var bTok  = varTok("b");
        var declA = new Stmt.Var(aTok, str("A"));
        var declB = new Stmt.Var(bTok, str("B"));
        var read  = new Stmt.Print(varAt("a", 0, 1)); // 이름 a, 그러나 slot 1
        var block = new Stmt.Block(List.of(declA, declB, read));
        assertEquals("B", exec(List.of(block)));
    }

    @Test void staticBinding_assign_setAtWritesTargetedSlot() {
        // 최내곽에서 x = "W"를 (depth 2, slot 0)으로 → global x에 기록.
        // 종료 후 전역 x를 읽으면 "W" (nearest인 outer가 아니라 global이 바뀜)
        var xTok       = varTok("x");
        var declGlobal = new Stmt.Var(xTok, str("g"));
        var declOuter  = new Stmt.Var(xTok, str("o"));
        var assign     = new Expr.Assign(xTok, str("W"));
        assign.depth   = 2; // global
        assign.slot    = 0;
        var innerBlock = new Stmt.Block(List.of(new Stmt.Expression(assign)));
        var outerBlock = new Stmt.Block(List.of(declOuter, innerBlock));
        var printGlobal = new Stmt.Print(new Expr.Variable(xTok)); // depth -1 → 전역 동적 조회
        assertEquals("W", exec(List.of(declGlobal, outerBlock, printGlobal)));
    }

    @Test void staticBinding_unresolved_fallsBackToDynamicLookup() {
        // depth/slot 미해석(-1)이면 기존 이름 기반 동적 조회로 폴백
        var xTok = varTok("x");
        var decl = new Stmt.Var(xTok, num(7.0));
        var print = new Stmt.Print(varRead("x", -1));
        assertEquals("7", exec(List.of(decl, print)));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. 상수 폴딩 — foldedValue가 있으면 자식을 평가하지 않고 즉시 반환
    //    (런타임 계산 횟수 0회 검증: 자식이 평가되면 터지는 폭탄식을 자식으로 둔다)
    // ════════════════════════════════════════════════════════════════════════

    /** 평가되는 순간 RuntimeError를 던지는 식 (미정의 변수 읽기). 폴딩되면 평가 안 됨. */
    private Expr bomb() { return varRead("UNDEFINED_BOOM", -1); }

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
}
