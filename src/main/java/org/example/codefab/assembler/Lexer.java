package org.example.codefab.assembler;

import org.example.codefab.error.LexError;
import org.example.codefab.token.Token;
import org.example.codefab.token.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Assembler Step 1: Converts raw source text into a flat list of Tokens.
 * Handles: numbers, strings, identifiers, keywords, single-char symbols,
 * and // line comments (skipped entirely).
 */
public class Lexer {

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("var",   TokenType.VAR),
            Map.entry("if",    TokenType.IF),
            Map.entry("else",  TokenType.ELSE),
            Map.entry("for",   TokenType.FOR),
            Map.entry("print", TokenType.PRINT),
            Map.entry("true",  TokenType.TRUE),
            Map.entry("false", TokenType.FALSE),
            Map.entry("and",   TokenType.AND),
            Map.entry("or",    TokenType.OR)
    );

    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    private int start   = 0;
    private int current = 0;
    private int line    = 1;

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, line));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(' -> addToken(TokenType.LEFT_PAREN);
            case ')' -> addToken(TokenType.RIGHT_PAREN);
            case '{' -> addToken(TokenType.LEFT_BRACE);
            case '}' -> addToken(TokenType.RIGHT_BRACE);
            case ';' -> addToken(TokenType.SEMICOLON);
            case '+' -> addToken(TokenType.PLUS);
            case '-' -> addToken(TokenType.MINUS);
            case '*' -> addToken(TokenType.STAR);
            case '/' -> addToken(TokenType.SLASH);
            case '>' -> addToken(TokenType.GREATER);
            case '<' -> addToken(TokenType.LESS);
            case '=' -> addToken(TokenType.EQUAL);
            case '"' -> string();
            case ' ', '\r', '\t' -> { /* ignore whitespace */ }
            default -> {
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    throw new UnsupportedOperationException("TODO: more cases");
                }
            }
        }
    }

    private void string() {
        while (!isAtEnd() && peek() != '"') {
            advance();
        }
        if (isAtEnd()) {
            throw new LexError(line, "Unterminated string.");
        }
        advance(); // closing "
        String value = source.substring(start + 1, current - 1);
        addToken(TokenType.STRING, value);
    }

    private void number() {
        while (!isAtEnd() && isDigit(peek())) advance();
        if (!isAtEnd() && peek() == '.' && isDigit(peekNext())) {
            advance(); // consume '.'
            while (!isAtEnd() && isDigit(peek())) advance();
        }
        double value = Double.parseDouble(source.substring(start, current));
        addToken(TokenType.NUMBER, value);
    }

    private void identifier() {
        while (!isAtEnd() && isAlphaNumeric(peek())) advance();
        String text = source.substring(start, current);
        TokenType type = KEYWORDS.getOrDefault(text, TokenType.IDENTIFIER);
        Object value = switch (type) {
            case TRUE  -> Boolean.TRUE;
            case FALSE -> Boolean.FALSE;
            default    -> null;
        };
        addToken(type, value);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private char advance()  { return source.charAt(current++); }
    private char peek()     { return isAtEnd() ? '\0' : source.charAt(current); }
    private char peekNext() { return (current + 1 >= source.length()) ? '\0' : source.charAt(current + 1); }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) return false;
        current++;
        return true;
    }

    private boolean isAtEnd()         { return current >= source.length(); }
    private boolean isDigit(char c)   { return c >= '0' && c <= '9'; }
    private boolean isAlpha(char c)   { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_'; }
    private boolean isAlphaNumeric(char c) { return isAlpha(c) || isDigit(c); }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object value) {
        tokens.add(new Token(type, source.substring(start, current), value, line));
    }
}
