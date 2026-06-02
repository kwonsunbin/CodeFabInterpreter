package org.example.codefab.shell;

/**
 * Accumulates lines of input and determines when a complete statement
 * has been entered (all parentheses and braces are balanced).
 *
 * String literals are handled: delimiters inside "..." are ignored.
 * A stray closing delimiter (negative depth) is treated as complete
 * so the Parser can report the syntax error rather than hanging the prompt.
 */
public class SubmissionBuffer {

    private final StringBuilder buffer = new StringBuilder();

    public void append(String line) {
        buffer.append(line).append('\n');
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    /** Returns true when parentheses and braces are balanced (not inside a string). */
    public boolean isComplete() {
        boolean inString   = false;
        int     parenDepth = 0;
        int     braceDepth = 0;

        for (char c : buffer.toString().toCharArray()) {
            switch (c) {
                case '"' -> inString = !inString;
                case '(' -> { if (!inString) parenDepth++; }
                case ')' -> { if (!inString) { parenDepth--; if (parenDepth < 0) return true; } }
                case '{' -> { if (!inString) braceDepth++; }
                case '}' -> { if (!inString) { braceDepth--; if (braceDepth < 0) return true; } }
            }
        }

        return !inString && parenDepth <= 0 && braceDepth <= 0;
    }

    public String text() { return buffer.toString(); }
    public void  clear() { buffer.setLength(0); }
}
