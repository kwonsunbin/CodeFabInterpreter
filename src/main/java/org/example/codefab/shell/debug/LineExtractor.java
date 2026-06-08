package org.example.codefab.shell.debug;

import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;

/**
 * AST 노드에서 디버그 표시용 대표 줄번호를 추출하는 Visitor.
 *
 * Executor/Checker와 동일하게 Expr.Visitor/Stmt.Visitor를 구현하여
 * instanceof 분기 대신 더블 디스패치로 줄번호를 얻는다. 상태가 없으므로 싱글턴.
 *
 * 주의: Expr.Visitor/Stmt.Visitor의 visitCall/visitArrayGet/visitArraySet/
 * visitFunction/visitReturn/visitArrayDecl은 default가 예외를 던지므로
 * 모두 오버라이드해야 한다.
 */
final class LineExtractor implements Expr.Visitor<Integer>, Stmt.Visitor<Integer> {

    static final LineExtractor INSTANCE = new LineExtractor();

    private LineExtractor() {}

    /** 구문에서 대표 줄번호를 추출. null이면 -1 */
    int lineOf(Stmt stmt) { return stmt == null ? -1 : stmt.accept(this); }

    /** 표현식에서 대표 줄번호를 추출. 토큰이 없는 리터럴/ null이면 -1 */
    int lineOf(Expr expr) { return expr == null ? -1 : expr.accept(this); }

    // ── Stmt ──────────────────────────────────────────────────────────────────

    @Override public Integer visitVar(Stmt.Var s)               { return s.name.line(); }
    @Override public Integer visitPrint(Stmt.Print s)           { return lineOf(s.expression); }
    @Override public Integer visitExpression(Stmt.Expression s) { return lineOf(s.expression); }
    @Override public Integer visitIf(Stmt.If s)                 { return lineOf(s.condition); }
    @Override public Integer visitFor(Stmt.For s) {
        return s.initializer != null ? lineOf(s.initializer) : lineOf(s.condition);
    }
    @Override public Integer visitBlock(Stmt.Block s) {
        return s.statements.isEmpty() ? -1 : lineOf(s.statements.get(0));
    }
    @Override public Integer visitFunction(Stmt.Function s)     { return s.name.line(); }
    @Override public Integer visitReturn(Stmt.Return s)         { return s.keyword.line(); }
    @Override public Integer visitArrayDecl(Stmt.ArrayDecl s)   { return s.name.line(); }

    // ── Expr ──────────────────────────────────────────────────────────────────

    @Override public Integer visitAssign(Expr.Assign e)         { return e.name.line(); }
    @Override public Integer visitVariable(Expr.Variable e)     { return e.name.line(); }
    @Override public Integer visitBinary(Expr.Binary e)         { return e.op.line(); }
    @Override public Integer visitLogical(Expr.Logical e)       { return e.op.line(); }
    @Override public Integer visitComparison(Expr.Comparison e) { return e.op.line(); }
    @Override public Integer visitUnary(Expr.Unary e)           { return e.op.line(); }
    @Override public Integer visitGrouping(Expr.Grouping e)     { return lineOf(e.expression); }
    @Override public Integer visitLiteral(Expr.Literal e)       { return e.line; }
    @Override public Integer visitArrayGet(Expr.ArrayGet e)     { return e.name.line(); }
    @Override public Integer visitArraySet(Expr.ArraySet e)     { return e.name.line(); }
    @Override public Integer visitCall(Expr.Call e)             { return e.paren.line(); }
}
