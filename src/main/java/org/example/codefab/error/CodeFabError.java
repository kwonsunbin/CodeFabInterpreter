package org.example.codefab.error;

public abstract class CodeFabError extends RuntimeException {
    private final int line;

    protected CodeFabError(int line, String message) {
        super(message);
        this.line = line;
    }

    public int line() { return line; }

    public abstract String stage();

    public String userMessage() {
        return "[" + stage() + "] line " + line + ": " + getMessage();
    }
}
