package org.example.codefab.checker;

import org.example.codefab.assembler.Assembler;
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
}
