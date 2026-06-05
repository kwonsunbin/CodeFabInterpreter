package org.example.codefab.executor;

import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.example.codefab.error.RuntimeError;
import org.example.codefab.log.Logger;
import org.example.codefab.token.Token;
import org.example.codefab.token.TokenType;

import java.util.ArrayList;
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
        log.executionStart();
        for (Stmt stmt : program) execute(stmt);
        log.executionComplete();
    }

    // ── Statement visitors ────────────────────────────────────────────────────

    @Override
    public Void visitVar(Stmt.Var stmt) {
        Object value = stmt.initializer != null ? evaluate(stmt.initializer) : null;
        environment.define(stmt.name.origin(), value);
        return null;
    }

    @Override
    public Void visitIf(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) execute(stmt.thenBranch);
        else if (stmt.elseBranch != null)        execute(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitFor(Stmt.For stmt) {
        Environment loopEnv = new Environment(environment);
        Environment previous = environment;
        environment = loopEnv;
        try {
            if (stmt.initializer != null) execute(stmt.initializer);
            while (stmt.condition == null || isTruthy(evaluate(stmt.condition))) {
                execute(stmt.body);
                if (stmt.increment != null) evaluate(stmt.increment);
            }
        } finally {
            environment = previous;
        }
        return null;
    }

    @Override
    public Void visitPrint(Stmt.Print stmt) {
        System.out.println(stringify(evaluate(stmt.expression)));
        return null;
    }

    @Override
    public Void visitBlock(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitExpression(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitFuncDecl(Stmt.FuncDecl stmt) {
        CodeFabFunction function = new CodeFabFunction(stmt, environment);
        environment.define(stmt.name.origin(), function);
        return null;
    }

    @Override
    public Void visitReturn(Stmt.Return stmt) {
        Object value = stmt.value != null ? evaluate(stmt.value) : null;
        throw new ReturnException(value);
    }

    // ── Expression visitors ───────────────────────────────────────────────────

    @Override
    public Object visitLiteral(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitVariable(Expr.Variable expr) {
        if (expr.depth >= 0) return environment.getAt(expr.depth, expr.name.origin());
        return environment.get(expr.name);
    }

    @Override
    public Object visitAssign(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        if (expr.depth >= 0) {
            environment.setAt(expr.depth, expr.name.origin(), value);
        } else {
            environment.assign(expr.name, value);
        }
        return value;
    }

    @Override
    public Object visitGrouping(Expr.Grouping expr) {
        if (expr.foldedValue != null) return expr.foldedValue;
        return evaluate(expr.expression);
    }

    @Override
    public Object visitUnary(Expr.Unary expr) {
        if (expr.foldedValue != null) return expr.foldedValue;
        Object operand = evaluate(expr.operand);
        return switch (expr.op.type()) {
            case MINUS -> { checkNumberOperand(expr.op, operand); yield -(double) operand; }
            case BANG  -> !isTruthy(operand);
            default    -> throw new RuntimeError(expr.op, "Unknown unary operator.");
        };
    }

    @Override
    public Object visitBinary(Expr.Binary expr) {
        if (expr.foldedValue != null) return expr.foldedValue;
        Object left  = evaluate(expr.left);
        Object right = evaluate(expr.right);
        return switch (expr.op.type()) {
            case PLUS -> {
                if (left instanceof Double l && right instanceof Double r) yield l + r;
                if (left instanceof String  l && right instanceof String  r) yield l + r;
                throw new RuntimeError(expr.op, "Operands must be two numbers or two strings.");
            }
            case MINUS -> { checkNumberOperands(expr.op, left, right); yield (double) left - (double) right; }
            case STAR  -> { checkNumberOperands(expr.op, left, right); yield (double) left * (double) right; }
            case SLASH -> {
                checkNumberOperands(expr.op, left, right);
                if ((double) right == 0) throw new RuntimeError(expr.op, "Division by zero.");
                yield (double) left / (double) right;
            }
            default    -> throw new RuntimeError(expr.op, "Unknown binary operator.");
        };
    }

    @Override
    public Object visitComparison(Expr.Comparison expr) {
        if (expr.foldedValue != null) return expr.foldedValue;
        Object left  = evaluate(expr.left);
        Object right = evaluate(expr.right);
        checkNumberOperands(expr.op, left, right);
        return switch (expr.op.type()) {
            case GREATER       -> (double) left >  (double) right;
            case GREATER_EQUAL -> (double) left >= (double) right;
            case LESS          -> (double) left <  (double) right;
            case LESS_EQUAL    -> (double) left <= (double) right;
            default -> throw new RuntimeError(expr.op, "Unknown comparison operator.");
        };
    }

    @Override
    public Object visitLogical(Expr.Logical expr) {
        if (expr.foldedValue != null) return expr.foldedValue;
        Object left = evaluate(expr.left);
        if (expr.op.type() == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left;
        }
        return evaluate(expr.right);
    }

    @Override
    public Object visitCall(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr arg : expr.arguments) arguments.add(evaluate(arg));

        if (!(callee instanceof CodeFabCallable function)) {
            throw new RuntimeError(expr.paren, "Can only call functions.");
        }
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren,
                    "Expected " + function.arity() + " arguments but got " + arguments.size() + ".");
        }
        return function.call(this, arguments);
    }

    @Override
    public Object visitArrayLiteral(Expr.ArrayLiteral expr) {
        List<Object> elements = new ArrayList<>();
        for (Expr element : expr.elements) elements.add(evaluate(element));
        return elements;
    }

    @Override
    public Object visitArrayIndex(Expr.ArrayIndex expr) {
        Object arrayObj = evaluate(expr.target);
        Object indexObj = evaluate(expr.index);

        if (!(arrayObj instanceof List<?>)) {
            throw new RuntimeError(expr.bracket, "Only arrays can be indexed.");
        }
        if (!(indexObj instanceof Double d) || d != Math.floor(d) || Double.isInfinite(d)) {
            throw new RuntimeError(expr.bracket, "Array index must be an integer.");
        }

        @SuppressWarnings("unchecked")
        List<Object> array = (List<Object>) arrayObj;
        int index = ((Double) indexObj).intValue();

        if (index < 0 || index >= array.size()) {
            throw new RuntimeError(expr.bracket,
                    "Array index " + index + " out of bounds (length " + array.size() + ").");
        }
        return array.get(index);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Execute a block in a fresh child environment. */
    public void executeBlock(List<Stmt> statements, Environment blockEnv) {
        Environment previous = environment;
        environment = blockEnv;
        try {
            for (Stmt stmt : statements) execute(stmt);
        } finally {
            environment = previous;
        }
    }

    private Object evaluate(Expr expr)  {
        return expr.accept(this);
    }

    private void   execute(Stmt stmt)   { stmt.accept(this); }

    /** Truthiness: Boolean → itself; null → false; everything else → true. */
    private boolean isTruthy(Object value) {
        if (value == null)             return false;
        if (value instanceof Boolean b) return b;
        return true;
    }

    private void checkNumberOperand(Token op, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(op,
                typeName(operand) + " 타입에 대해 '" + op.origin() + "' 연산은 지원하지 않습니다.");
    }

    private void checkNumberOperands(Token op, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;
        throw new RuntimeError(op,
                typeName(left) + " 타입과 " + typeName(right) + " 타입에 대해 '" +
                op.origin() + "' 연산은 지원하지 않습니다.");
    }

    private static String typeName(Object value) {
        if (value == null)                    return "null";
        if (value instanceof Double)          return "number";
        if (value instanceof Boolean)         return "boolean";
        if (value instanceof String)          return "string";
        if (value instanceof List<?>)         return "array";
        if (value instanceof CodeFabCallable) return "function";
        return value.getClass().getSimpleName();
    }

    /**
     * Converts a runtime value to its display string.
     * Integral doubles are printed without the trailing .0 (15.0 → "15").
     * Arrays are rendered as [e0, e1, ...] with each element stringified recursively.
     */
    public static String stringify(Object value) {
        if (value == null) return "nil";
        if (value instanceof Double d) {
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf(d.longValue());
            }
            return d.toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(stringify(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return value.toString(); // Boolean, String, CodeFabFunction
    }
}
