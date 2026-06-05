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

    private ExecutionListener listener;

    public Executor(Logger log) { this.log = log; }

    /** 디버그 리스너 등록 / 해제 (null 전달 시 해제) */
    public void setListener(ExecutionListener l) { this.listener = l; }

    /** 현재 런타임 환경 반환 — Debugger의 inspect / watch 에서 사용 */
    public Environment getEnvironment() { return environment; }

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
        if (listener != null) listener.onEnterScope();
        try {
            if (stmt.initializer != null) execute(stmt.initializer);
            while (stmt.condition == null || isTruthy(evaluate(stmt.condition))) {
                execute(stmt.body);
                if (stmt.increment != null) evaluate(stmt.increment);
            }
        } finally {
            if (listener != null) listener.onExitScope();
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

    // ── Expression visitors ───────────────────────────────────────────────────

    @Override
    public Object visitLiteral(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitGrouping(Expr.Grouping expr) {
        if (expr.foldedValue != null) return expr.foldedValue; // 상수 폴딩 결과 사용
        return evaluate(expr.expression);
    }

    @Override
    public Object visitVariable(Expr.Variable expr) {
        // 정적 바인딩: Checker가 거리를 계산했으면 O(1)로 즉시 접근
        if (expr.depth >= 0) return environment.getAt(expr.depth, expr.name.origin());
        return environment.get(expr.name); // 미해석(전역 등) → 동적 조회 폴백
    }

    @Override
    public Object visitAssign(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        // 정적 바인딩: 거리가 있으면 O(1)로 즉시 기록
        if (expr.depth >= 0) environment.setAt(expr.depth, expr.name.origin(), value);
        else                 environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Object visitUnary(Expr.Unary expr) {
        if (expr.foldedValue != null) return expr.foldedValue; // 상수 폴딩 결과 사용
        Object operand = evaluate(expr.operand);
        return switch (expr.op.type()) {
            case MINUS -> { checkNumberOperand(expr.op, operand); yield -(double) operand; }
            case BANG  -> !isTruthy(operand);
            default    -> throw new RuntimeError(expr.op, "Unknown unary operator.");
        };
    }

    @Override
    public Object visitBinary(Expr.Binary expr) {
        if (expr.foldedValue != null) return expr.foldedValue; // 상수 폴딩 결과 사용
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
        if (expr.foldedValue != null) return expr.foldedValue; // 상수 폴딩 결과 사용
        Object left  = evaluate(expr.left);
        Object right = evaluate(expr.right);
        return switch (expr.op.type()) {
            case GREATER       -> compareOrdered(left, right) >  0;
            case GREATER_EQUAL -> compareOrdered(left, right) >= 0;
            case LESS          -> compareOrdered(left, right) <  0;
            case LESS_EQUAL    -> compareOrdered(left, right) <= 0;
            case EQUAL_EQUAL   -> isEqual(left, right);
            case BANG_EQUAL    -> !isEqual(left, right);
            default -> throw new RuntimeError(expr.op, "Unknown comparison operator.");
        };
    }

    // 숫자끼리는 수치 비교, 그 외(문자열, 혼합)는 stringify 후 사전순 비교
    private int compareOrdered(Object left, Object right) {
        if (left instanceof Double l && right instanceof Double r) return Double.compare(l, r);
        return stringify(left).compareTo(stringify(right));
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;
        return a.equals(b);
    }

    @Override
    public Object visitLogical(Expr.Logical expr) {
        if (expr.foldedValue != null) return expr.foldedValue; // 상수 폴딩 결과 사용
        Object left = evaluate(expr.left);
        if (expr.op.type() == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left;
        }
        return evaluate(expr.right);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Execute a block in a fresh child environment. */
    public void executeBlock(List<Stmt> statements, Environment blockEnv) {
        Environment previous = environment;
        environment = blockEnv;
        if (listener != null) listener.onEnterScope();
        try {
            for (Stmt stmt : statements) execute(stmt);
        } finally {
            if (listener != null) listener.onExitScope();
            environment = previous;
        }
    }

    private Object evaluate(Expr expr) { return expr.accept(this); }

    private void execute(Stmt stmt) {
        if (listener != null) listener.onStatement(stmt);
        stmt.accept(this);
    }

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
        if (value == null)            return "null";
        if (value instanceof Double)  return "number";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof String)  return "string";
        return value.getClass().getSimpleName();
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
