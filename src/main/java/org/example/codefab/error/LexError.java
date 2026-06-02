package org.example.codefab.error;

public class LexError extends CodeFabError {
    public LexError(int line, String message) {
        super(line, message);
    }

    @Override
    public String stage() { return "lex"; }
}
