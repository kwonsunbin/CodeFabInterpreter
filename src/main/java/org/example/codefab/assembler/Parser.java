package org.example.codefab.assembler;

import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.example.codefab.error.ParseError;
import org.example.codefab.token.Token;
import org.example.codefab.token.TokenType;

import java.util.ArrayList;
import java.util.List;

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
        // TODO: loop until EOF, collecting statements
        throw new UnsupportedOperationException("TODO: implement parse");
    }

    // ── Statements ────────────────────────────────────────────────────────────

    private Stmt statement() {
        // TODO: dispatch to varDeclaration / ifStatement / forStatement /
        //       printStatement / block / expressionStatement
        throw new UnsupportedOperationException("TODO: implement statement");
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
        // TODO
        throw new UnsupportedOperationException("TODO: implement printStatement");
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

    private Expr expression() {
        return assignment();
    }

    /** assignment = IDENTIFIER = assignment | logic_or  (right-associative) */
    private Expr assignment() {
        // TODO
        throw new UnsupportedOperationException("TODO: implement assignment");
    }

    private Expr or() {
        // TODO
        throw new UnsupportedOperationException("TODO: implement or");
    }

    private Expr and() {
        // TODO
        throw new UnsupportedOperationException("TODO: implement and");
    }

    /** comparison = term ( ( > | < ) term )* */
    private Expr comparison() {
        // TODO
        throw new UnsupportedOperationException("TODO: implement comparison");
    }

    /** term = factor ( ( + | - ) factor )* */
    private Expr term() {
        // TODO
        throw new UnsupportedOperationException("TODO: implement term");
    }

    /** factor = unary ( ( * | / ) unary )* */
    private Expr factor() {
        // TODO
        throw new UnsupportedOperationException("TODO: implement factor");
    }

    /** unary = - unary | primary */
    private Expr unary() {
        // TODO
        throw new UnsupportedOperationException("TODO: implement unary");
    }

    /** primary = NUMBER | STRING | true | false | IDENTIFIER | ( expression ) */
    private Expr primary() {
        // TODO
        throw new UnsupportedOperationException("TODO: implement primary");
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
