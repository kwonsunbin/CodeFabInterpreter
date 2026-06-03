package org.example.codefab.checker;

import org.example.codefab.assembler.Assembler;
import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.example.codefab.token.Token;
import org.example.codefab.token.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckerTest {

    Checker checker;

    @Mock
    Assembler assembler;

    @BeforeEach void setUp() {
        checker = new Checker(); // fresh instance per test (fresh scope state)
    }

    private CheckResult check(String src) {
        List<Stmt> program = assembler.assemble(src);
        return checker.check(program);
    }

    @Test void validCodeHasNoErrors() {
        assertTrue(check("var a = 1; print a;").ok());
    }

    @Test void duplicateDeclarationInSameScope() {
        Token nameA = new Token(TokenType.IDENTIFIER, "a", null, 1);
        when(assembler.assemble(anyString())).thenReturn(List.of(
                new Stmt.Block(List.of(
                        new Stmt.Var(nameA, new Expr.Literal(1.0)),
                        new Stmt.Var(nameA, new Expr.Literal(2.0))
                ))
        ));

        var result = check("{ var a = 1; var a = 2; }");
        assertFalse(result.ok());
        assertTrue(result.errors.get(0).message().contains("Already a variable"));
    }

    @Test void shadowingAcrossScopesIsAllowed() {
        assertTrue(check("var x = 1; { var x = 2; print x; } print x;").ok());
    }

    @Test void selfReferenceInInitializer() {
        Token nameA = new Token(TokenType.IDENTIFIER, "a", null, 1);
        when(assembler.assemble(anyString())).thenReturn(List.of(
                new Stmt.Block(List.of(
                        new Stmt.Var(nameA, new Expr.Variable(nameA))  // var a = a
                ))
        ));

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
        Token nameA = new Token(TokenType.IDENTIFIER, "a", null, 1);
        Token nameB = new Token(TokenType.IDENTIFIER, "b", null, 1);
        when(assembler.assemble(anyString())).thenReturn(List.of(
                new Stmt.Block(List.of(
                        new Stmt.Var(nameA, new Expr.Variable(nameA)),
                        new Stmt.Var(nameB, new Expr.Variable(nameB))
                ))
        ));

        var result = check("{ var a = a; var b = b; }");
        assertEquals(2, result.errors.size());
    }

    @Test void duplicateGlobalDetected() {
        Token nameG = new Token(TokenType.IDENTIFIER, "g", null, 1);
        when(assembler.assemble(anyString()))
                .thenReturn(List.of(new Stmt.Var(nameG, new Expr.Literal(1.0))))
                .thenReturn(List.of(new Stmt.Var(nameG, new Expr.Literal(2.0))));

        check("var g = 1;"); // first submission — global scope declares g
        // second submission on same checker — g is already defined
        var result = check("var g = 2;");
        assertFalse(result.ok());
    }
}
