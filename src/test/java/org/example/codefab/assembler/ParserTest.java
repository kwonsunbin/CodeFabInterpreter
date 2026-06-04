package org.example.codefab.assembler;

import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.example.codefab.error.ParseError;
import org.example.codefab.token.TokenType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    Lexer lexer;


    private List<Stmt> parse(String src) {
        return new Assembler(lexer).assemble(src);
    }


    private <T extends Stmt> T getStatement(String src, int i, Class<T> type) {
        return type.cast(parse(src).get(i));
    }


    // ── Architecture rule: Expr must never contain a Stmt field ──────────────

    @Test
    void exprNodesNeverContainStmtField() throws Exception {
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

    @Test
    void multiplicationBeforeAddition() {
        // 1 + 2 * 3  →  Binary(1, +, Binary(2, *, 3))
        var outer = (Expr.Binary) getStatement("print 1 + 2 * 3;", 0, Stmt.Print.class).expression;
        assertEquals(TokenType.PLUS, outer.op.type());
        var inner = (Expr.Binary) outer.right;
        assertEquals(TokenType.STAR, inner.op.type());
    }

    @Test
    void groupingOverridesPrecedence() {
        // print (1 + 2) * 3;  →  Binary(Grouping(Binary(1,+,2)), *, 3)
        var outer = (Expr.Binary) getStatement("print (1 + 2) * 3;", 0, Stmt.Print.class).expression;
        assertEquals(TokenType.STAR, outer.op.type());
        assertInstanceOf(Expr.Grouping.class, outer.left);
    }

    @Test
    void leftAssociativity() {
        // 10 - 4 - 3 → Binary(Binary(10, -, 4), -, 3)
        var outer = (Expr.Binary) getStatement("print 10 - 4 - 3;", 0, Stmt.Print.class).expression;
        assertInstanceOf(Expr.Binary.class, outer.left);
    }

    @Test
    void comparisonBindsLooserThanTerm() {
        // 1 + 2 < 3 + 4  →  Comparison(Binary(1,+,2), <, Binary(3,+,4))
        var outer = getStatement("print 1 + 2 < 3 + 4;", 0, Stmt.Print.class).expression;
        assertInstanceOf(Expr.Comparison.class, outer);
    }

    @Test
    void andBindsTighterThanOr() {
        // a or b and c → Logical(a, or, Logical(b, and, c))
        var outer = (Expr.Logical) getStatement("print 1 or 2 and 3;", 0, Stmt.Print.class).expression;
        assertEquals(TokenType.OR, outer.op.type());
        assertInstanceOf(Expr.Logical.class, outer.right);
    }

    // ── Unary minus ───────────────────────────────────────────────────────────

    @Test
    void unaryMinusProducesUnaryNode() {
        var outer = (Expr.Unary) getStatement("print -5;", 0, Stmt.Print.class).expression;
        assertInstanceOf(Expr.Unary.class, outer);
    }

    @Test
    void doubleUnaryMinus() {
        var outer = (Expr.Unary) getStatement("print --5;", 0, Stmt.Print.class).expression;
        assertInstanceOf(Expr.Unary.class, outer.operand);
    }

    // ── Assignment ────────────────────────────────────────────────────────────

    @Test
    void validAssignment() {
        var stmts = parse("var a = 1; a = 2;");
        assertInstanceOf(Stmt.Var.class, stmts.get(0));
        var exprStmt = (Stmt.Expression) stmts.get(1);
        assertInstanceOf(Expr.Assign.class, exprStmt.expression);
    }

    @Test
    void invalidAssignmentTargetThrows() {
        assertThrows(ParseError.class, () -> parse("var a = 1; var b = 2; a + b = 3;"));
    }

    @Test
    void rightAssociativeAssignment() {
        // var a = 1; var b = 1; a = b = 5; — should parse without error
        assertDoesNotThrow(() -> parse("var a = 1; var b = 1; a = b = 5;"));
    }

    // ── for statement ─────────────────────────────────────────────────────────

    @Test
    void forWithVarInit() {
        var forStmt = getStatement("for (var i = 0; i < 3; i = i + 1) { print i; }", 0, Stmt.For.class);
        assertInstanceOf(Stmt.Var.class, forStmt.initializer);
        assertNotNull(forStmt.condition);
        assertNotNull(forStmt.increment);
        assertInstanceOf(Stmt.Block.class, forStmt.body);
    }

    @Test
    void forWithEmptyInit() {
        assertDoesNotThrow(() -> parse("for (; true; ) { print 1; }"));
    }

    @Test
    void forBodyMustBeBlock() {
        assertThrows(ParseError.class, () -> parse("for (var i = 0; i < 1; i = i + 1) print i;"));
    }

    // ── if / else ─────────────────────────────────────────────────────────────

    @Test
    void ifWithoutElse() {
        var ifStmt = getStatement("if (true) print 1;", 0, Stmt.If.class);
        assertNull(ifStmt.elseBranch);
    }

    @Test
    void ifWithElse() {
        var ifStmt = getStatement("if (false) print 1; else print 2;", 0, Stmt.If.class);
        assertNotNull(ifStmt.elseBranch);
    }

    @Test
    void danglingElseBindsToNearestIf() {
        // if (true) if (false) print 1; else print 2;
        // The else belongs to the INNER if
        var outer = getStatement("if (true) if (false) print 1; else print 2;", 0, Stmt.If.class);
        assertNull(outer.elseBranch); // outer if has NO else
        var inner = (Stmt.If) outer.thenBranch;
        assertNotNull(inner.elseBranch); // inner if has the else
    }

    // ── Parse error messages ──────────────────────────────────────────────────

    @Test
    void missingSemicolonMessage() {
        var ex = assertThrows(ParseError.class, () -> parse("print 1 + 2"));
        assertTrue(ex.getMessage().contains("';'"), "Expected ';' message, got: " + ex.getMessage());
    }

    @Test
    void missingClosingParenMessage() {
        var ex = assertThrows(ParseError.class, () -> parse("print (1 + 2;"));
        assertTrue(ex.getMessage().contains("')'"), "Expected ')' message, got: " + ex.getMessage());
    }

    @Test
    void expectExpressionMessage() {
        var ex = assertThrows(ParseError.class, () -> parse("print * 5;"));
        assertTrue(ex.getMessage().contains("expression"), "Expected 'expression' message, got: " + ex.getMessage());
    }
}
