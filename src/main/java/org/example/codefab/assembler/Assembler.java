package org.example.codefab.assembler;

import org.example.codefab.ast.Stmt;
import org.example.codefab.error.LexError;
import org.example.codefab.error.ParseError;

import java.util.List;

/**
 * Facade for the Assembler unit.
 * Runs Lexer → Parser and returns the resulting AST.
 */
public class Assembler {

    /** @throws LexError   on unrecognized characters or unterminated strings
     *  @throws ParseError on grammar violations */
    public List<Stmt> assemble(String source) {
        // TODO: run Lexer then Parser and return the statement list
        throw new UnsupportedOperationException("TODO: implement assemble");
    }
}
