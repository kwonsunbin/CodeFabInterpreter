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
    void greaterEqualProducesComparisonNode() {
        var cmp = (Expr.Comparison) getStatement("print 3 >= 3;", 0, Stmt.Print.class).expression;
        assertEquals(TokenType.GREATER_EQUAL, cmp.op.type());
    }

    @Test
    void lessEqualProducesComparisonNode() {
        var cmp = (Expr.Comparison) getStatement("print 2 <= 5;", 0, Stmt.Print.class).expression;
        assertEquals(TokenType.LESS_EQUAL, cmp.op.type());
    }

    @Test
    void andBindsTighterThanOr() {
        // a or b and c → Logical(a, or, Logical(b, and, c))
        var outer = (Expr.Logical) getStatement("print 1 or 2 and 3;", 0, Stmt.Print.class).expression;
        assertEquals(TokenType.OR, outer.op.type());
        assertInstanceOf(Expr.Logical.class, outer.right);
    }

    // ── Unary minus / bang ────────────────────────────────────────────────────

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

    @Test
    void unaryBangProducesUnaryNode() {
        var unary = (Expr.Unary) getStatement("print !true;", 0, Stmt.Print.class).expression;
        assertEquals(TokenType.BANG, unary.op.type());
        assertInstanceOf(Expr.Literal.class, unary.operand);
    }

    @Test
    void doubleUnaryBang() {
        var outer = (Expr.Unary) getStatement("print !!false;", 0, Stmt.Print.class).expression;
        assertEquals(TokenType.BANG, outer.op.type());
        assertInstanceOf(Expr.Unary.class, outer.operand);
    }

    @Test
    void bangOnGrouping() {
        var unary = (Expr.Unary) getStatement("print !(1 < 2);", 0, Stmt.Print.class).expression;
        assertEquals(TokenType.BANG, unary.op.type());
        assertInstanceOf(Expr.Grouping.class, unary.operand);
    }

    // ── String literal ───────────────────────────────────────────────────────

    @Test
    void stringLiteralProducesLiteralNode() {
        var print = getStatement("print \"hello\";", 0, Stmt.Print.class);
        var lit = (Expr.Literal) print.expression;
        assertEquals("hello", lit.value);
    }

    @Test
    void stringConcatProducesBinaryNodeWithStringChildren() {
        var print = getStatement("print \"a\" + \"b\";", 0, Stmt.Print.class);
        var binary = (Expr.Binary) print.expression;
        assertEquals(TokenType.PLUS, binary.op.type());
        assertEquals("a", ((Expr.Literal) binary.left).value);
        assertEquals("b", ((Expr.Literal) binary.right).value);
    }

    @Test
    void varDeclWithStringInitializer() {
        var varStmt = getStatement("var s = \"hi\";", 0, Stmt.Var.class);
        var lit = (Expr.Literal) varStmt.initializer;
        assertEquals("hi", lit.value);
    }

    @Test
    void varDeclWithoutInitializerHasNullInitializer() {
        // var x; — EQUAL 미매칭 → initializer = null 분기
        var varStmt = getStatement("var x;", 0, Stmt.Var.class);
        assertEquals("x", varStmt.name.origin());
        assertNull(varStmt.initializer);
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
    void forWithExpressionInit() {
        // 이미 선언된 변수를 초기화식으로 사용 — else initializer = expressionStatement() 경로
        var forStmt = getStatement("var i = 0; for (i = 5; i < 10; i = i + 1) { print i; }", 1, Stmt.For.class);
        assertInstanceOf(Stmt.Expression.class, forStmt.initializer);
        var assign = (Expr.Assign) ((Stmt.Expression) forStmt.initializer).expression;
        assertEquals("i", assign.name.origin());
    }

    @Test
    void forBodyMustBeBlock() {
        assertThrows(ParseError.class, () -> parse("for (var i = 0; i < 1; i = i + 1) print i;"));
    }

    @Test
    void forWithNullCondition() {
        // for (var i = 0; ; ) — condition 생략 시 check(SEMICOLON) true 분기
        var forStmt = getStatement("for (var i = 0; ; ) { print 1; }", 0, Stmt.For.class);
        assertNull(forStmt.condition);
    }

    // ── block statement ──────────────────────────────────────────────────────

    @Test
    void standaloneBlockProducesBlockNode() {
        // { print 1; } — statement()에서 LEFT_BRACE true 분기 직접 진입
        var block = getStatement("{ print 1; }", 0, Stmt.Block.class);
        assertEquals(1, block.statements.size());
        assertInstanceOf(Stmt.Print.class, block.statements.get(0));
    }

    @Test
    void unclosedBlockThrowsParseError() {
        // EOF에 도달 시 block()의 while 조건에서 isAtEnd() true 분기 경유
        assertThrows(ParseError.class, () -> parse("{ print 1;"));
    }

    @Test
    void ifWithBlockBodyEntersLeftBraceBranch() {
        // thenBranch가 블록일 때 statement() → LEFT_BRACE true 분기 경유
        var ifStmt = getStatement("if (true) { print 1; }", 0, Stmt.If.class);
        assertInstanceOf(Stmt.Block.class, ifStmt.thenBranch);
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

    // ── Func declaration ─────────────────────────────────────────────────────

    @Test
    void funcDeclarationNoParams() {
        var func = getStatement("Func greet() { print 1; }", 0, Stmt.Function.class);
        assertEquals("greet", func.name.origin());
        assertEquals(0, func.params.size());
        assertEquals(1, func.body.size());
    }

    @Test
    void funcDeclarationOneParam() {
        var func = getStatement("Func double(x) { return x; }", 0, Stmt.Function.class);
        assertEquals(1, func.params.size());
        assertEquals("x", func.params.get(0).origin());
    }

    @Test
    void funcDeclarationMultipleParams() {
        var func = getStatement("Func add(a, b, c) { return a; }", 0, Stmt.Function.class);
        assertEquals(3, func.params.size());
        assertEquals("a", func.params.get(0).origin());
        assertEquals("b", func.params.get(1).origin());
        assertEquals("c", func.params.get(2).origin());
    }

    @Test
    void funcBodyCanContainMultipleStatements() {
        var func = getStatement("Func f(x) { var y = x; return y; }", 0, Stmt.Function.class);
        assertEquals(2, func.body.size());
        assertInstanceOf(Stmt.Var.class, func.body.get(0));
        assertInstanceOf(Stmt.Return.class, func.body.get(1));
    }

    // ── return statement ──────────────────────────────────────────────────────

    @Test
    void returnWithValue() {
        var func = getStatement("Func f() { return 42; }", 0, Stmt.Function.class);
        var ret = (Stmt.Return) func.body.get(0);
        assertNotNull(ret.value);
        assertEquals(42.0, ((Expr.Literal) ret.value).value);
    }

    @Test
    void returnWithoutValue() {
        var func = getStatement("Func f() { return; }", 0, Stmt.Function.class);
        var ret = (Stmt.Return) func.body.get(0);
        assertNull(ret.value);
    }

    @Test
    void returnWithExpression() {
        var func = getStatement("Func add(a, b) { return a + b; }", 0, Stmt.Function.class);
        var ret = (Stmt.Return) func.body.get(0);
        assertInstanceOf(Expr.Binary.class, ret.value);
    }

    // ── function call expression ──────────────────────────────────────────────

    @Test
    void callExprNoArgs() {
        var call = (Expr.Call) getStatement("foo();", 0, Stmt.Expression.class).expression;
        assertInstanceOf(Expr.Variable.class, call.callee);
        assertEquals(0, call.arguments.size());
    }

    @Test
    void callExprOneArg() {
        var call = (Expr.Call) getStatement("foo(1);", 0, Stmt.Expression.class).expression;
        assertEquals(1, call.arguments.size());
        assertEquals(1.0, ((Expr.Literal) call.arguments.get(0)).value);
    }

    @Test
    void callExprMultipleArgs() {
        var call = (Expr.Call) getStatement("foo(1, 2, 3);", 0, Stmt.Expression.class).expression;
        assertEquals(3, call.arguments.size());
    }

    @Test
    void callExprArgCanBeExpression() {
        var call = (Expr.Call) getStatement("foo(1 + 2);", 0, Stmt.Expression.class).expression;
        assertEquals(1, call.arguments.size());
        assertInstanceOf(Expr.Binary.class, call.arguments.get(0));
    }

    // ── Func parse errors ─────────────────────────────────────────────────────

    @Test
    void missingFuncNameThrows() {
        assertThrows(ParseError.class, () -> parse("Func () { }"));
    }

    @Test
    void missingParenAfterFuncNameThrows() {
        assertThrows(ParseError.class, () -> parse("Func foo { }"));
    }

    @Test
    void missingClosingParenAfterParamsThrows() {
        assertThrows(ParseError.class, () -> parse("Func foo(x { }"));
    }

    @Test
    void missingBraceBeforeFuncBodyThrows() {
        assertThrows(ParseError.class, () -> parse("Func foo() print 1;"));
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
