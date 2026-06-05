package org.example.codefab.checker;

import org.example.codefab.assembler.Assembler;
import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CheckerFoldTest {

    CheckerFold checker;

    @BeforeEach void setUp() {
        checker = new CheckerFold();
    }

    private List<Stmt> assembleAndCheck(String src) {
        List<Stmt> program = new Assembler().assemble(src);
        checker.check(program);
        return program;
    }

    private CheckResult checkResult(String src) {
        List<Stmt> program = new Assembler().assemble(src);
        return checker.check(program);
    }

    // ── 기존 에러 검출 동작 유지 확인 ────────────────────────────────────────────

    @Test void validCodeHasNoErrors() {
        assertTrue(checkResult("var a = 1; print a;").ok());
    }

    @Test void duplicateDeclarationStillDetected() {
        assertFalse(checkResult("{ var a = 1; var a = 2; }").ok());
    }

    @Test void selfReferenceStillDetected() {
        assertFalse(checkResult("{ var a = a; }").ok());
    }

    @Test void undeclaredVariableStillDetected() {
        assertFalse(checkResult("print x;").ok());
    }

    // ── 상수 폴딩 검증 ────────────────────────────────────────────────────────────

    @Test void simpleAdditionFolded() {
        var program = assembleAndCheck("print 1 + 2;");
        Expr.Binary b = (Expr.Binary) ((Stmt.Print) program.get(0)).expression;
        assertEquals(3.0, b.foldedValue);
    }

    @Test void multiplicationFolded() {
        var program = assembleAndCheck("print 2 * 3;");
        Expr.Binary b = (Expr.Binary) ((Stmt.Print) program.get(0)).expression;
        assertEquals(6.0, b.foldedValue);
    }

    @Test void divisionFolded() {
        var program = assembleAndCheck("print 10 / 2;");
        Expr.Binary b = (Expr.Binary) ((Stmt.Print) program.get(0)).expression;
        assertEquals(5.0, b.foldedValue);
    }

    @Test void complexConstantExpressionFolded() {
        // 1*2*3*4*5/6+7+8+9 = 44.0
        var program = assembleAndCheck("print 1 * 2 * 3 * 4 * 5 / 6 + 7 + 8 + 9;");
        Expr.Binary top = (Expr.Binary) ((Stmt.Print) program.get(0)).expression;
        assertEquals(44.0, top.foldedValue);
    }

    @Test void groupingOfConstantsFolded() {
        var program = assembleAndCheck("print (2 + 3);");
        Expr.Grouping g = (Expr.Grouping) ((Stmt.Print) program.get(0)).expression;
        assertEquals(5.0, g.foldedValue);
    }

    @Test void unaryNegationFolded() {
        // -(5): Grouping이 먼저 5.0으로 폴딩, Unary가 -5.0으로 폴딩
        var program = assembleAndCheck("print -(5);");
        Expr.Unary u = (Expr.Unary) ((Stmt.Print) program.get(0)).expression;
        assertEquals(-5.0, u.foldedValue);
    }

    @Test void comparisonFolded() {
        var program = assembleAndCheck("print 1 < 2;");
        Expr.Comparison c = (Expr.Comparison) ((Stmt.Print) program.get(0)).expression;
        assertEquals(true, c.foldedValue);
    }

    @Test void stringConcatenationFolded() {
        var program = assembleAndCheck("print \"hello\" + \" world\";");
        Expr.Binary b = (Expr.Binary) ((Stmt.Print) program.get(0)).expression;
        assertEquals("hello world", b.foldedValue);
    }

    // ── 폴딩 불가 케이스 ────────────────────────────────────────────────────────

    @Test void divisionByZeroNotFolded() {
        // 0 나누기는 런타임 에러로 위임 — 폴딩하지 않는다
        var program = assembleAndCheck("print 1 / 0;");
        Expr.Binary b = (Expr.Binary) ((Stmt.Print) program.get(0)).expression;
        assertNull(b.foldedValue);
    }

    @Test void expressionWithVariableNotFolded() {
        // 변수가 포함된 표현식은 런타임 의존 — 전체 폴딩 불가
        var program = assembleAndCheck("var a = 1; print a + 2;");
        Expr.Binary b = (Expr.Binary) ((Stmt.Print) program.get(1)).expression;
        assertNull(b.foldedValue);
    }

    @Test void depthNotSetByCheckerFold() {
        // CheckerFold는 depth를 설정하지 않는다 — 기본값 -1 유지
        var program = assembleAndCheck("var a = 1; print a;");
        Expr.Variable v = (Expr.Variable) ((Stmt.Print) program.get(1)).expression;
        assertEquals(-1, v.depth);
    }

    // ── 루프 내 상수 부분식 폴딩 (핵심 최적화 케이스) ──────────────────────────────

    @Test void constantSubExprInLoopBodyFolded() {
        // total = total + (1*2*3)
        // 우변 (1*2*3)만 폴딩됨, 좌변 total은 런타임 값이라 전체 폴딩 불가
        var program = assembleAndCheck(
            "var total = 0; for (var i = 0; i < 3; i = i + 1) { total = total + (1 * 2 * 3); }"
        );

        Stmt.For      forStmt  = (Stmt.For)    program.get(1);
        Stmt.Block    body     = (Stmt.Block)  forStmt.body;
        Expr.Assign   assign   = (Expr.Assign) ((Stmt.Expression) body.statements.get(0)).expression;
        Expr.Binary   addExpr  = (Expr.Binary)   assign.value;      // total + (1*2*3)
        Expr.Grouping grouping = (Expr.Grouping) addExpr.right;     // (1*2*3)

        assertEquals(6.0, grouping.foldedValue);  // 상수 부분식 폴딩됨
        assertNull(addExpr.foldedValue);           // total + ... 은 폴딩 불가
    }
}
