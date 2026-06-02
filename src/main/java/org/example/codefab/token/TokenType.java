package org.example.codefab.token;

public enum TokenType {
    // Delimiters / grouping
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE, SEMICOLON,

    // Arithmetic operators
    PLUS, MINUS, STAR, SLASH,

    // Assignment / comparison operators
    EQUAL, GREATER, LESS,

    // Logical operators (keyword-style)
    AND, OR,

    // Keywords
    VAR, IF, ELSE, FOR, PRINT, TRUE, FALSE,

    // Identifiers and literals
    IDENTIFIER, NUMBER, STRING,

    // End of input
    EOF
}
