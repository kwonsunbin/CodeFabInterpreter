package org.example.codefab.executor;

import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.example.codefab.error.RuntimeError;
import org.example.codefab.log.Logger;
import org.example.codefab.token.Token;
import org.example.codefab.token.TokenType;

import java.util.List;

/**
 * Executor Unit: runtime evaluation via recursive DFS (Visitor pattern).
 *
 * Expr visitors return Object (Double | String | Boolean | null).
 * Stmt visitors return Void — they modify state or drive output.
 *
 * A single global Environment persists across REPL submissions so that
 * variables declared in one line are available in subsequent lines.
 */
public class Executor implements Stmt.Visitor<Void>, Expr.Visitor<Object> {

    private final Logger log;
    private final Environment globalEnv = new Environment();
    private Environment environment = globalEnv;

    public Executor(Logger log) { this.log = log; }

    /**
     * Runs a list of statements.
     * RuntimeError propagates to the caller (Shell catches it; test harness catches it).
     */
    public void run(List<Stmt> program) {
        // TODO: log start, execute each statement, log complete
        throw new UnsupportedOperationException("TODO: implement run");
    }

    // ── Statement visitors ────────────────────────────────────────────────────

    @Override
    public Void visitVar(Stmt.Var stmt) {
        // TODO: evaluate initializer (if present) and define in environment
        throw new UnsupportedOperationException("TODO: implement visitVar");
    }

    @Override
    public Void visitIf(Stmt.If stmt) {
        // TODO
        throw new UnsupportedOperationException("TODO: implement visitIf");
    }

    @Override
    public Void visitFor(Stmt.For stmt) {
        // TODO: create loop environment, execute init, loop while condition, run increment
        throw new UnsupportedOperationException("TODO: implement visitFor");
    }

    @Override
    public Void visitPrint(Stmt.Print stmt) {
        // TODO: evaluate expression and println(stringify(value))
        throw new UnsupportedOperationException("TODO: implement visitPrint");
    }

    @Override
    public Void visitBlock(Stmt.Block stmt) {
        // TODO: execute block in a child environment
        throw new UnsupportedOperationException("TODO: implement visitBlock");
    }

    @Override
    public Void visitExpression(Stmt.Expression stmt) {
        // TODO
        throw new UnsupportedOperationException("TODO: implement visitExpression");
    }

    // ── Expression visitors ───────────────────────────────────────────────────

    @Override
    public Object visitLiteral(Expr.Literal expr) {
        // TODO
        throw new UnsupportedOperationException("TODO: implement visitLiteral");
    }

    @Override
    public Object visitGrouping(Expr.Grouping expr) {
        // TODO
        throw new UnsupportedOperationException("TODO: implement visitGrouping");
    }

    @Override
    public Object visitVariable(Expr.Variable expr) {
        // TODO
        throw new UnsupportedOperationException("TODO: implement visitVariable");
    }

    @Override
    public Object visitAssign(Expr.Assign expr) {
        // TODO: evaluate value, assign to environment, return value
        throw new UnsupportedOperationException("TODO: implement visitAssign");
    }

    @Override
    public Object visitUnary(Expr.Unary expr) {
        // TODO: evaluate operand, check it's a number, negate
        throw new UnsupportedOperationException("TODO: implement visitUnary");
    }

    @Override
    public Object visitBinary(Expr.Binary expr) {
        // TODO: evaluate both sides, switch on op type (+, -, *, /)
        throw new UnsupportedOperationException("TODO: implement visitBinary");
    }

    @Override
    public Object visitComparison(Expr.Comparison expr) {
        // TODO: evaluate both sides, check numbers, switch on > / <
        throw new UnsupportedOperationException("TODO: implement visitComparison");
    }

    @Override
    public Object visitLogical(Expr.Logical expr) {
        // TODO: short-circuit evaluation for and / or
        throw new UnsupportedOperationException("TODO: implement visitLogical");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Execute a block in a fresh child environment. */
    public void executeBlock(List<Stmt> statements, Environment blockEnv) {
        // TODO: swap environment, execute all, restore even on exception
        throw new UnsupportedOperationException("TODO: implement executeBlock");
    }

    private Object evaluate(Expr expr)  { return expr.accept(this); }
    private void   execute(Stmt stmt)   { stmt.accept(this); }

    /** Truthiness: Boolean → itself; null → false; everything else → true. */
    private boolean isTruthy(Object value) {
        if (value == null)             return false;
        if (value instanceof Boolean b) return b;
        return true;
    }

    private void checkNumberOperand(Token op, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(op, "Operand must be a number.");
    }

    private void checkNumberOperands(Token op, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;
        throw new RuntimeError(op, "Operands must be numbers.");
    }

    /**
     * Converts a runtime value to its display string.
     * Integral doubles are printed without the trailing .0 (15.0 → "15").
     */
    public static String stringify(Object value) {
        if (value == null) return "nil";
        if (value instanceof Double d) {
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf(d.longValue());
            }
            return d.toString();
        }
        return value.toString(); // Boolean, String
    }
}
