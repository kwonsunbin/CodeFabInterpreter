package org.example.codefab.assembler;

import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.example.codefab.error.ParseError;
import org.example.codefab.token.Token;
import org.example.codefab.token.TokenType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ParserTest {

    @Mock
    Lexer lexer;

    private List<Stmt> parse(String src) {
        return new Assembler(lexer).assemble(src);
    }


    private Expr.Binary getOuterStatementOfFirstStatement(String src) {
        var stmts = parse(src);
        var print = (Stmt.Print) stmts.getFirst();
        return (Expr.Binary) print.expression;
    }


    // ── Architecture rule: Expr must never contain a Stmt field ──────────────

    @Test void exprNodesNeverContainStmtField() throws Exception {
        for (Class<?> permitted : Expr.class.getPermittedSubclasses()) {
            for (Field field : permitted.getDeclaredFields()) {
                assertFalse(
                    Stmt.class.isAssignableFrom(field.getType()),
                    "Expr subclass " + permitted.getSimpleName() +
                    " has a Stmt-typed field '" + field.getName() + "' — violates the hard rule!"
                );
            }
        }
    }

    // ── Operator precedence ──────────────────────────────────────────────────

    @Test void multiplicationBeforeAddition() {
        // 1 + 2 * 3  →  Binary(1, +, Binary(2, *, 3))
        Mockito.when(lexer.scanTokens()).thenReturn(List.of(
                new Token(TokenType.PRINT,     "print", null, 1),
                new Token(TokenType.NUMBER,    "1",     1.0,  1),
                new Token(TokenType.PLUS,      "+",     null, 1),
                new Token(TokenType.NUMBER,    "2",     2.0,  1),
                new Token(TokenType.STAR,      "*",     null, 1),
                new Token(TokenType.NUMBER,    "3",     3.0,  1),
                new Token(TokenType.SEMICOLON, ";",     null, 1),
                new Token(TokenType.EOF,       "",      null, 1)
        ));
        var outer = getOuterStatementOfFirstStatement("print 1 + 2 * 3;");
        assertEquals(TokenType.PLUS, outer.op.type());
        var inner = (Expr.Binary) outer.right;
        assertEquals(TokenType.STAR, inner.op.type());
    }
    @Test void groupingOverridesPrecedence() {
        // print (1 + 2) * 3;  →  Binary(Grouping(Binary(1,+,2)), *, 3)
        Mockito.when(lexer.scanTokens()).thenReturn(List.of(
                new Token(TokenType.PRINT,       "print", null, 1),
                new Token(TokenType.LEFT_PAREN,  "(",     null, 1),
                new Token(TokenType.NUMBER,      "1",     1.0,  1),
                new Token(TokenType.PLUS,        "+",     null, 1),
                new Token(TokenType.NUMBER,      "2",     2.0,  1),
                new Token(TokenType.RIGHT_PAREN, ")",     null, 1),
                new Token(TokenType.STAR,        "*",     null, 1),
                new Token(TokenType.NUMBER,      "3",     3.0,  1),
                new Token(TokenType.SEMICOLON,   ";",     null, 1),
                new Token(TokenType.EOF,         "",      null, 1)
        ));
        var outer = getOuterStatementOfFirstStatement("print (1 + 2) * 3;");
        assertEquals(TokenType.STAR, outer.op.type());
        assertInstanceOf(Expr.Grouping.class, outer.left);
    }

    @Test void leftAssociativity() {
        // 10 - 4 - 3 → Binary(Binary(10, -, 4), -, 3)

        Mockito.when(lexer.scanTokens()).thenReturn(List.of(
                new Token(TokenType.PRINT,       "print", null, 1),
                new Token(TokenType.NUMBER,  "10",     10.0, 1),
                new Token(TokenType.MINUS,      "-",     null,  1),
                new Token(TokenType.NUMBER,        "4",     4.0, 1),
                new Token(TokenType.MINUS,      "-",     null,  1),
                new Token(TokenType.NUMBER, "3",     null, 1),
                new Token(TokenType.SEMICOLON,   ";",     null, 1),
                new Token(TokenType.EOF,         "",      null, 1)
        ));
        var outer = getOuterStatementOfFirstStatement("print 10 - 4 - 3;");
        assertInstanceOf(Expr.Binary.class, outer.left);
    }
//
//    @Test void comparisonBindsLooserThanTerm() {
//        // 1 + 2 < 3 + 4  →  Comparison(Binary(1,+,2), <, Binary(3,+,4))
//        var stmts = parse("print 1 + 2 < 3 + 4;");
//        var print = (Stmt.Print) stmts.get(0);
//        assertInstanceOf(Expr.Comparison.class, print.expression);
//    }
//
//    @Test void andBindsTighterThanOr() {
//        // a or b and c → Logical(a, or, Logical(b, and, c))
//        var stmts = parse("var a = true; var b = true; var c = false; print a or b and c;");
//        var print = (Stmt.Print) stmts.get(3);
//        var outer = (Expr.Logical) print.expression;
//        assertEquals(TokenType.OR, outer.op.type());
//        assertInstanceOf(Expr.Logical.class, outer.right);
//    }
//
//    // ── Unary minus ───────────────────────────────────────────────────────────
//
//    @Test void unaryMinusProducesUnaryNode() {
//        var stmts = parse("print -5;");
//        var print = (Stmt.Print) stmts.get(0);
//        assertInstanceOf(Expr.Unary.class, print.expression);
//    }
//
//    @Test void doubleUnaryMinus() {
//        var stmts = parse("print - -5;");
//        var print = (Stmt.Print) stmts.get(0);
//        var outer = (Expr.Unary) print.expression;
//        assertInstanceOf(Expr.Unary.class, outer.operand);
//    }
//
//    // ── Assignment ────────────────────────────────────────────────────────────
//
//    @Test void validAssignment() {
//        var stmts = parse("var a = 1; a = 2;");
//        assertInstanceOf(Stmt.Var.class, stmts.get(0));
//        assertInstanceOf(Stmt.Expression.class, stmts.get(1));
//        assertInstanceOf(Expr.Assign.class, ((Stmt.Expression) stmts.get(1)).expression);
//    }
//
//    @Test void invalidAssignmentTargetThrows() {
//        assertThrows(ParseError.class, () -> parse("var a = 1; var b = 2; a + b = 3;"));
//    }
//
//    @Test void rightAssociativeAssignment() {
//        // var a = 1; var b = 1; a = b = 5; — should parse without error
//        assertDoesNotThrow(() -> parse("var a = 1; var b = 1; a = b = 5;"));
//    }
//
//    // ── for statement ─────────────────────────────────────────────────────────
//
//    @Test void forWithVarInit() {
//        var stmts = parse("for (var i = 0; i < 3; i = i + 1) { print i; }");
//        var forStmt = (Stmt.For) stmts.get(0);
//        assertInstanceOf(Stmt.Var.class, forStmt.initializer);
//        assertNotNull(forStmt.condition);
//        assertNotNull(forStmt.increment);
//        assertInstanceOf(Stmt.Block.class, forStmt.body);
//    }
//
//    @Test void forWithEmptyInit() {
//        assertDoesNotThrow(() -> parse("for (; true; ) { print 1; }"));
//    }
//
//    @Test void forBodyMustBeBlock() {
//        assertThrows(ParseError.class, () -> parse("for (var i = 0; i < 1; i = i + 1) print i;"));
//    }
//
//    // ── if / else ─────────────────────────────────────────────────────────────
//
//    @Test void ifWithoutElse() {
//        var stmts = parse("if (true) print 1;");
//        var ifStmt = (Stmt.If) stmts.get(0);
//        assertNull(ifStmt.elseBranch);
//    }
//
//    @Test void ifWithElse() {
//        var stmts = parse("if (false) print 1; else print 2;");
//        var ifStmt = (Stmt.If) stmts.get(0);
//        assertNotNull(ifStmt.elseBranch);
//    }
//
//    @Test void danglingElseBindsToNearestIf() {
//        // if (true) if (false) print 1; else print 2;
//        // The else belongs to the INNER if
//        var stmts = parse("if (true) if (false) print 1; else print 2;");
//        var outer = (Stmt.If) stmts.get(0);
//        assertNull(outer.elseBranch); // outer if has NO else
//        var inner = (Stmt.If) outer.thenBranch;
//        assertNotNull(inner.elseBranch); // inner if has the else
//    }
//
//    // ── Parse error messages ──────────────────────────────────────────────────
//
//    @Test void missingSemicolonMessage() {
//        var ex = assertThrows(ParseError.class, () -> parse("print 1 + 2"));
//        assertTrue(ex.getMessage().contains("';'"), "Expected ';' message, got: " + ex.getMessage());
//    }
//
//    @Test void missingClosingParenMessage() {
//        var ex = assertThrows(ParseError.class, () -> parse("print (1 + 2;"));
//        assertTrue(ex.getMessage().contains("')'"), "Expected ')' message, got: " + ex.getMessage());
//    }
//
//    @Test void expectExpressionMessage() {
//        var ex = assertThrows(ParseError.class, () -> parse("print * 5;"));
//        assertTrue(ex.getMessage().contains("expression"), "Expected 'expression' message, got: " + ex.getMessage());
//    }
}
