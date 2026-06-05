package org.example.codefab.token;

public enum TokenType {
    // Delimiters / grouping
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE, LEFT_BRACKET, RIGHT_BRACKET, SEMICOLON, COMMA,

    // Arithmetic operators
    PLUS, MINUS, STAR, SLASH,

    // Assignment / comparison operators
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,

    // Unary operators
    BANG, BANG_EQUAL,

    // Logical operators (keyword-style)
    AND, OR,

    // Keywords
    VAR, IF, ELSE, FOR, PRINT, TRUE, FALSE, FUNC, RETURN,

    // Identifiers and literals
    IDENTIFIER, NUMBER, STRING,

    // End of input
    EOF
}
