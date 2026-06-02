package org.example.codefab.assembler;

import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.example.codefab.error.ParseError;
import org.example.codefab.token.Token;
import org.example.codefab.token.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Assembler Step 2: Recursive-descent parser.
 * Converts a token stream into a List<Stmt> AST.
 *
 * Grammar (top-down, highest precedence last):
 *   program    → statement* EOF
 *   statement  → varDecl | ifStmt | forStmt | printStmt | block | exprStmt
 *   expression → assignment → or → and → comparison → term → factor → unary → primary
 */
public class Parser {

    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(statement());
        }
        return statements;
    }

    // ── Statements ────────────────────────────────────────────────────────────

    private Stmt statement() {
        if (match(TokenType.PRINT)) return printStatement();
        // TODO: var / if / for / block / expressionStatement
        throw new UnsupportedOperationException("TODO: implement remaining statements");
    }

    /** var IDENTIFIER ( = expression )? ; */
    private Stmt varDeclaration() {
        // TODO
        throw new UnsupportedOperationException("TODO: implement varDeclaration");
    }

    /** if ( expression ) statement ( else statement )? */
    private Stmt ifStatement() {
        // TODO
        throw new UnsupportedOperationException("TODO: implement ifStatement");
    }

    /** for ( (varDecl | exprStmt | ;)  expression? ;  expression? ) block */
    private Stmt forStatement() {
        // TODO
        throw new UnsupportedOperationException("TODO: implement forStatement");
    }

    /** print expression ; */
    private Stmt printStatement() {
        Expr value = expression();
        consume(TokenType.SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    /** { statement* } — caller must have already consumed LEFT_BRACE */
    private Stmt block() {
        // TODO
        throw new UnsupportedOperationException("TODO: implement block");
    }

    /** expression ; */
    private Stmt expressionStatement() {
        // TODO
        throw new UnsupportedOperationException("TODO: implement expressionStatement");
    }

    // ── Expressions (precedence: low → high) ──────────────────────────────────

    @FunctionalInterface
    interface NodeBuilder {
        Expr build(Expr left, Token op, Expr right);
    }

    private Expr leftAssoc(Supplier<Expr> operand, NodeBuilder builder, TokenType... ops) {
        Expr expr = operand.get();
        while (match(ops)) {
            expr = builder.build(expr, previous(), operand.get());
        }
        return expr;
    }

    private Expr expression() {
        return assignment();
    }

    /** assignment = IDENTIFIER = assignment | logic_or  (right-associative) */
    private Expr assignment() {
        return or();
    }

    private Expr or()         { return leftAssoc(this::and,        Expr.Logical::new,    TokenType.OR); }
    private Expr and()        { return leftAssoc(this::comparison, Expr.Logical::new,    TokenType.AND); }
    private Expr comparison() { return leftAssoc(this::term,       Expr.Comparison::new, TokenType.GREATER, TokenType.LESS); }
    private Expr term()       { return leftAssoc(this::factor,     Expr.Binary::new,     TokenType.PLUS,    TokenType.MINUS); }
    private Expr factor()     { return leftAssoc(this::unary,      Expr.Binary::new,     TokenType.STAR,    TokenType.SLASH); }

    /** unary = - unary | primary */
    private Expr unary() {
        if (match(TokenType.MINUS)) {
            return new Expr.Unary(previous(), unary());
        }
        return primary();
    }

    /** primary = NUMBER | STRING | true | false | IDENTIFIER | ( expression ) */
    private Expr primary() {
        if (match(TokenType.NUMBER)) return new Expr.Literal(previous().value());
        if (match(TokenType.LEFT_PAREN)) {
            Expr expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }
        // TODO: STRING / true / false / IDENTIFIER
        throw new ParseError(peek().line(), "Expect expression.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SafeVarargs
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) { advance(); return true; }
        }
        return false;
    }

    private boolean check(TokenType type) {
        return !isAtEnd() && peek().type() == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw new ParseError(peek().line(), message);
    }

    private boolean isAtEnd()  { return peek().type() == TokenType.EOF; }
    private Token peek()       { return tokens.get(current); }
    private Token previous()   { return tokens.get(current - 1); }
}
