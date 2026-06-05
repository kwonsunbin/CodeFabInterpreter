package org.example.codefab.ast;

import org.example.codefab.token.Token;
import java.util.List;

/**
 * Base class for all expression nodes.
 * RULE: No Expr subclass may contain a Stmt-typed field.
 * Expr nodes evaluate to a value; Stmt nodes perform actions.
 */
public abstract sealed class Expr
        permits Expr.Binary, Expr.Logical, Expr.Comparison,
                Expr.Unary, Expr.Grouping, Expr.Literal,
                Expr.Variable, Expr.Assign, Expr.Call,
                Expr.ArrayGet, Expr.ArraySet {

    public Object foldedValue = null; // set by CheckerFold; null = 폴딩 안 됨

    public interface Visitor<R> {
        R visitBinary(Binary expr);
        R visitLogical(Logical expr);
        R visitComparison(Comparison expr);
        R visitUnary(Unary expr);
        R visitGrouping(Grouping expr);
        R visitLiteral(Literal expr);
        R visitVariable(Variable expr);
        R visitAssign(Assign expr);
        // TODO: Checker/Executor에서 visitArrayGet, visitArraySet 구현 필요
        default R visitArrayGet(ArrayGet expr) { throw new UnsupportedOperationException("visitArrayGet not implemented"); }
        default R visitArraySet(ArraySet expr) { throw new UnsupportedOperationException("visitArraySet not implemented"); }
        // TODO: Checker/Executor에서 visitCall 구현 필요
        default R visitCall(Call expr) { throw new UnsupportedOperationException("visitCall not implemented"); }
    }

    public abstract <R> R accept(Visitor<R> visitor);

    // ── Arithmetic: + - * / ──────────────────────────────────────────────────
    public static final class Binary extends Expr {
        public final Expr left;
        public final Token op;
        public final Expr right;

        public Binary(Expr left, Token op, Expr right) {
            this.left = left; this.op = op; this.right = right;
        }

        @Override public <R> R accept(Visitor<R> v) { return v.visitBinary(this); }
    }

    // ── Logical short-circuit: and / or ──────────────────────────────────────
    public static final class Logical extends Expr {
        public final Expr left;
        public final Token op;
        public final Expr right;

        public Logical(Expr left, Token op, Expr right) {
            this.left = left; this.op = op; this.right = right;
        }

        @Override public <R> R accept(Visitor<R> v) { return v.visitLogical(this); }
    }

    // ── Comparison: > < ──────────────────────────────────────────────────────
    public static final class Comparison extends Expr {
        public final Expr left;
        public final Token op;
        public final Expr right;

        public Comparison(Expr left, Token op, Expr right) {
            this.left = left; this.op = op; this.right = right;
        }

        @Override public <R> R accept(Visitor<R> v) { return v.visitComparison(this); }
    }

    // ── Unary negation: -x ───────────────────────────────────────────────────
    public static final class Unary extends Expr {
        public final Token op;
        public final Expr operand;

        public Unary(Token op, Expr operand) {
            this.op = op; this.operand = operand;
        }

        @Override public <R> R accept(Visitor<R> v) { return v.visitUnary(this); }
    }

    // ── Parenthesized expression ──────────────────────────────────────────────
    public static final class Grouping extends Expr {
        public final Expr expression;

        public Grouping(Expr expression) { this.expression = expression; }

        @Override public <R> R accept(Visitor<R> v) { return v.visitGrouping(this); }
    }

    // ── Literal value: number, string, boolean ────────────────────────────────
    public static final class Literal extends Expr {
        public final Object value; // Double | String | Boolean | null
        public final int line;     // 소스 줄번호 (디버그 표시용). 미상이면 -1

        public Literal(Object value) { this(value, -1); }

        public Literal(Object value, int line) {
            this.value = value; this.line = line;
        }

        @Override public <R> R accept(Visitor<R> v) { return v.visitLiteral(this); }
    }

    // ── Variable read ─────────────────────────────────────────────────────────
    public static final class Variable extends Expr {
        public final Token name;
        public int depth = -1; // set by CheckerDepth; 0 = current scope, N = N hops up

        public Variable(Token name) { this.name = name; }

        @Override public <R> R accept(Visitor<R> v) { return v.visitVariable(this); }
    }

    // ── Assignment expression: a = expr (returns value) ───────────────────────
    public static final class Assign extends Expr {
        public final Token name;
        public final Expr value;
        public int depth = -1; // set by CheckerDepth; 0 = current scope, N = N hops up

        public Assign(Token name, Expr value) {
            this.name = name; this.value = value;
        }

        @Override public <R> R accept(Visitor<R> v) { return v.visitAssign(this); }
    }

    // ── Array index read: name[index] ─────────────────────────────────────────
    public static final class ArrayGet extends Expr {
        public final Token name;
        public final Expr index;

        public ArrayGet(Token name, Expr index) {
            this.name = name;
            this.index = index;
        }

        @Override public <R> R accept(Visitor<R> v) { return v.visitArrayGet(this); }
    }

    // ── Array index write: name[index] = value ────────────────────────────────
    public static final class ArraySet extends Expr {
        public final Token name;
        public final Expr index;
        public final Expr value;

        public ArraySet(Token name, Expr index, Expr value) {
            this.name = name;
            this.index = index;
            this.value = value;
        }

        @Override public <R> R accept(Visitor<R> v) { return v.visitArraySet(this); }
    }

    // ── Function call: callee(arg1, arg2, ...) ────────────────────────────────
    public static final class Call extends Expr {
        public final Expr callee;
        public final Token paren; // closing ')' — used for error reporting
        public final List<Expr> arguments;

        public Call(Expr callee, Token paren, List<Expr> arguments) {
            this.callee = callee;
            this.paren = paren;
            this.arguments = arguments;
        }

        @Override public <R> R accept(Visitor<R> v) { return v.visitCall(this); }
    }
}
