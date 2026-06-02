package org.example.codefab.token;

public record Token(TokenType type, String origin, Object value, int line) {
    @Override
    public String toString() {
        return type + " '" + origin + "'" + (value != null ? " (" + value + ")" : "");
    }
}
