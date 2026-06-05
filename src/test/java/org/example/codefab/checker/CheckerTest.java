package org.example.codefab.checker;

import org.example.codefab.assembler.Assembler;
import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CheckerTest {

    Checker checker;

    @BeforeEach void setUp() {
        checker = new Checker(); // fresh instance per test (fresh scope state)
    }

    private CheckResult check(String src) {
        List<Stmt> program = new Assembler().assemble(src);
        return checker.check(program);
    }

    private List<Stmt> assembleAndCheck(String src) {
        List<Stmt> program = new Assembler().assemble(src);
        checker.check(program);
        return program;
    }

    @Test void validCodeHasNoErrors() {
        assertTrue(check("var a = 1; print a;").ok());
    }

    @Test void duplicateDeclarationInSameScope() {
        var result = check("{ var a = 1; var a = 2; }");
        assertFalse(result.ok());
        assertTrue(result.errors.get(0).message().contains("Already a variable"));
    }

    @Test void shadowingAcrossScopesIsAllowed() {
        assertTrue(check("var x = 1; { var x = 2; print x; } print x;").ok());
    }

    @Test void selfReferenceInInitializer() {
        var result = check("{ var a = a; }");
        assertFalse(result.ok());
        assertTrue(result.errors.get(0).message().contains("Can't read local variable"));
    }

    @Test void noSelfReferenceWithPreviousDecl() {
        // var a = 1; var b = a + 1; — second declaration reads 'a' which is DEFINED
        assertTrue(check("var a = 1; var b = a + 1;").ok());
    }

    @Test void forInitScopeSeparateFromBody() {
        // var i declared in for-init scope; var i inside body is a different scope
        assertTrue(check("for (var i = 0; i < 3; i = i + 1) { var i = 9; print i; }").ok());
    }

    @Test void multipleErrorsCollected() {
        var result = check("{ var a = a; var b = b; }");
        assertEquals(2, result.errors.size());
    }

    @Test void duplicateGlobalDetected() {
        check("var g = 1;"); // first submission — global scope declares g
        // second submission on same checker — g is already defined
        var result = check("var g = 2;");
        assertFalse(result.ok());
    }

    @Test void ifStatementHasNoErrors() {
        assertTrue(check("var a = 1; if (a > 0) { print a; }").ok());
    }

    @Test void ifElseStatementHasNoErrors() {
        assertTrue(check("var a = 1; if (a > 0) { print a; } else { print a; }").ok());
    }

    @Test void logicalAndExpressionHasNoErrors() {
        assertTrue(check("var a = 1; var b = 2; if (a > 0 and b > 0) { print a; }").ok());
    }

    @Test void unaryExpressionHasNoErrors() {
        assertTrue(check("var a = 1; print -a;").ok());
    }

    @Test void groupingExpressionHasNoErrors() {
        assertTrue(check("var a = 1; print (a + 1);").ok());
    }

    @Test void assignmentStatementHasNoErrors() {
        assertTrue(check("var a = 1; a = 2; print a;").ok());
    }

    // ── Rule 3: undeclared variable read ─────────────────────────────────────

    @Test void undeclaredVariableReadIsError() {
        var result = check("print x;");
        assertFalse(result.ok());
        assertTrue(result.errors.get(0).message().contains("Undefined variable"));
    }

    @Test void variableNotAccessibleOutsideBlockIsError() {
        var result = check("{ var x = 1; } print x;");
        assertFalse(result.ok());
        assertTrue(result.errors.get(0).message().contains("Undefined variable"));
    }

    @Test void forwardReferenceIsError() {
        var result = check("var a = b + 1; var b = 2;");
        assertFalse(result.ok());
        assertTrue(result.errors.get(0).message().contains("Undefined variable"));
    }

    // ── Rule 4: undeclared variable write ────────────────────────────────────

    @Test void undeclaredVariableAssignIsError() {
        var result = check("a = 5;");
        assertFalse(result.ok());
        assertTrue(result.errors.get(0).message().contains("Undefined variable"));
    }

    // ── Static binding (depth) ────────────────────────────────────────────────

    @Test void globalVariableReadHasDepthZero() {
        var program = assembleAndCheck("var a = 1; print a;");
        Expr.Variable v = (Expr.Variable) ((Stmt.Print) program.get(1)).expression;
        assertEquals(0, v.depth);
    }

    @Test void variableOneBlockDeepHasDepthOne() {
        var program = assembleAndCheck("var a = 1; { print a; }");
        Stmt.Block block = (Stmt.Block) program.get(1);
        Expr.Variable v  = (Expr.Variable) ((Stmt.Print) block.statements.get(0)).expression;
        assertEquals(1, v.depth);
    }

    @Test void variableThreeBlocksDeepHasDepthThree() {
        var program = assembleAndCheck("var a = 1; { { { print a; } } }");
        Stmt.Block b1 = (Stmt.Block) program.get(1);
        Stmt.Block b2 = (Stmt.Block) b1.statements.get(0);
        Stmt.Block b3 = (Stmt.Block) b2.statements.get(0);
        Expr.Variable v = (Expr.Variable) ((Stmt.Print) b3.statements.get(0)).expression;
        assertEquals(3, v.depth);
    }

    @Test void assignDepthSetCorrectly() {
        var program = assembleAndCheck("var a = 1; { a = 2; }");
        Stmt.Block block  = (Stmt.Block) program.get(1);
        Expr.Assign assign = (Expr.Assign) ((Stmt.Expression) block.statements.get(0)).expression;
        assertEquals(1, assign.depth);
    }

    @Test void forLoopVariableInConditionHasDepthZero() {
        var program = assembleAndCheck("for (var i = 0; i < 3; i = i + 1) { }");
        Stmt.For forStmt      = (Stmt.For) program.get(0);
        Expr.Comparison cond  = (Expr.Comparison) forStmt.condition;
        Expr.Variable iInCond = (Expr.Variable) cond.left;
        assertEquals(0, iInCond.depth);
    }

    @Test void forLoopVariableInBodyHasDepthOne() {
        var program = assembleAndCheck("for (var i = 0; i < 3; i = i + 1) { print i; }");
        Stmt.For forStmt      = (Stmt.For) program.get(0);
        Stmt.Block body       = (Stmt.Block) forStmt.body;
        Expr.Variable iInBody = (Expr.Variable) ((Stmt.Print) body.statements.get(0)).expression;
        assertEquals(1, iInBody.depth);
    }

    @Test void undeclaredVariableDepthRemainsMinusOne() {
        List<Stmt> program = new Assembler().assemble("print x;");
        checker.check(program);
        Expr.Variable v = (Expr.Variable) ((Stmt.Print) program.get(0)).expression;
        assertEquals(-1, v.depth);
    }

    @Test void selfReferenceDepthRemainsMinusOne() {
        List<Stmt> program = new Assembler().assemble("{ var a = a; }");
        checker.check(program);
        Stmt.Block block = (Stmt.Block) program.get(0);
        Stmt.Var varStmt = (Stmt.Var) block.statements.get(0);
        Expr.Variable v  = (Expr.Variable) varStmt.initializer;
        assertEquals(-1, v.depth);
    }

    // ── Constant folding ──────────────────────────────────────────────────────

    @Test void simpleAdditionFolded() {
        var program = assembleAndCheck("print 1 + 2;");
        Expr.Binary b = (Expr.Binary) ((Stmt.Print) program.get(0)).expression;
        assertEquals(3.0, b.foldedValue);
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

    @Test void divisionByZeroNotFolded() {
        var program = assembleAndCheck("print 1 / 0;");
        Expr.Binary b = (Expr.Binary) ((Stmt.Print) program.get(0)).expression;
        assertNull(b.foldedValue);
    }

    @Test void expressionWithVariableNotFolded() {
        var program = assembleAndCheck("var a = 1; print a + 2;");
        Expr.Binary b = (Expr.Binary) ((Stmt.Print) program.get(1)).expression;
        assertNull(b.foldedValue);
    }

    @Test void constantSubExprInLoopBodyFolded() {
        var program = assembleAndCheck(
            "var total = 0; for (var i = 0; i < 3; i = i + 1) { total = total + (1 * 2 * 3); }"
        );
        Stmt.For      forStmt  = (Stmt.For)    program.get(1);
        Stmt.Block    body     = (Stmt.Block)  forStmt.body;
        Expr.Assign   assign   = (Expr.Assign) ((Stmt.Expression) body.statements.get(0)).expression;
        Expr.Binary   addExpr  = (Expr.Binary)   assign.value;
        Expr.Grouping grouping = (Expr.Grouping) addExpr.right;
        assertEquals(6.0, grouping.foldedValue);
        assertNull(addExpr.foldedValue);
    }
}
