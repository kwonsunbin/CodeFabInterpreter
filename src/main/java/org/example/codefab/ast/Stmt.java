package org.example.codefab.ast;

import org.example.codefab.token.Token;
import java.util.List;

/**
 * Base class for all statement nodes.
 * Stmt nodes perform actions (no return value).
 * Stmt may contain Expr and/or Stmt children.
 */
public abstract sealed class Stmt
        permits Stmt.Var, Stmt.If, Stmt.For,
                Stmt.Print, Stmt.Block, Stmt.Expression,
                Stmt.FuncDecl, Stmt.Return {

    public interface Visitor<R> {
        R visitVar(Var stmt);
        R visitIf(If stmt);
        R visitFor(For stmt);
        R visitPrint(Print stmt);
        R visitBlock(Block stmt);
        R visitExpression(Expression stmt);
        R visitFuncDecl(FuncDecl stmt);
        R visitReturn(Return stmt);
    }

    public abstract <R> R accept(Visitor<R> visitor);

    // ── Variable declaration: var name = expr; ────────────────────────────────
    public static final class Var extends Stmt {
        public final Token name;
        public final Expr initializer; // nullable — var x; is allowed

        public Var(Token name, Expr initializer) {
            this.name = name; this.initializer = initializer;
        }

        @Override public <R> R accept(Visitor<R> v) { return v.visitVar(this); }
    }

    // ── Conditional: if (cond) then [else else] ───────────────────────────────
    public static final class If extends Stmt {
        public final Expr condition;
        public final Stmt thenBranch;
        public final Stmt elseBranch; // nullable

        public If(Expr condition, Stmt thenBranch, Stmt elseBranch) {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override public <R> R accept(Visitor<R> v) { return v.visitIf(this); }
    }

    // ── C-style for loop: for (init; cond; incr) { body } ────────────────────
    // initializer is a Stmt (Var or Expression) — legal because For is a Stmt.
    // condition and increment are Expr — Stmt holding Expr is always legal.
    public static final class For extends Stmt {
        public final Stmt initializer;  // nullable: Var | Expression
        public final Expr condition;    // nullable → treated as true
        public final Expr increment;    // nullable Expr (typically Assign)
        public final Stmt body;         // always a Block

        public For(Stmt initializer, Expr condition, Expr increment, Stmt body) {
            this.initializer = initializer;
            this.condition = condition;
            this.increment = increment;
            this.body = body;
        }

        @Override public <R> R accept(Visitor<R> v) { return v.visitFor(this); }
    }

    // ── Print statement ───────────────────────────────────────────────────────
    public static final class Print extends Stmt {
        public final Expr expression;

        public Print(Expr expression) { this.expression = expression; }

        @Override public <R> R accept(Visitor<R> v) { return v.visitPrint(this); }
    }

    // ── Block: { stmts... } ───────────────────────────────────────────────────
    public static final class Block extends Stmt {
        public final List<Stmt> statements;

        public Block(List<Stmt> statements) { this.statements = statements; }

        @Override public <R> R accept(Visitor<R> v) { return v.visitBlock(this); }
    }

    // ── Expression used as a statement (e.g. a = 5;) ─────────────────────────
    public static final class Expression extends Stmt {
        public final Expr expression;

        public Expression(Expr expression) { this.expression = expression; }

        @Override public <R> R accept(Visitor<R> v) { return v.visitExpression(this); }
    }

    // ── Function declaration: Func name(p1, p2) { body } ─────────────────────
    public static final class FuncDecl extends Stmt {
        public final Token name;
        public final List<Token> params;
        public final Stmt.Block body;

        public FuncDecl(Token name, List<Token> params, Stmt.Block body) {
            this.name = name; this.params = params; this.body = body;
        }

        @Override public <R> R accept(Visitor<R> v) { return v.visitFuncDecl(this); }
    }

    // ── Return statement: return [expr]; ─────────────────────────────────────
    public static final class Return extends Stmt {
        public final Token keyword;     // 'return' token — carries line for errors
        public final Expr value;        // nullable — bare return yields nil

        public Return(Token keyword, Expr value) {
            this.keyword = keyword; this.value = value;
        }

        @Override public <R> R accept(Visitor<R> v) { return v.visitReturn(this); }
    }
}
