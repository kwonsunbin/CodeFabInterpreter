package org.example.codefab.error;

import org.example.codefab.token.Token;

public class RuntimeError extends CodeFabError {
    public RuntimeError(Token token, String message) {
        super(token.line(), message);
    }

    public RuntimeError(int line, String message) {
        super(line, message);
    }

    @Override
    public String stage() { return "runtime"; }
}
