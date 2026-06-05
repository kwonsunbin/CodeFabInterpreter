package org.example.codefab.checker;

import org.example.codefab.assembler.Assembler;
import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CheckerDepthTest {

    CheckerDepth checker;

    @BeforeEach void setUp() {
        checker = new CheckerDepth();
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

    @Test void undeclaredAssignStillDetected() {
        assertFalse(checkResult("a = 5;").ok());
    }

    // ── depth 정적 바인딩 검증 ────────────────────────────────────────────────────

    @Test void globalVariableReadHasDepthZero() {
        // scopes when scanning 'a': [globalScope] → depth 0
        var program = assembleAndCheck("var a = 1; print a;");

        Expr.Variable v = (Expr.Variable) ((Stmt.Print) program.get(1)).expression;

        assertEquals(0, v.depth);
    }

    @Test void variableOneBlockDeepHasDepthOne() {
        // scopes when scanning 'a': [blockScope, globalScope] → depth 1
        var program = assembleAndCheck("var a = 1; { print a; }");

        Stmt.Block block = (Stmt.Block) program.get(1);
        Expr.Variable v  = (Expr.Variable) ((Stmt.Print) block.statements.get(0)).expression;

        assertEquals(1, v.depth);
    }

    @Test void variableThreeBlocksDeepHasDepthThree() {
        // scopes: [block3, block2, block1, globalScope] → depth 3
        var program = assembleAndCheck("var a = 1; { { { print a; } } }");

        Stmt.Block b1 = (Stmt.Block) program.get(1);
        Stmt.Block b2 = (Stmt.Block) b1.statements.get(0);
        Stmt.Block b3 = (Stmt.Block) b2.statements.get(0);
        Expr.Variable v = (Expr.Variable) ((Stmt.Print) b3.statements.get(0)).expression;

        assertEquals(3, v.depth);
    }

    @Test void assignDepthSetCorrectly() {
        // scopes when scanning assign 'a': [blockScope, globalScope] → depth 1
        var program = assembleAndCheck("var a = 1; { a = 2; }");

        Stmt.Block block  = (Stmt.Block) program.get(1);
        Expr.Assign assign = (Expr.Assign) ((Stmt.Expression) block.statements.get(0)).expression;

        assertEquals(1, assign.depth);
    }

    @Test void forLoopVariableInConditionHasDepthZero() {
        // 'i'는 for-init 스코프에 선언됨. condition 스캔 시점 scopes: [forInitScope, globalScope]
        // → depth 0
        var program = assembleAndCheck("for (var i = 0; i < 3; i = i + 1) { }");

        Stmt.For forStmt        = (Stmt.For) program.get(0);
        Expr.Comparison cond    = (Expr.Comparison) forStmt.condition;
        Expr.Variable iInCond   = (Expr.Variable) cond.left;

        assertEquals(0, iInCond.depth);
    }

    @Test void forLoopVariableInBodyHasDepthOne() {
        // body 블록 스캔 시점 scopes: [bodyScope, forInitScope, globalScope]
        // 'i'는 forInitScope에 있으므로 → depth 1
        var program = assembleAndCheck("for (var i = 0; i < 3; i = i + 1) { print i; }");

        Stmt.For forStmt      = (Stmt.For) program.get(0);
        Stmt.Block body       = (Stmt.Block) forStmt.body;
        Expr.Variable iInBody = (Expr.Variable) ((Stmt.Print) body.statements.get(0)).expression;

        assertEquals(1, iInBody.depth);
    }

    @Test void undeclaredVariableDepthRemainsMinusOne() {
        // 에러 케이스 — depth가 기본값 -1을 유지해야 한다
        List<Stmt> program = new Assembler().assemble("print x;");
        checker.check(program);

        Expr.Variable v = (Expr.Variable) ((Stmt.Print) program.get(0)).expression;

        assertEquals(-1, v.depth);
    }

    @Test void selfReferenceDepthRemainsMinusOne() {
        // 자기 참조 에러 케이스 — DECLARING 상태에서 early return, depth 미기록
        List<Stmt> program = new Assembler().assemble("{ var a = a; }");
        checker.check(program);

        Stmt.Block block  = (Stmt.Block) program.get(0);
        Stmt.Var varStmt  = (Stmt.Var) block.statements.get(0);
        Expr.Variable v   = (Expr.Variable) varStmt.initializer;

        assertEquals(-1, v.depth);
    }
}
