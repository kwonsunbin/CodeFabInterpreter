package org.example.codefab.shell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubmissionBufferTest {

    private SubmissionBuffer buf() { return new SubmissionBuffer(); }

    @Test
    void emptyIsComplete() {
        // An empty buffer contributes no open delimiters — considered complete
        var b = buf();
        assertTrue(b.isComplete());
    }

    @Test
    void simplePrintIsComplete() {
        var b = buf();
        b.append("print 1;");
        assertTrue(b.isComplete());
    }

    @Test
    void unclosedParenIsIncomplete() {
        var b = buf();
        b.append("print (1 +");
        assertFalse(b.isComplete());
    }

    @Test
    void closedParenIsComplete() {
        var b = buf();
        b.append("print (1 + 2);");
        assertTrue(b.isComplete());
    }

    @Test
    void unclosedBraceIsIncomplete() {
        var b = buf();
        b.append("if (true) {");
        assertFalse(b.isComplete());
    }

    @Test
    void multiLineBlockIsIncomplete_ThenComplete() {
        var b = buf();
        b.append("if (true) {");
        assertFalse(b.isComplete());
        b.append("  print 1;");
        assertFalse(b.isComplete());
        b.append("}");
        assertTrue(b.isComplete());
    }

    @Test
    void delimitersInsideStringIgnored() {
        var b = buf();
        b.append("print \"(\";"); // the ( inside the string should not count
        assertTrue(b.isComplete());
    }

    @Test
    void strayClosingParenTreatedAsComplete() {
        // A stray ) triggers negative depth → treated as complete so Parser can error
        var b = buf();
        b.append("print 1 + 2);");
        assertTrue(b.isComplete());
    }

    @Test
    void strayClosingBraceTreatedAsComplete() {
        var b = buf();
        b.append("print 1; }");
        assertTrue(b.isComplete());
    }

    @Test
    void clearResets() {
        var b = buf();
        b.append("if (true) {");
        assertFalse(b.isComplete());
        b.clear();
        assertTrue(b.isEmpty());
        b.append("print 1;");
        assertTrue(b.isComplete());
    }

    @Test
    void unterminatedStringIsIncomplete() {
        var b = buf();
        b.append("print \"unterminated");
        assertFalse(b.isComplete()); // inString = true 로 끝남
    }

    @Test
    void textReturnsAccumulatedLines() {
        var b = buf();
        b.append("var a = 1;");
        b.append("print a;");
        assertEquals("var a = 1;\nprint a;\n", b.text());
    }
}
