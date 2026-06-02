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
        // TODO: scan all tokens and append EOF
        throw new UnsupportedOperationException("TODO: implement scanTokens");
    }

    private void scanToken() {
        // TODO: read next char and dispatch to the correct handler
        throw new UnsupportedOperationException("TODO: implement scanToken");
    }

    private void string() {
        // TODO: consume characters until closing " and add STRING token
        throw new UnsupportedOperationException("TODO: implement string");
    }

    private void number() {
        // TODO: consume digits (and optional decimal part) and add NUMBER token
        throw new UnsupportedOperationException("TODO: implement number");
    }

    private void identifier() {
        // TODO: consume alphanumeric chars, look up keyword map, add token
        throw new UnsupportedOperationException("TODO: implement identifier");
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
