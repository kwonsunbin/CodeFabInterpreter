package org.example.codefab.assembler;

import org.example.codefab.ast.Stmt;
import org.example.codefab.error.LexError;
import org.example.codefab.error.ParseError;
import org.example.codefab.token.Token;

import java.util.List;

/**
 * Facade for the Assembler unit.
 * Runs Lexer → Parser and returns the resulting AST.
 */
public class Assembler {

    private final Lexer lexer;

    public Assembler() { this.lexer = null; }

    /** 테스트에서 Lexer를 주입할 때 사용 */
    public Assembler(Lexer lexer) { this.lexer = lexer; }

    /** @throws LexError   on unrecognized characters or unterminated strings
     *  @throws ParseError on grammar violations */
    public List<Stmt> assemble(String source) {
        List<Token> tokens = (lexer != null)
                ? lexer.scanTokens()
                : new Lexer(source).scanTokens();
        return new Parser(tokens).parse();
    }
}
