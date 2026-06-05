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
 * <p>
 * Grammar (top-down, highest precedence last):
 * program    → statement* EOF
 * expression → assignment → or → and → comparison → term → factor → unary → primary
 */
public class Parser {

    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) statements.add(statement());
        return statements;
    }

    // ── Statements ────────────────────────────────────────────────────────────

    private Stmt statement() {
        if (match(TokenType.VAR)) return varDeclaration();
        if (match(TokenType.IF)) return ifStatement();
        if (match(TokenType.FOR)) return forStatement();
        if (match(TokenType.PRINT)) return printStatement();
        if (match(TokenType.LEFT_BRACE)) return block();
        return expressionStatement();
    }

    /**
     * var IDENTIFIER ( = expression )? ;
     */
    private Stmt varDeclaration() {
        Token name = consume(TokenType.IDENTIFIER, "Expect variable name.");
        Expr initializer = match(TokenType.EQUAL) ? expression() : null;
        consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    /**
     * if ( expression ) statement ( else statement )?
     */
    private Stmt ifStatement() {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.");
        Stmt thenBranch = statement();
        Stmt elseBranch = match(TokenType.ELSE) ? statement() : null;
        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    /**
     * for ( (varDecl | exprStmt | ;) expression? ; expression? ) block
     */
    private Stmt forStatement() {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'for'.");
        Stmt initializer;
        if (match(TokenType.SEMICOLON)) initializer = null;
        else if (match(TokenType.VAR)) initializer = varDeclaration();
        else initializer = expressionStatement();
        Expr condition = check(TokenType.SEMICOLON) ? null : expression();
        consume(TokenType.SEMICOLON, "Expect ';' after for condition.");
        Expr increment = check(TokenType.RIGHT_PAREN) ? null : expression();
        consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.");
        consume(TokenType.LEFT_BRACE, "Expect '{' before for body.");
        return new Stmt.For(initializer, condition, increment, block());
    }

    /**
     * print expression ;
     */
    private Stmt printStatement() {
        Expr value = expression();
        consume(TokenType.SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    /**
     * { statement* } — caller must have already consumed LEFT_BRACE
     */
    private Stmt block() {
        List<Stmt> stmts = new ArrayList<>();
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) stmts.add(statement());
        consume(TokenType.RIGHT_BRACE, "Expect '}' after block.");
        return new Stmt.Block(stmts);
    }

    /**
     * expression ;
     */
    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(TokenType.SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    // ── Expressions (precedence: low → high) ──────────────────────────────────

    private Expr leftAssoc(Supplier<Expr> operand, NodeBuilder builder, TokenType... ops) {
        Expr expr = operand.get();
        while (match(ops)) expr = builder.build(expr, previous(), operand.get());
        return expr;
    }

    private Expr expression() {
        return assignment();
    }

    /**
     * assignment → IDENTIFIER = assignment | or  (right-associative)
     */
    private Expr assignment() {
        Expr expr = or();
        if (match(TokenType.EQUAL)) {
            Expr value = assignment();
            if (expr instanceof Expr.Variable v) return new Expr.Assign(v.name, value);
            throw new ParseError(peek().line(), "Invalid assignment target.");
        }
        return expr;
    }

    private Expr or() {
        return leftAssoc(this::and, Expr.Logical::new, TokenType.OR);
    }

    private Expr and() {
        return leftAssoc(this::comparison, Expr.Logical::new, TokenType.AND);
    }

    private Expr comparison() {
        return leftAssoc(this::term, Expr.Comparison::new,
                TokenType.GREATER, TokenType.GREATER_EQUAL,
                TokenType.LESS, TokenType.LESS_EQUAL,
                TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL);
    }

    private Expr term() {
        return leftAssoc(this::factor, Expr.Binary::new, TokenType.PLUS, TokenType.MINUS);
    }

    private Expr factor() {
        return leftAssoc(this::unary, Expr.Binary::new, TokenType.STAR, TokenType.SLASH);
    }

    private Expr unary() {
        if (match(TokenType.MINUS, TokenType.BANG)) return new Expr.Unary(previous(), unary());
        return primary();
    }

    /**
     * primary = NUMBER | STRING | true | false | IDENTIFIER | ( expression )
     */
    private Expr primary() {
        if (match(TokenType.NUMBER, TokenType.STRING, TokenType.TRUE, TokenType.FALSE))
            return new Expr.Literal(previous().value(), previous().line());
        if (match(TokenType.IDENTIFIER)) return new Expr.Variable(previous());
        if (match(TokenType.LEFT_PAREN)) {
            Expr expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }
        throw new ParseError(peek().line(), "Expect expression.");
    }

    @SafeVarargs
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private boolean isAtEnd() {
        return peek().type() == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    @FunctionalInterface
    private interface NodeBuilder {
        Expr build(Expr left, Token op, Expr right);
    }
}
