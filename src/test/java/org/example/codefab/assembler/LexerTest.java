package org.example.codefab.assembler;

import org.example.codefab.error.LexError;
import org.example.codefab.token.Token;
import org.example.codefab.token.TokenType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LexerTest {

    private List<Token> lex(String src) {
        return new Lexer(src).scanTokens();
    }

    @Test void singleCharTokens() {
        var tokens = lex("(){};+-*/=><!");
        assertEquals(TokenType.LEFT_PAREN,  tokens.get(0).type());
        assertEquals(TokenType.RIGHT_PAREN, tokens.get(1).type());
        assertEquals(TokenType.LEFT_BRACE,  tokens.get(2).type());
        assertEquals(TokenType.RIGHT_BRACE, tokens.get(3).type());
        assertEquals(TokenType.SEMICOLON,   tokens.get(4).type());
        assertEquals(TokenType.PLUS,        tokens.get(5).type());
        assertEquals(TokenType.MINUS,       tokens.get(6).type());
        assertEquals(TokenType.STAR,        tokens.get(7).type());
        assertEquals(TokenType.SLASH,       tokens.get(8).type());
        assertEquals(TokenType.EQUAL,       tokens.get(9).type());
        assertEquals(TokenType.GREATER,     tokens.get(10).type());
        assertEquals(TokenType.LESS,        tokens.get(11).type());
        assertEquals(TokenType.BANG,        tokens.get(12).type());
        assertEquals(TokenType.EOF,         tokens.get(13).type());
    }

    @Test void compoundOperators() {
        var tokens = lex(">= <= == !=");
        assertEquals(TokenType.GREATER_EQUAL, tokens.get(0).type());
        assertEquals(">=",                    tokens.get(0).origin());
        assertEquals(TokenType.LESS_EQUAL,    tokens.get(1).type());
        assertEquals("<=",                    tokens.get(1).origin());
        assertEquals(TokenType.EQUAL_EQUAL,   tokens.get(2).type());
        assertEquals("==",                    tokens.get(2).origin());
        assertEquals(TokenType.BANG_EQUAL,    tokens.get(3).type());
        assertEquals("!=",                    tokens.get(3).origin());
        assertEquals(TokenType.EOF,           tokens.get(4).type());
    }

    @Test void keywords() {
        var tokens = lex("var if else for print true false and or");
        assertEquals(TokenType.VAR,   tokens.get(0).type());
        assertEquals(TokenType.IF,    tokens.get(1).type());
        assertEquals(TokenType.ELSE,  tokens.get(2).type());
        assertEquals(TokenType.FOR,   tokens.get(3).type());
        assertEquals(TokenType.PRINT, tokens.get(4).type());
        assertEquals(TokenType.TRUE,  tokens.get(5).type());
        assertEquals(TokenType.FALSE, tokens.get(6).type());
        assertEquals(TokenType.AND,   tokens.get(7).type());
        assertEquals(TokenType.OR,    tokens.get(8).type());
    }

    @Test void keywordVsIdentifier() {
        var tokens = lex("forx truthy varname");
        // All should be identifiers, not keywords
        for (int i = 0; i < 3; i++) {
            assertEquals(TokenType.IDENTIFIER, tokens.get(i).type());
        }
    }

    @Test void numberLiteral() {
        var tokens = lex("42 3.14");
        assertEquals(TokenType.NUMBER, tokens.get(0).type());
        assertEquals(42.0,             (Double) tokens.get(0).value(), 1e-9);
        assertEquals(TokenType.NUMBER, tokens.get(1).type());
        assertEquals(3.14,             (Double) tokens.get(1).value(), 1e-9);
    }

    @Test void stringLiteral() {
        var tokens = lex("\"hello\"");
        assertEquals(TokenType.STRING, tokens.get(0).type());
        assertEquals("hello",          tokens.get(0).value());
    }

    @Test void booleanValues() {
        var tokens = lex("true false");
        assertEquals(Boolean.TRUE,  tokens.get(0).value());
        assertEquals(Boolean.FALSE, tokens.get(1).value());
    }

    @Test void lineCommentSkipped() {
        var tokens = lex("var x = 1; // this is a comment\nprint x;");
        // Should see: VAR IDENTIFIER EQUAL NUMBER SEMICOLON PRINT IDENTIFIER SEMICOLON EOF
        assertEquals(TokenType.VAR,        tokens.get(0).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(1).type());
        // Comment entirely skipped — no tokens from it
        assertEquals(TokenType.PRINT, tokens.get(5).type());
        assertEquals(TokenType.EOF,   tokens.get(8).type());
    }

    @Test void trailingLineComment() {
        var tokens = lex("print 5; // expect: 5");
        // Should be: PRINT NUMBER SEMICOLON EOF
        assertEquals(4, tokens.size());
        assertEquals(TokenType.PRINT,     tokens.get(0).type());
        assertEquals(TokenType.NUMBER,    tokens.get(1).type());
        assertEquals(TokenType.SEMICOLON, tokens.get(2).type());
        assertEquals(TokenType.EOF,       tokens.get(3).type());
    }

    @Test void lineNumbers() {
        var tokens = lex("var a;\nvar b;");
        assertEquals(1, tokens.get(0).line()); // var
        assertEquals(1, tokens.get(2).line()); // ;
        assertEquals(2, tokens.get(3).line()); // var (second line)
    }

    @Test void unterminatedStringThrows() {
        assertThrows(LexError.class, () -> lex("\"unterminated"));
    }

    @Test void unexpectedCharacterThrows() {
        assertThrows(LexError.class, () -> lex("@"));
    }
}
