package org.example.codefab.log;

import org.example.codefab.error.LexError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class LoggerTest {

    private ByteArrayOutputStream errBytes;
    private Logger verboseLogger;
    private Logger silentLogger;

    @BeforeEach
    void setUp() {
        errBytes = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errBytes);
        verboseLogger = new Logger(true,  err);
        silentLogger  = new Logger(false, err);
    }

    private String captured() { return errBytes.toString(); }

    // ── verbose=true ──────────────────────────────────────────────────────────

    @Test
    void executionStartPrintsWhenVerbose() {
        verboseLogger.executionStart();
        assertTrue(captured().contains("[exec] start"));
    }

    @Test
    void executionCompletePrintsWhenVerbose() {
        verboseLogger.executionComplete();
        assertTrue(captured().contains("[exec] done"));
    }

    @Test
    void diagnosticPrintsWhenVerbose() {
        verboseLogger.diagnostic("hello");
        assertTrue(captured().contains("hello"));
    }

    // ── verbose=false — 침묵 검증 ─────────────────────────────────────────────

    @Test
    void executionStartSilentWhenNotVerbose() {
        silentLogger.executionStart();
        assertTrue(captured().isEmpty());
    }

    @Test
    void executionCompleteSilentWhenNotVerbose() {
        silentLogger.executionComplete();
        assertTrue(captured().isEmpty());
    }

    @Test
    void diagnosticSilentWhenNotVerbose() {
        silentLogger.diagnostic("should not appear");
        assertTrue(captured().isEmpty());
    }

    // ── error()는 verbose와 무관하게 항상 출력 ────────────────────────────────

    @Test
    void errorAlwaysPrintsWhenVerbose() {
        verboseLogger.error(new LexError(3, "bad char"));
        assertTrue(captured().contains("[lex] line 3: bad char"));
    }

    @Test
    void errorAlwaysPrintsWhenNotVerbose() {
        silentLogger.error(new LexError(5, "unterminated"));
        assertTrue(captured().contains("[lex] line 5: unterminated"));
    }
}
