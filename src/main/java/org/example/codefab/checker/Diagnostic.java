package org.example.codefab.checker;

public record Diagnostic(Severity severity, int line, String message) {

    public enum Severity { ERROR, WARNING }

    @Override
    public String toString() {
        return "[check:" + severity.name().toLowerCase() + "] line " + line + ": " + message;
    }
}
