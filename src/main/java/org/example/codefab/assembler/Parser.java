package org.example.codefab.assembler;

import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.example.codefab.error.ParseError;
import org.example.codefab.token.Token;
import org.example.codefab.token.TokenType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Assembler Step 2: Recursive-descent parser.
 * Converts a token stream into a List<Stmt> AST.
 * <p>
 * Grammar (top-down, highest precedence last):
 * program    → statement* EOF
 * expression → assignment → or → and → comparison → term → factor → unary → call → primary
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
        if (match(TokenType.FUNC)) return funcDeclaration();
        if (match(TokenType.RETURN)) return returnStatement();
        if (match(TokenType.VAR)) return varDeclaration();
        if (match(TokenType.IF)) return ifStatement();
        if (match(TokenType.FOR)) return forStatement();
        if (match(TokenType.PRINT)) return printStatement();
        if (match(TokenType.LEFT_BRACE)) return block();
        return expressionStatement();
    }

    /**
     * func IDENTIFIER ( IDENTIFIER (, IDENTIFIER)* )? { block }
     */
    private Stmt funcDeclaration() {
        Token name = consume(TokenType.IDENTIFIER, "Expect function name.");
        consume(TokenType.LEFT_PAREN, "Expect '(' after function name.");
        List<Token> params = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                Token param = consume(TokenType.IDENTIFIER, "Expect parameter name.");
                if (!seen.add(param.origin()))
                    throw new ParseError(param.line(), "Duplicate parameter name '" + param.origin() + "'.");
                params.add(param);
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.");
        consume(TokenType.LEFT_BRACE, "Expect '{' before function body.");
        List<Stmt> body = ((Stmt.Block) block()).statements;
        if (body.stream().noneMatch(s -> s instanceof Stmt.Return))
            throw new ParseError(name.line(), "Function '" + name.origin() + "' must contain a return statement.");
        return new Stmt.Function(name, params, body);
    }

    /**
     * return expression? ;
     */
    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = check(TokenType.SEMICOLON) ? null : expression();
        consume(TokenType.SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    /**
     * var IDENTIFIER ( "[" expression "]" | "=" expression )? ;
     */
    private Stmt varDeclaration() {
        Token name = consume(TokenType.IDENTIFIER, "Expect variable name.");
        if (match(TokenType.LEFT_BRACKET)) {
            Expr size = expression();
            consume(TokenType.RIGHT_BRACKET, "Expect ']' after array size.");
            consume(TokenType.SEMICOLON, "Expect ';' after array declaration.");
            return new Stmt.ArrayDecl(name, size);
        }
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

    private Expr leftAssoc(Supplier<Expr> operand, TokenType... ops) {
        Expr expr = operand.get();
        while (match(ops))
            expr = Expr.builder().left(expr).op(previous()).right(operand.get()).build();
        return expr;
    }

    private Expr expression() {
        return assignment();
    }

    /**
     * assignment → IDENTIFIER = assignment | IDENTIFIER "[" expr "]" = assignment | or
     */
    private Expr assignment() {
        Expr expr = or();
        if (match(TokenType.EQUAL)) {
            Expr value = assignment();
            if (expr instanceof Expr.Variable v) return new Expr.Assign(v.name, value);
            if (expr instanceof Expr.ArrayGet ag) return new Expr.ArraySet(ag.name, ag.index, value);
            throw new ParseError(peek().line(), "Invalid assignment target.");
        }
        return expr;
    }

    private Expr or() {
        return leftAssoc(this::and, TokenType.OR);
    }

    private Expr and() {
        return leftAssoc(this::comparison, TokenType.AND);
    }

    private Expr comparison() {
        return leftAssoc(this::term,
                TokenType.GREATER, TokenType.GREATER_EQUAL,
                TokenType.LESS, TokenType.LESS_EQUAL,
                TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL);
    }

    private Expr term() {
        return leftAssoc(this::factor, TokenType.PLUS, TokenType.MINUS);
    }

    private Expr factor() {
        return leftAssoc(this::unary, TokenType.STAR, TokenType.SLASH);
    }

    private Expr unary() {
        if (match(TokenType.MINUS, TokenType.BANG))
            return Expr.builder().op(previous()).operand(unary()).build();
        return call();
    }

    /**
     * call → primary ( "(" args ")" )*
     */
    private Expr call() {
        Expr expr = primary();
        while (match(TokenType.LEFT_PAREN)) {
            expr = finishCall(expr);
        }
        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                arguments.add(expression());
            } while (match(TokenType.COMMA));
        }
        Token paren = consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.");
        return new Expr.Call(callee, paren, arguments);
    }

    /**
     * primary = NUMBER | STRING | true | false | IDENTIFIER ( "[" expression "]" )? | ( expression )
     */
    private Expr primary() {
        if (match(TokenType.NUMBER, TokenType.STRING, TokenType.TRUE, TokenType.FALSE))
            return Expr.builder().literalValue(previous().value()).build();
        if (match(TokenType.IDENTIFIER)) {
            Token name = previous();
            if (match(TokenType.LEFT_BRACKET)) {
                Expr index = expression();
                consume(TokenType.RIGHT_BRACKET, "Expect ']' after index.");
                return new Expr.ArrayGet(name, index);
            }
            return Expr.builder().name(name).build();
        }
        if (match(TokenType.LEFT_PAREN)) {
            Expr expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.");
            return Expr.builder().expression(expr).build();
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

}
