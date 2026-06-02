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
        // TODO: walk buffer char by char tracking parenDepth, braceDepth, inString
        //       return true when balanced (or negative depth = stray delimiter)
        throw new UnsupportedOperationException("TODO: implement isComplete");
    }

    public String text()  { return buffer.toString(); }
    public void   clear() { buffer.setLength(0); }
}
