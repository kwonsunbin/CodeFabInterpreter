# CodeFabInterpreter — Best Practice 분석

> 179개 커밋 / 40개 PR / 4인 TDD 팀 프로젝트  
> 분석 기준: 디자인패턴 · TDD · 코드리뷰

---

## 목차

1. [디자인패턴](#1-디자인패턴)
   - 1.1 Visitor 패턴
   - 1.2 Sealed Classes
   - 1.3 Builder 패턴
   - 1.4 Strategy 패턴 (에러 계층)
   - 1.5 Observer 패턴 (디버거)
   - 1.6 Pipeline / Facade 패턴
2. [TDD 사례](#2-tdd-사례)
   - 2.1 Hand AST 기반 유닛 테스트
   - 2.2 E2E + REPL 상태 공유 테스트
   - 2.3 폭탄식(Bomb) 최적화 검증 테스트
   - 2.4 브랜치 커버리지 목표 관리
   - 2.5 Mock → 실제 Lexer 전환
   - 2.6 테스트 헬퍼 메서드 변천
3. [코드리뷰 사례](#3-코드리뷰-사례)
   - 3.1 PR #31 — Environment 단일화
   - 3.2 PR #16 — SRP 적용
   - 3.3 PR #14 — Logger DI 도입

---

## 1. 디자인패턴

### 1.1 Visitor 패턴

> **패턴 개념**  
> 객체 구조(Element)와 그 구조를 처리하는 연산(Visitor)을 분리하는 패턴.  
> Element 클래스는 `accept(Visitor)` 하나만 구현하면 되고, 새로운 연산이 필요할 때는 Element를 전혀 수정하지 않고 Visitor 구현체만 추가한다.  
> 인터프리터·컴파일러에서 AST 노드(Element)에 실행·타입검사·출력 등 여러 연산을 붙일 때 특히 유효하다.
>
> | 역할 | 이 프로젝트에서 |
> |------|----------------|
> | Element | `Expr` / `Stmt` 서브클래스 (Binary, Var, For …) |
> | Visitor 인터페이스 | `Expr.Visitor<R>`, `Stmt.Visitor<R>` |
> | ConcreteVisitor | `Executor`, `Checker`, `LineExtractor` |
> | `accept()` | 각 AST 노드 내부에서 `visitor.visitXxx(this)` 호출 |

**참고 위치**

| 구분 | 위치 |
|------|------|
| 인터페이스 정의 | `ast/Expr.java` — `Visitor<R>` 인터페이스 |
| 인터페이스 정의 | `ast/Stmt.java` — `Visitor<R>` 인터페이스 |
| 구현체 1 | `executor/Executor.java` |
| 구현체 2 | `checker/Checker.java` |
| 구현체 3 | `shell/debug/LineExtractor.java` |
| PR | [PR #35](https://github.com/kwonsunbin/CodeFabInterpreter/pull/35) `refactor/debugger-line-visitor` |
| 핵심 커밋 | `bc826be` — "Debugger의 줄번호 추출 instanceof 체인을 Visitor로 교체" |
| 신규 파일 | `shell/debug/LineExtractor.java` |
| 영향 파일 | `Debugger.java` — `lineOf()`, `exprLine()` 메서드 삭제 |

**Before** — PR #35 이전: Debugger 내부에 직접 instanceof 체인

```java
// Debugger.java (커밋 bc826be 이전) — 29줄 instanceof 분기
private int lineOf(Stmt stmt) {
    if (stmt instanceof Stmt.Var s)        return s.name.line();
    if (stmt instanceof Stmt.Print s)      return exprLine(s.expression);
    if (stmt instanceof Stmt.Expression s) return exprLine(s.expression);
    if (stmt instanceof Stmt.If s)         return exprLine(s.condition);
    if (stmt instanceof Stmt.For s)
        return s.initializer != null ? lineOf(s.initializer) : exprLine(s.condition);
    if (stmt instanceof Stmt.Block s)
        return s.statements.isEmpty() ? -1 : lineOf(s.statements.get(0));
    return -1;
}

private int exprLine(Expr expr) {
    if (expr == null)                       return -1;
    if (expr instanceof Expr.Assign e)      return e.name.line();
    if (expr instanceof Expr.Variable e)    return e.name.line();
    if (expr instanceof Expr.Binary e)      return e.op.line();
    if (expr instanceof Expr.Logical e)     return e.op.line();
    if (expr instanceof Expr.Comparison e)  return e.op.line();
    if (expr instanceof Expr.Unary e)       return e.op.line();
    if (expr instanceof Expr.Grouping e)    return exprLine(e.expression);
    if (expr instanceof Expr.Literal e)     return e.line;
    // Call, ArrayGet, ArraySet 누락 → 항상 -1 반환 ("?번째 줄" 표시)
    return -1;
}
```

**After** — Visitor 패턴으로 추출, sealed switch로 완전성 보장

```java
// LineExtractor.java (커밋 bc826be) — Visitor 구현체로 분리
final class LineExtractor
        implements Expr.Visitor<Integer>, Stmt.Visitor<Integer> {

    static final LineExtractor INSTANCE = new LineExtractor();

    @Override public Integer visitVar(Stmt.Var s)            { return s.name.line(); }
    @Override public Integer visitPrint(Stmt.Print s)        { return s.expression.accept(this); }
    @Override public Integer visitCall(Expr.Call e)          { return e.paren.line(); }  // ← 이전엔 누락
    @Override public Integer visitArrayGet(Expr.ArrayGet e)  { return e.name.line(); }  // ← 이전엔 누락
    @Override public Integer visitArraySet(Expr.ArraySet e)  { return e.name.line(); }  // ← 이전엔 누락
    // sealed class 강제 → permits 목록의 모든 타입 반드시 구현, 미구현 시 컴파일 오류

    public int lineOf(Stmt stmt) { return stmt.accept(this); }
    public int lineOf(Expr expr) { return expr.accept(this); }
}

// Debugger.java — 단 한 줄로 교체
int line = LineExtractor.INSTANCE.lineOf(stmt);
```

**BP 포인트**

- **버그 수정 효과**: `Call`, `ArrayGet`, `ArraySet`의 줄번호가 `-1`(→ `?번째 줄`)에서 정확한 줄번호로 수정
- **OCP 준수**: Expr/Stmt 수정 없이 새 Visitor 목적(프린터, 타입체커 등) 추가 가능
- **sealed class 시너지**: 새 AST 노드 추가 시 LineExtractor 컴파일 오류 → 줄번호 누락 방지
- **패턴 일관성**: Executor, Checker와 동일한 Visitor 인터페이스 사용 → 코드베이스 통일

**리뷰 포인트 요약 (PR #35)**

| 항목 | Before | After |
|------|--------|-------|
| 구현 방식 | `instanceof` 체인 (29줄) | Visitor 패턴 |
| `Call` 줄번호 | 항상 `-1` ("?번째 줄") | `e.paren.line()` 정확 반환 |
| `ArrayGet/Set` 줄번호 | 항상 `-1` | `e.name.line()` 정확 반환 |
| 신규 노드 누락 위험 | 컴파일 통과 (런타임 버그) | sealed switch → 컴파일 오류 |
| 패턴 일관성 | Executor/Checker와 다른 방식 | 동일 Visitor 인터페이스 |

---

### 1.2 Sealed Classes

> **패턴 개념**  
> Java 17+의 `sealed class`는 허용된 서브클래스 목록(`permits`)을 컴파일러가 강제한다.  
> `switch` 식에서 모든 서브클래스를 분기하지 않으면 컴파일 오류가 발생하므로, "새 타입을 추가했는데 처리 코드를 빠뜨리는" 버그를 빌드 단계에서 차단한다.  
> Visitor 패턴과 결합하면, 새 AST 노드를 추가할 때 `permits` 목록 → Visitor 인터페이스 → 모든 구현체 순서로 컴파일 오류가 연쇄적으로 발생하여 누락을 원천 방지한다.
>
> | 역할 | 이 프로젝트에서 |
> |------|----------------|
> | sealed 선언 | `Expr`, `Stmt` — `permits`로 허용 서브클래스 고정 |
> | 강제 대상 | `Executor`, `Checker`, `LineExtractor`의 `switch` 분기 |
> | 누락 감지 시점 | 런타임 → **컴파일 타임**으로 전환 |

**참고 위치**

| 구분 | 위치 |
|------|------|
| 선언 | `ast/Expr.java` Line 14 |
| 선언 | `ast/Stmt.java` Line 11 |
| 아키텍처 규칙 테스트 | `assembler/ParserTest.java` — `exprNodesNeverContainStmtField()` |

**Before** — sealed 미적용 시 발생하는 문제 (가상 시나리오)

```java
// sealed 없이 open class였다면:
public abstract class Expr {
    public interface Visitor<R> {
        R visitBinary(Binary expr);
        // ... (새 노드 추가해도 여기 추가 강제 안 됨)
    }
}

// Checker.java — 일반 if-else 체인으로 처리
private Object scanExpr(Expr expr) {
    if (expr instanceof Expr.Binary b)   { ... }
    if (expr instanceof Expr.Call c)     { ... }
    // 새 노드 Expr.ArrayGet 추가해도 여기 빠뜨려도 컴파일 통과
    // → 런타임에 처리 안 되거나 잘못된 분기 실행
}
```

**After** — sealed class + sealed switch로 컴파일 타임 완전성 보장

```java
// Expr.java
public abstract sealed class Expr
        permits Expr.Binary, Expr.Logical, Expr.Comparison,
                Expr.Unary, Expr.Grouping, Expr.Literal,
                Expr.Variable, Expr.Assign, Expr.Call,
                Expr.ArrayGet, Expr.ArraySet {
    // permits 목록이 유일한 하위 타입 목록 → 컴파일러가 강제
}

// Checker.java — sealed switch (빠진 케이스 = 컴파일 오류)
private Object scanExpr(Expr expr) {
    return switch (expr) {
        case Expr.Literal l   -> l.value;
        case Expr.Binary b    -> { ... yield result; }
        case Expr.Call c      -> { ... yield null; }
        case Expr.ArrayGet ag -> { ... yield null; }
        // Expr.ArraySet 빠지면 컴파일 오류 → 누락 불가
    };
}

// ParserTest.java — 아키텍처 규칙을 테스트로도 이중 보호
@Test
void exprNodesNeverContainStmtField() throws Exception {
    for (Class<?> permitted : Expr.class.getPermittedSubclasses()) {
        for (Field field : permitted.getDeclaredFields()) {
            assertFalse(Stmt.class.isAssignableFrom(field.getType()),
                "Expr subclass " + permitted.getSimpleName() +
                " has a Stmt-typed field — 순환 의존 위반!");
        }
    }
}
```

**BP 포인트**

- **신규 노드 추가 프로세스**: `permits` 추가 → Visitor 인터페이스 메서드 추가 → 구현체(Executor/Checker/LineExtractor) 컴파일 오류 → 전체 구현 강제
- **런타임 오류 → 컴파일 오류**: 누락된 타입 처리가 즉시 빌드 실패로 드러남
- **테스트로 이중 보호**: `exprNodesNeverContainStmtField()`가 구조적 설계 규칙을 자동 검증

---

### 1.3 Builder 패턴

> **패턴 개념**  
> 복잡한 객체의 생성 과정을 단계적으로 조립하고, 최종 `build()` 호출 시점에 완성 객체를 반환하는 패턴.  
> 생성자 인자 순서나 타입을 호출 측이 직접 알 필요가 없으며, 어떤 구현 클래스를 선택할지(Binary vs Logical vs Comparison)를 Builder 내부로 캡슐화할 수 있다.
>
> | 역할 | 이 프로젝트에서 |
> |------|----------------|
> | Builder | `Expr.Builder` — 내부 클래스 |
> | 생성 결정 로직 | `build()` 내부에서 `op.type()` 기반으로 Binary/Logical/Comparison 자동 선택 |
> | 호출 측 | `Parser.leftAssoc()`, `ExecutorTest` 헬퍼 |
> | 진입점 | `Expr.builder()` 정적 팩토리 |

**참고 위치**

| 구분 | 위치 |
|------|------|
| Builder 정의 | `ast/Expr.java` — `Expr.Builder` 내부 클래스 |
| PR | [PR #27](https://github.com/kwonsunbin/CodeFabInterpreter/pull/27) `refactor/parser` |
| 1단계 (직접 호출) | [`d52783e`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/d52783e) ~ [`2913349`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/2913349) |
| 2단계 (NodeBuilder) | [`d276549`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/d276549) — "연산자 소스 반복되는 내용을 수정" |
| 3단계 (Builder 완성) | [`d8c7c5a`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/d8c7c5a) — "Expr Builder 패턴 도입" |
| 영향 파일 | `Expr.java`, `Parser.java`, `ExecutorTest.java` |
| 활용 — 파서 | `assembler/Parser.java` — `leftAssoc()` 메서드 |
| 활용 — 테스트 | `executor/ExecutorTest.java` — `num()`, `str()` 헬퍼 |

**변천 요약**

| 단계 | 방식 | 클래스 결정 위치 |
|------|------|----------------|
| 1단계 | `new Expr.Binary(...)`, `new Expr.Logical(...)` 직접 호출 | Parser 각 메서드 |
| 2단계 | `NodeBuilder` 람다로 추상화, 클래스 지정은 호출 측 유지 | `or()`, `and()` 등 |
| 3단계 | `Expr.builder().left().op().right().build()` 단일 API | `Expr.Builder.build()` 내부 |

**1단계: 직접 생성자 호출** ([`d52783e`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/d52783e))

```java
// Parser.java — while 루프 + new Expr.XXX()가 5개 메서드에 중복
private Expr or() {
    Expr expr = and();
    while (match(TokenType.OR)) expr = new Expr.Logical(expr, previous(), and());
    return expr;
}
private Expr term() {
    Expr expr = factor();
    while (match(TokenType.PLUS, TokenType.MINUS)) expr = new Expr.Binary(expr, previous(), factor());
    return expr;
}

// ExecutorTest.java — 생성자 직접 호출로 파편화
var expr = new Expr.Binary(new Expr.Literal(1.0), plusTok, new Expr.Literal(2.0));
var cmp  = new Expr.Comparison(new Expr.Literal(3.0), gtTok, new Expr.Literal(2.0));
```

**2단계: NodeBuilder 도입 — 루프 중복 제거** ([`d276549`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/d276549))

```java
// Parser.java — leftAssoc()로 루프 추출, 클래스는 여전히 호출 측에서 지정
@FunctionalInterface
private interface NodeBuilder {
    Expr build(Expr left, Token op, Expr right);
}

private Expr leftAssoc(Supplier<Expr> operand, NodeBuilder builder, TokenType... ops) {
    Expr expr = operand.get();
    while (match(ops)) expr = builder.build(expr, previous(), operand.get());
    return expr;
}

private Expr or()   { return leftAssoc(this::and,    Expr.Logical::new, TokenType.OR); }  // 클래스 지정 유지
private Expr term() { return leftAssoc(this::factor, Expr.Binary::new,  PLUS, MINUS); }  // 클래스 지정 유지
```

**3단계: Builder 패턴 완성 — 클래스 지정 완전 소멸** ([`d8c7c5a`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/d8c7c5a))

```java
// Expr.java — Builder 내부 클래스, op.type()으로 클래스 자동 선택
public static final class Builder {
    private static final Set<TokenType> LOGICAL_OPS    = EnumSet.of(AND, OR);
    private static final Set<TokenType> COMPARISON_OPS = EnumSet.of(GREATER, GREATER_EQUAL, ...);

    public Expr build() {
        if (left != null && op != null && right != null) {
            if (LOGICAL_OPS.contains(op.type()))    return new Logical(left, op, right);
            if (COMPARISON_OPS.contains(op.type())) return new Comparison(left, op, right);
            return new Binary(left, op, right);   // ← op 타입에 따라 자동 분기
        }
        if (op != null && operand != null) return new Unary(op, operand);
        if (name != null)                  return new Variable(name);
        if (literalValue != null)          return new Literal(literalValue);
        if (expression != null)            return new Grouping(expression);
        throw new IllegalStateException("Invalid builder state");
    }
}

// Parser.java — NodeBuilder 인터페이스 삭제, 단일 Builder로 통일
private Expr leftAssoc(Supplier<Expr> operand, TokenType... ops) {
    Expr expr = operand.get();
    while (match(ops))
        expr = Expr.builder().left(expr).op(previous()).right(operand.get()).build();
    return expr;
}

private Expr or()   { return leftAssoc(this::and,    TokenType.OR); }          // 클래스 참조 없음
private Expr term() { return leftAssoc(this::factor, TokenType.PLUS, TokenType.MINUS); } // 클래스 참조 없음

// primary()도 동일하게 교체
if (match(IDENTIFIER))  return Expr.builder().name(previous()).build();
if (match(LEFT_PAREN))  return Expr.builder().expression(expr).build();

// ExecutorTest.java — 동일 Builder API 사용
private Expr num(double v) { return Expr.builder().literalValue(v).build(); }
var expr = Expr.builder().left(num(1.0)).op(tok(PLUS, "+")).right(num(2.0)).build();
```

**BP 포인트**

- **3단계 진화**: 직접 생성자 → NodeBuilder(루프 중복 제거) → Expr.Builder(클래스 지정 중앙화) — 각 단계가 이전의 가장 큰 한계 하나씩 해결
- **연산자-클래스 매핑 이동**: "OR → `Expr.Logical`" 지식이 Parser 각 메서드에서 `Expr.Builder.build()` 내부로 수렴 → 새 연산자 추가 시 Parser 수정 없음
- **NodeBuilder 삭제**: 이항 연산만 처리하던 `@FunctionalInterface`가 단항·리터럴·변수까지 수용하는 `Expr.Builder`로 흡수
- **API 통일**: Parser와 ExecutorTest 150건이 동일한 `Expr.builder()` 사용 → 생성 불일치 버그 방지

**리뷰 포인트 요약 (PR #27)**

| 항목 | Before | After |
|------|--------|-------|
| 타입 결정 위치 | `or()`, `and()` 등 파서 각 단계 | `Expr.Builder.build()` 중앙화 |
| 파서 API | `leftAssoc(operand, Expr.Logical::new, ops)` — 3인자 | `leftAssoc(operand, ops)` — 2인자 |
| 테스트 생성 | `new Expr.Binary(...)` 직접 호출 | `Expr.builder().left().op().right().build()` |
| 인터페이스 파편화 | `NodeBuilder` FunctionalInterface 별도 존재 | 제거됨 |

---

### 1.4 Strategy 패턴 (에러 계층)

> **패턴 개념**  
> 동일한 역할을 하는 알고리즘(또는 동작)을 인터페이스로 추상화하고, 런타임에 구체적인 구현체를 교체할 수 있게 하는 패턴.  
> 이 프로젝트에서는 "에러를 어떻게 표현하는가"를 추상 클래스 `CodeFabError`로 정의하고, 각 단계(Lexer·Parser·Runtime)가 `stage()` 메서드를 통해 자신만의 표현 전략을 제공한다.  
> 에러를 받는 쪽(`Pipeline`)은 구체 타입을 몰라도 `userMessage()`만 호출하면 된다.
>
> | 역할 | 이 프로젝트에서 |
> |------|----------------|
> | Strategy 인터페이스 | `CodeFabError` 추상 클래스 — `stage()` 추상 메서드 |
> | ConcreteStrategy | `LexError`, `ParseError`, `RuntimeError` |
> | Context (사용 측) | `Pipeline.run()` — `catch (CodeFabError e)` 단일 포인트 |
> | 교체 시점 | 각 단계(Lexer→Parser→Executor)가 자신의 에러 타입을 `throw` |

**참고 위치**

| 구분 | 위치 |
|------|------|
| 기반 클래스 | `error/CodeFabError.java` |
| 구현체 | `LexError.java`, `ParseError.java`, `RuntimeError.java` |
| 도입 | 초기 설계 — PR #2(`feat/lexer`), PR #3(`feat/parser`), PR #8(`feat/executor`) |

**Before** — 단일 Exception에 문자열로 단계 구분 (가상 시나리오)

```java
// 단일 예외 클래스로 처리했다면:
throw new RuntimeException("[Lexer] line " + line + ": " + message);
throw new RuntimeException("[Parser] line " + line + ": " + message);

// catch 측에서 문자열 파싱으로 구분 → 취약
catch (RuntimeException e) {
    String msg = e.getMessage();
    if (msg.startsWith("[Lexer]")) { ... }       // 문자열 비교로 단계 판별
    else if (msg.startsWith("[Parser]")) { ... }
}
```

**After** — 계층적 예외 + `stage()` 추상 메서드로 단계 명시

```java
// CodeFabError.java — 공통 기반
public abstract class CodeFabError extends RuntimeException {
    private final int line;
    private final String message;

    protected CodeFabError(int line, String message) { ... }

    public abstract String stage();  // 각 단계가 오버라이드

    public String userMessage() {
        return "[" + stage() + "] line " + line + ": " + message;
    }
}

// LexError.java
public class LexError extends CodeFabError {
    public String stage() { return "Lexer"; }
}

// ParseError.java
public class ParseError extends CodeFabError {
    public String stage() { return "Parser"; }
}

// RuntimeError.java
public class RuntimeError extends CodeFabError {
    public String stage() { return "runtime"; }
}

// Pipeline.java — 단일 catch 포인트
try {
    List<Stmt> program = assembler.assemble(source);  // LexError, ParseError 가능
    executor.run(program);                            // RuntimeError 가능
} catch (CodeFabError e) {
    out.println(e.userMessage());  // "[Lexer] line 5: ..." 자동 포맷
}
```

**BP 포인트**

- **타입 안전한 단계 구분**: `instanceof LexError` 체크로 catch 측에서 안전하게 단계 구분 가능
- **오류 형식 일관성**: `userMessage()`가 `[단계] line N: 메시지` 형식을 모든 에러에 공통 보장
- **단계별 책임 명확**: 각 클래스는 자신의 stage만 알면 됨 → 새 단계(예: SemanticError) 추가 시 기존 코드 무수정

---

### 1.5 Observer 패턴 (디버거)

> **패턴 개념**  
> 한 객체(Observable/Subject)의 상태 변화를 다른 객체(Observer)들이 자동으로 통보받는 패턴.  
> Observable은 Observer가 누구인지, 무엇을 하는지 알 필요가 없다. 인터페이스를 통해 알림을 보낼 뿐이고, Observer가 없으면 알림 자체를 건너뛴다.  
> 이 패턴 덕분에 Executor(실행 엔진)를 전혀 수정하지 않고 디버그 기능을 탈부착할 수 있다.
>
> | 역할 | 이 프로젝트에서 |
> |------|----------------|
> | Observer 인터페이스 | `ExecutionListener` — `onStatement()`, `onEnterScope()`, `onExitScope()` |
> | Observable (Subject) | `Executor` — `setListener()`, `execute()` 내부에서 `listener.onStatement()` 호출 |
> | ConcreteObserver | `Debugger` — 알림을 받아 `commandLoop()` 실행 |
> | 등록/해제 | `executor.setListener(this)` / `executor.setListener(null)` |

**참고 위치**

| 구분 | 위치 |
|------|------|
| Observer 인터페이스 | `executor/ExecutionListener.java` |
| Observable | `executor/Executor.java` — `setListener()`, `execute()` |
| ConcreteObserver | `shell/debug/Debugger.java` |
| 도입 커밋 | `624048e` — "디버그 모드 Observer 훅 연동 및 정지 로직 활성화" |

**Before** — Observer 도입 전: Executor에 디버거 로직이 직접 결합

```java
// Executor.java (커밋 624048e 이전) — 리스너 없음, 순수 실행만
public class Executor implements Stmt.Visitor<Void>, Expr.Visitor<Object> {

    private final Logger log;
    private final Environment globalEnv = new Environment();
    private Environment environment = globalEnv;

    public Executor(Logger log) { this.log = log; }

    public void run(List<Stmt> program) {
        log.executionStart();
        for (Stmt stmt : program) execute(stmt);
        log.executionComplete();
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);  // 훅 없음 — 디버거가 끼어들 지점이 없음
    }
    // → 디버그 기능을 추가하려면 Executor 코드를 직접 수정해야 했음
}
```

**After** — `ExecutionListener` 인터페이스로 느슨하게 연결

```java
// ExecutionListener.java — Observer 인터페이스
public interface ExecutionListener {
    void onStatement(Stmt stmt);
    void onEnterScope();
    void onExitScope();
}

// Executor.java — 런타임에 Listener 주입 가능
public class Executor ... {
    private ExecutionListener listener;  // null 가능 (일반 실행 모드)

    public void setListener(ExecutionListener l) { this.listener = l; }

    private void execute(Stmt stmt) {
        if (listener != null) listener.onStatement(stmt);  // 훅
        stmt.accept(this);
    }

    private void beginScope() {
        environment.beginScope();
        if (listener != null) listener.onEnterScope();     // 훅
    }
}

// Debugger.java — ConcreteObserver, Executor를 직접 참조하지 않음
public class Debugger implements ExecutionListener {
    @Override
    public void onStatement(Stmt stmt) {
        if (stmt instanceof Stmt.Block || stmt instanceof Stmt.For) return;
        int line = LineExtractor.INSTANCE.lineOf(stmt);
        if (shouldPause(line)) commandLoop(line, isBreakpoint(line));
    }
}

// Debugger.runFile() — 실행 전 등록, finally로 반드시 해제
executor.setListener(this);
try {
    executor.run(program);
} finally {
    executor.setListener(null);  // 디버그 종료 후 오염 방지
}
```

**BP 포인트**

- **Executor 불변**: 디버그 기능 추가를 위해 Executor 코드를 전혀 수정하지 않음
- **런타임 옵션**: `listener == null` 체크만으로 일반 실행 시 오버헤드 최소
- **단일 책임 유지**: Executor는 실행, Debugger는 사용자 인터랙션만 담당
- **finally 보장**: Debugger 예외 발생 시에도 `setListener(null)` 호출 → 다음 실행 오염 방지

---

### 1.6 Pipeline / Facade 패턴

> **패턴 개념**
>
> **Facade**: 복잡한 서브시스템(Lexer → Parser → Checker → Executor 등 여러 단계)을 하나의 단순한 인터페이스로 감싸는 패턴. 외부 코드는 내부 구조를 알 필요 없이 Facade의 단일 메서드만 호출한다.
>
> **Pipeline**: 데이터가 여러 처리 단계를 순서대로 통과하는 구조. 각 단계는 이전 단계의 출력을 입력으로 받으며, 서로 독립적으로 교체·확장할 수 있다.
>
> 이 프로젝트의 `Pipeline` 클래스는 두 패턴을 결합한다. Shell·FileRunner·Debugger에게는 Facade(단순한 `run()` 창구)로, 내부적으로는 Assembler → Checker → Executor 순서의 Pipeline으로 동작한다.
>
> | 역할 | 이 프로젝트에서 |
> |------|----------------|
> | Facade | `Pipeline.run(source)` — 3단계를 단일 진입점으로 감춤 |
> | Pipeline 단계 | `Assembler.assemble()` → `Checker.check()` → `Executor.run()` |
> | Facade 클라이언트 | `Shell`, `FileRunner`, `Debugger` — 내부 단계 몰라도 됨 |
> | 서브시스템 접근 | `pipeline.executor()` 등 게터로 필요 시 직접 접근 허용 |

**참고 위치**

| 구분 | 위치 |
|------|------|
| Pipeline 클래스 | `shell/Pipeline.java` |
| PR | [PR #13](https://github.com/kwonsunbin/CodeFabInterpreter/pull/13) `refactor/shell` |
| 핵심 커밋 | `3372489` — "Shell에서 Pipeline 추출 — REPL 루프와 실행 책임 분리" |
| 테스트 커밋 | `ca989dd` — "Pipeline 단위 테스트 추가 — stdin 없이 실행 경로 검증" |
| 단위 테스트 | `shell/PipelineTest.java` |

**Before** — PR #13 이전: Shell이 Assembler·Checker·Executor를 직접 참조 + 실행 로직 혼재

```java
// Shell.java (커밋 3372489 이전) — REPL 루프와 실행 파이프라인이 한 클래스에 혼재
public class Shell {

    private final Assembler assembler;  // 3개 컴포넌트를 직접 보유
    private final Checker   checker;
    private final Executor  executor;
    private final Logger    log;

    public Shell(Assembler assembler, Checker checker, Executor executor,
                 Logger log, InputStream in, PrintStream out) { ... }

    // REPL 루프 + 파이프라인 실행이 같은 클래스에 공존
    public void run() {
        SubmissionBuffer buf = new SubmissionBuffer();
        while (true) {
            out.print(buf.isEmpty() ? ">>> " : "... ");
            String line = reader.readLine();
            buf.append(line);
            if (buf.isComplete()) {
                runPipeline(buf.text());  // ← 실행 로직도 Shell이 직접 처리
                buf.clear();
            }
        }
    }

    // 실행 책임이 Shell 안에 있음 → FileRunner 재사용 불가, 테스트 어려움
    private void runPipeline(String source) {
        List<Stmt> program = assembler.assemble(source);
        CheckResult cr = checker.check(program);
        if (!cr.ok()) { printDiagnostics(cr.errors); return; }
        executor.run(program);
    }
}
// 문제: Shell 테스트 시 stdin 필요, FileRunner와 실행 로직 중복
```

**After** — Pipeline 분리: Shell·FileRunner·Debugger가 단일 인터페이스 공유

```java
// Pipeline.java — 3단계 협주, 외부에는 단일 run() 인터페이스
public class Pipeline {
    private final Assembler  assembler;
    private final Checker    checker;
    private final Executor   executor;
    private final Logger     log;
    private final PrintStream out;

    public void run(String source) { ... }   // ← 모든 모드가 이것만 호출
    public Assembler assembler() { return assembler; }
    public Checker   checker()   { return checker; }
    public Executor  executor()  { return executor; }
}

// Shell.java — REPL 루프만 담당, 실행은 Pipeline에 위임
public class Shell {
    private final Pipeline pipeline;

    public void run() {
        while (true) {
            String source = readSubmission();
            pipeline.run(source);  // 내부 구현 모름
        }
    }
}

// FileRunner.java — 파일 실행 모드, 동일 Pipeline 재사용
public class FileRunner {
    private final Pipeline pipeline;

    public void run(String filePath) {
        String source = Files.readString(Path.of(filePath));
        pipeline.run(source);
    }
}
```

**BP 포인트**

- **단일 책임**: Shell은 REPL I/O, FileRunner는 파일 읽기, Pipeline은 실행 — 각자의 관심사만
- **재사용성**: Shell·FileRunner·Debugger가 동일 Pipeline을 공유 → 실행 로직 중복 제거
- **테스트 용이**: `PipelineTest`가 stdin 없이 Pipeline만 독립 테스트 가능

**리뷰 포인트 요약 (PR #13)**

| 항목 | Before | After |
|------|--------|-------|
| 실행 로직 위치 | `Shell.runPipeline()` 내부 | `Pipeline.run()` 독립 클래스 |
| FileRunner 재사용 | 불가 (Shell에 종속) | `Pipeline` 공유로 재사용 |
| Pipeline 단위 테스트 | 불가 (stdin 필요) | `PipelineTest`로 독립 테스트 가능 |
| 생성자 인자 | `Shell(Assembler, Checker, Executor, Logger, ...)` 5인자 | `Shell(Pipeline, ...)` 1인자 |

---

## 2. TDD 사례

### 2.1 Hand AST 기반 유닛 테스트

**참고 위치**

| 구분 | 위치 |
|------|------|
| 테스트 파일 | `executor/ExecutorTest.java` — 150건 |
| Red 커밋 | `ec304f3` — "test(executor): 함수·배열 기능 Unit Test 추가 (손 AST, Checker 우회)" |
| Green 커밋 | `f34c674` — "feat(executor): 함수 선언/호출·배열 리터럴/인덱싱 실행 구현" |
| 관련 PR | PR #36 (`feat/executor-function-array`) |

**Before** — Red 단계: Executor 미구현, 테스트가 먼저 존재

```java
// ExecutorTest.java (커밋 ec304f3, Red 단계)
// 주석에 "현재 모든 visitor 메서드가 UnsupportedOperationException을 던짐" 명시
/**
 * Red-phase hand-AST tests for Executor.
 * 현재 모든 visitor 메서드가 UnsupportedOperationException 을 던지므로
 * stringify_* 를 제외한 전체 테스트가 Red 상태다.
 */
class ExecutorTest {

    @Test void functionCallReturnsValue() {
        // Func add(x, y) { return x + y; }  — 손으로 AST 구성
        var funcDecl = new Stmt.Function(...);
        var call     = new Expr.Call(...);
        // → visitCall() 미구현 → UnsupportedOperationException → Red
        assertEquals("7", exec(List.of(funcDecl, new Stmt.Print(call))));
    }

    @Test void arrayDeclAndGet() {
        var decl = new Stmt.ArrayDecl(nameTok("arr"), num(3.0));
        var get  = new Expr.ArrayGet(nameTok("arr"), num(0.0));
        // → visitArrayDecl() 미구현 → UnsupportedOperationException → Red
        assertEquals("0.0", exec(List.of(decl, new Stmt.Print(get))));
    }
}
```

**After** — Green 단계: Executor 구현 후 동일 테스트 통과

```java
// Executor.java (커밋 f34c674, Green 단계)
@Override
public Void visitFunction(Stmt.Function stmt) {
    CodeFabFunction function = new CodeFabFunction(stmt, environment);
    environment.define(stmt.name.origin(), function);
    return null;
}

@Override
public Object visitCall(Expr.Call expr) {
    Object callee = evaluate(expr.callee);
    if (!(callee instanceof CodeFabCallable fn))
        throw new RuntimeError(expr.paren.line(), "Can only call functions.");
    List<Object> args = expr.arguments.stream().map(this::evaluate).toList();
    return fn.call(this, args);
}

// ExecutorTest.java — 동일 테스트, 이제 Green
@Test void functionCallReturnsValue() {
    ...
    assertEquals("7", exec(List.of(funcDecl, new Stmt.Print(call))));  // Green
}
```

**테스트 헬퍼 구조**

```java
// 파서 없이 AST 노드 직접 생성하는 헬퍼
private Expr num(double v)   { return Expr.builder().literalValue(v).build(); }
private Expr str(String s)   { return Expr.builder().literalValue(s).build(); }
private Token tok(TokenType t, String o) { return new Token(t, o, null, 1); }

// stdout 캡처 헬퍼
private String exec(List<Stmt> stmts) {
    var out = new ByteArrayOutputStream();
    new Executor(new Logger(false), new Environment())
            .run(stmts, new PrintStream(out));
    return out.toString().strip();
}
```

**BP 포인트**

- **Red-Green 커밋 분리**: 테스트 추가(Red)와 구현(Green)이 별도 커밋 → TDD 흐름이 히스토리에 기록
- **의존성 격리**: Lexer·Parser 구현 완료 전에 Executor 테스트 작성 가능 → 팀 병렬 개발 지원
- **실패 원인 명확**: 테스트 실패 시 Executor 로직만 의심 (Lexer/Parser 영향 없음)

---

### 2.2 E2E + REPL 상태 공유 테스트

**참고 위치**

| 구분 | 위치 |
|------|------|
| 테스트 파일 | `EndToEndTest.java` — 70건+ |
| 초기 도입 PR | PR #12 (`test/EndToEnd`) — 커밋 `917f619`, `7b91cfe` |
| 심화 추가 | 커밋 `a6103d4` (0 나누기), `b402fd0` (타입 오류 메시지) |

**Before** — E2E 테스트 없이 유닛 테스트만 존재하던 시기의 문제

```java
// PR #12 이전: Executor 단위 테스트만 존재
// → 파이프라인 전체를 통과한 통합 오류를 감지하지 못함
// 예) Lexer가 연산자를 잘못 토크나이즈해도 ExecutorTest는 통과
// 예) Checker가 잘못된 depth를 계산해도 ExecutorTest는 손 AST라 영향 없음

// 실제로 발생한 버그 (커밋 ad07358):
// Parser comparison()에 EQUAL_EQUAL, BANG_EQUAL 누락 → E2E에서만 포착
// "if (a == b)" 파싱 실패를 유닛 테스트로는 잡을 수 없었음
```

**After** — 격리 실행 + REPL 세션 시뮬레이션 두 가지 전략

```java
// EndToEndTest.java
class EndToEndTest {

    // ① 격리 실행: 기능 테스트 — 매번 새 인스턴스
    private Result run(String source) {
        var outBuf = new ByteArrayOutputStream();
        var assembler = new Assembler();
        var global    = new Environment();
        var checker   = new Checker(global);
        var executor  = new Executor(new Logger(false), global);
        // 전체 파이프라인 실행, stdout/에러 캡처
        return new Result(outBuf.toString(), checkResult, runtimeError);
    }

    // ② REPL 세션: 상태 누적 테스트 — 동일 인스턴스 유지
    private final Environment    sessionEnv      = new Environment();
    private final Checker        sessionChecker  = new Checker(sessionEnv);
    private final Executor       sessionExecutor = new Executor(new Logger(false), sessionEnv);

    private String runSession(String... sources) {
        var out = new StringBuilder();
        for (String src : sources) {
            List<Stmt> program = new Assembler().assemble(src);
            sessionChecker.check(program);
            sessionExecutor.run(program);
        }
        return out.toString();
    }

    // ── 테스트 예시 ─────────────────────────────────────────────────
    @Test void runtimeErrorOnDivisionByZero() {
        var result = run("print 10 / 0;");
        assertTrue(result.hasRuntimeError());
        assertTrue(result.runtimeError().contains("0"));
    }

    @Test void duplicateGlobalDetected() {
        // 첫 번째 submission: 전역 선언
        sessionChecker.check(new Assembler().assemble("var g = 1;"));
        // 두 번째 submission: 중복 감지
        var result = sessionChecker.check(new Assembler().assemble("var g = 2;"));
        assertFalse(result.ok());
        assertTrue(result.errors.get(0).message().contains("already declared"));
    }
}
```

**BP 포인트**

- **두 격리 전략의 용도 구분**: `run()` — 기능 독립 검증, `runSession()` — REPL 누적 상태 검증
- **회귀 방지**: PR #31의 Environment 통합 리팩터 후 70건 E2E가 즉시 회귀를 잡음
- **실제 파이프라인 오류 포착**: `EQUAL_EQUAL` 파싱 누락(커밋 `ad07358`)처럼 유닛 테스트로 잡을 수 없는 버그를 조기 발견

---

### 2.3 폭탄식(Bomb) 최적화 검증 테스트

**참고 위치**

| 구분 | 위치 |
|------|------|
| 테스트 파일 | `executor/OptimizationIntegrationTest.java` |
| 관련 커밋 | `40211d7` — "feat(executor): Checker 최적화를 Executor에 연동" |
| 관련 PR | PR #31 (`feat/executor-extension`) |

**Before** — 최적화 연동 없이 Executor가 항상 전체 평가

```java
// Executor.java (커밋 40211d7 이전)
@Override
public Object visitBinary(Expr.Binary expr) {
    // foldedValue 체크 없음 → 항상 자식 평가
    Object left  = evaluate(expr.left);
    Object right = evaluate(expr.right);
    // ...
}

// Checker가 foldedValue를 계산해도 Executor가 무시 → 최적화 사실상 무효
// 런타임마다 1 + 2를 다시 계산, Checker의 사전 계산이 낭비
```

**After** — Checker 메타데이터 연동 + 폭탄식 테스트로 동작 증명

```java
// Executor.java (커밋 40211d7 이후)
@Override
public Object visitBinary(Expr.Binary expr) {
    if (expr.foldedValue != null) return expr.foldedValue;  // O(1) 단락
    Object left  = evaluate(expr.left);   // 폴딩 없을 때만 실제 평가
    Object right = evaluate(expr.right);
    // ...
}

@Override
public Object visitVariable(Expr.Variable expr) {
    if (expr.depth >= 0) return environment.getAt(expr.depth, expr.name.origin()); // O(1)
    return environment.get(expr.name.origin());  // O(N) 폴백
}

// OptimizationIntegrationTest.java — 폭탄식으로 최적화 경로 강제 검증
class OptimizationIntegrationTest {

    // 평가되면 즉시 RuntimeError를 내는 폭탄 노드
    private Expr bomb() {
        return new Expr.Variable(new Token(IDENTIFIER, "UNDEFINED_BOOM", null, 1));
    }

    @Test void folding_binary_usesFoldedValue_andSkipsChildren() {
        var b = new Expr.Binary(bomb(), tok(PLUS, "+"), bomb());
        b.foldedValue = 42.0;  // Checker가 계산한 값을 직접 주입
        // bomb()이 평가되지 않아야 함 → 예외 없이 "42" 반환 = 최적화 경로 사용 증명
        assertEquals("42", exec(List.of(new Stmt.Print(b))));
    }

    @Test void folding_absent_fallsBackToEvaluation() {
        var b = new Expr.Binary(num(1.0), tok(PLUS, "+"), num(2.0));
        // foldedValue 없음 → 정상 평가 경로도 동작 확인
        assertEquals("3", exec(List.of(new Stmt.Print(b))));
    }

    @Test void staticBinding_usesDepth_forVariableLookup() {
        var decl = new Stmt.Var(tok(IDENTIFIER, "x"), num(99.0));
        var ref  = Expr.builder().name(tok(IDENTIFIER, "x")).build();
        ref.depth = 0;  // Checker가 계산한 스코프 거리 직접 주입
        assertEquals("99", exec(List.of(decl, new Stmt.Print(ref))));
    }
}
```

**BP 포인트**

- **폭탄식의 의미**: 최적화 경로를 타지 않으면 `UNDEFINED_BOOM` 평가 → RuntimeError → 테스트 실패 → 최적화 우회 불가
- **메타데이터 실사용 증명**: Checker의 `foldedValue`, `depth` 필드가 Executor에서 실제로 사용됨을 보증
- **두 경로 모두 검증**: 최적화 있을 때(폭탄식) + 없을 때(폴백) 모두 테스트

---

### 2.4 브랜치 커버리지 목표 관리

**커버리지 변화 요약**

| 대상 | 커밋 | 테스트 수 | BRANCH (전 → 후) | LINE (전 → 후) |
|------|------|-----------|-----------------|----------------|
| LexerTest | [`d26eb51`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/d26eb51) | 13 → 21 (+8) | 86.2% → **100%** | 100% → 100% |
| ParserTest | [`8bce85a`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/8bce85a) | 28 → 34 (+6) | 91.9% → **98.4%** | 98.8% → **100%** |
| CheckerTest | [`1d348a4`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/1d348a4) | 8 → 14 (+6) | 72.7% → **81.8%** | 89.9% → **100%** |
| ExecutorTest | [`fb6dbbf`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/fb6dbbf) · [`e1d8b86`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/e1d8b86) | 37 → 47 (+10) | 68.5% → **87.7%** | 90.4% → **100%** |

**각 테스트별 타격 대상**

- **LexerTest**: LINE은 이미 100%(EndToEndTest 기여) — `number()` 정수/소수 분기, `string()` 줄바꿈 분기, `isAlpha()` 대문자·밑줄·범위 초과 분기 등 경계 케이스 8개로 BRANCH 100% 달성
- **ParserTest**: `varDeclaration()` 초기화 생략, `forStatement()` 표현식 초기화·조건 생략, `block()` 미닫힌 블록 등 6개로 BRANCH 98.4% / LINE 100% 달성
- **CheckerTest**: `visitIf` / `visitLogical` / `visitUnary` / `visitGrouping` / `visitAssign` 등 미방문 visitor 분기 6개로 LINE 100% 달성
- **ExecutorTest**: `visitUnary` / `visitBinary` / `visitComparison` default·경계 분기 + `checkNumberOperands` throw 경로를 2개 커밋으로 나눠 LINE 100% 달성

**BP 포인트**

- **수치 기반 목표**: 커밋 메시지와 테스트 주석에 커버 대상 줄·분기를 명시 → 회귀 시 즉시 추적
- **분기 주석 패턴**: 각 테스트에 커버하는 분기/줄번호를 주석으로 기록 → 나중에 읽어도 의도 명확
- **LINE vs BRANCH 구분**: Lexer는 LINE이 이미 100%였고(EndToEndTests 기여), BRANCH만 미달 → 새 테스트가 특정 분기만 정밀 타격
- **점진적 달성**: Lexer(BRANCH 86.2%→100%) → Parser(BRANCH 91.9%→98.4%) → Checker(LINE 89.9%→100%) → Executor(LINE 90.4%→100%) 순서로 파일별 목표를 별도 커밋으로 분리

---

### 2.5 Mock → 실제 Lexer 전환

**참고 위치**

| 구분 | 위치 |
|------|------|
| 전환 커밋 | [`168d8a0`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/168d8a0) — "테스트 코드 Mock 사용하는 부분 실제 Lexer 사용하도록 변경" |
| 최종 정리 | [`822962f`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/822962f) — "Parser 및 ParserTest 리팩토링" |
| 영향 파일 | `assembler/ParserTest.java` |

**Before** — `@Mock Lexer` + `thenReturn(토큰 목록)` 직접 주입

`"print 1 + 2 * 3;"` 한 줄을 테스트하려면 `Token` 객체 8개를 직접 나열해야 했다.  
`parse(src)`의 `src`는 실제로 Lexer에 전달되지 않아 토큰 목록과 불일치해도 테스트가 통과했다.

```java
@ExtendWith(MockitoExtension.class)
class ParserTest {
    @Mock Lexer lexer;

    private List<Stmt> parse(String src) {
        return new Assembler(lexer).assemble(src);  // src는 무시됨
    }

    @Test void multiplicationBeforeAddition() {
        Mockito.when(lexer.scanTokens()).thenReturn(List.of(
                new Token(TokenType.PRINT,     "print", null, 1),
                new Token(TokenType.NUMBER,    "1",     1.0,  1),
                new Token(TokenType.PLUS,      "+",     null, 1),
                new Token(TokenType.NUMBER,    "2",     2.0,  1),
                new Token(TokenType.STAR,      "*",     null, 1),
                new Token(TokenType.NUMBER,    "3",     3.0,  1),
                new Token(TokenType.SEMICOLON, ";",     null, 1),
                new Token(TokenType.EOF,       "",      null, 1)
        ));
        var outer = (Expr.Binary) getOuterStatementOfSelectedStatement("print 1 + 2 * 3;", 0);
    }
}
```

**After** — 소스 문자열 한 줄, Mockito 의존성 완전 제거

```java
class ParserTest {
    private List<Stmt> parse(String src) {
        return new Assembler().assemble(src);  // 실제 Lexer 사용
    }

    @Test void multiplicationBeforeAddition() {
        var outer = (Expr.Binary) printExpr("print 1 + 2 * 3;");
        assertEquals(TokenType.PLUS, outer.op.type());
    }
}
```

**BP 포인트**

- **`src` 불일치 문제 해소**: Mock 단계에서는 `src`와 토큰 목록이 달라도 테스트가 통과했음 → 전환 후 강제 일치
- **격리 비용 > 이점**: Lexer 안정화 이후 Mock 유지 비용이 이점을 초과 → `for`·`if` 테스트가 비활성 방치됐다가 전환 즉시 활성화 (9건 → 11건)
- **Lexer-Parser 연동 오류 포착**: `EQUAL_EQUAL` 누락(PR #20)처럼 Mock으로는 잡을 수 없는 연동 버그를 실제 Lexer 테스트가 포착

---

### 2.6 테스트 헬퍼 메서드 변천

**참고 위치**

| 단계 | 커밋 | 헬퍼 시그니처 | 해결한 한계 |
|------|------|--------------|------------|
| 1단계 | [`644b4fd`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/644b4fd) | `getOuterStatementOfFirstStatement(src)` | 3줄 반복 → 헬퍼 추출, 반환 타입 `Expr.Binary` 고정 |
| 2단계 | [`5703a06`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/5703a06) | `getOuterStatementOfFirstStatement(src)` | 반환 타입 `Expr`로 완화 (다형성) |
| 3단계 | [`cb19583`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/cb19583) | `getOuterStatementOfSelectedStatement(src, i)` | 첫 번째 구문 고정 → 인덱스 선택 |
| 4단계 | [`adaf9af`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/adaf9af) | `getStatement(src, i, Class<T>)` | `Stmt.Print` 고정 → 모든 Stmt 타입 제네릭 |
| 5단계 | [`822962f`](https://github.com/kwonsunbin/CodeFabInterpreter/commit/822962f) | `printExpr(src)` 단축 헬퍼 추가 | 최빈 패턴 1줄 단축, Mockito import 완전 제거 |

**헬퍼 구현 변화**

```java
// 1단계: 644b4fd — 반환 타입 Expr.Binary 고정
private Expr.Binary getOuterStatementOfFirstStatement(String src) {
    return (Expr.Binary) ((Stmt.Print) parse(src).getFirst()).expression;
}

// 4단계: adaf9af — 제네릭으로 모든 Stmt 타입 지원
private <T extends Stmt> T getStatement(String src, int i, Class<T> type) {
    return type.cast(parse(src).get(i));
}

// 5단계: 822962f — 최빈 패턴 단축
private Expr printExpr(String src) {
    return getStatement(src, 0, Stmt.Print.class).expression;
}
```

**호출 측 변화 (같은 테스트 기준)**

```java
// 1단계: getOuterStatementOfFirstStatement("print 1 + 2 * 3;")
// 3단계: (Expr.Binary) getOuterStatementOfSelectedStatement("print 1 + 2 * 3;", 0)
// 4단계: (Expr.Binary) getStatement("print 1 + 2 * 3;", 0, Stmt.Print.class).expression
// 5단계: (Expr.Binary) printExpr("print 1 + 2 * 3;")
```

**BP 포인트**

- **단계적 추상화**: 각 단계가 이전 헬퍼의 가장 큰 한계(타입 고정 → 인덱스 고정 → Stmt 타입 고정) 하나씩 해결
- **설계 방향**: 헬퍼가 구체적인 타입 가정을 제거할수록 테스트가 "무엇을 검증하는가"에만 집중 가능

---

## 3. 코드리뷰 사례

### 3.1 PR #31 — Environment 단일화

**참고 위치**

| 구분 | 위치 |
|------|------|
| PR | [PR #31](https://github.com/kwonsunbin/CodeFabInterpreter/pull/31) |
| 핵심 커밋 | `f2a06b5` — "Scope/Environment를 단일 Environment로 통합" |
| 리뷰 반영 커밋 | `54ba976` — "정적 바인딩을 슬롯 인덱스 기반으로 전환 (PR #31 리뷰 반영)" |
| 영향 파일 | `Checker.java`, `Executor.java`, `Environment.java`, `Main.java` |

**Before** — Checker의 `Scope`와 Executor의 `Environment`가 각자 독립 관리

```java
// Checker.java (커밋 f2a06b5 이전) — 자체 Scope 스택 보유
public class Checker implements Stmt.Visitor<Void> {

    private final Scope globalScope = new Scope();        // ← Checker 전용
    private final Deque<Scope> scopes = new ArrayDeque<>();

    private void beginScope() { scopes.push(new Scope()); }
    private void endScope()   { scopes.pop(); }

    private void declare(Token name) {
        Scope scope = scopes.peek();
        scope.declare(name.origin());
    }
}

// Executor.java (동일 시점) — 별도 Environment 보유
public class Executor implements Stmt.Visitor<Void>, Expr.Visitor<Object> {
    private final Environment globalEnv = new Environment();  // ← Executor 전용
    private Environment environment = globalEnv;

    // Checker가 계산한 depth 정보를 활용할 방법 없음
    // → 매번 O(N) 선형 탐색으로 변수 조회
    public Object visitVariable(Expr.Variable expr) {
        return environment.get(expr.name.origin());  // O(N) 항상
    }
}
// 문제: 스코프 구조가 두 곳에 중복 → 깊이 불일치 버그 잠재
//        Checker의 최적화(depth) 메타데이터를 Executor가 활용 불가
```

**After** — 단일 Environment를 Checker·Executor가 공유

```java
// Environment.java — Checker + Executor 공동 사용
public class Environment {
    private final List<Map<String, Object>> scopes = new ArrayList<>();

    // Checker가 사용
    public void declare(String name) { ... }
    public void define(String name, Object value) { ... }
    public int  distanceOf(String name) { ... }  // depth 계산

    // Executor가 사용
    public Object get(String name)                    { ... }  // O(N) 폴백
    public Object getAt(int depth, String name)       { ... }  // O(1) 직접 접근
    public void   assign(String name, Object value)   { ... }
}

// Main.java — 단일 Environment를 두 곳에 주입
Environment global  = new Environment();
Checker   checker   = new Checker(global);    // 같은 인스턴스
Executor  executor  = new Executor(log, global);  // 같은 인스턴스

// Executor.java — depth 메타데이터 활용 가능
public Object visitVariable(Expr.Variable expr) {
    if (expr.depth >= 0) return environment.getAt(expr.depth, expr.name.origin()); // O(1)
    return environment.get(expr.name.origin());  // O(N) 폴백
}
```

**BP 포인트**

- **단일 진실 공급처**: 스코프 구조가 Checker와 Executor에서 동일 → 깊이 불일치 버그 근절
- **최적화 연동 가능**: 공유 Environment를 통해 Checker의 `depth` 메타데이터를 Executor가 활용 → O(N) → O(1)
- **리팩터 규모**: Scope 클래스 삭제, 코드 약 99줄 감소, 308개 테스트 전부 통과
- **리뷰 피드백 추적**: 커밋 메시지에 "PR #31 리뷰 반영" 명시 → 리뷰 → 수정 연결이 히스토리에 기록

---

### 3.2 PR #16 — SRP 적용 (책임 분리)

**참고 위치**

| 구분 | 위치 |
|------|------|
| PR | [PR #16](https://github.com/kwonsunbin/CodeFabInterpreter/pull/16) |
| 핵심 커밋 | `8f25b28` — "SRP 적용 — Scope 관리 책임을 ScopeStack으로 분리" |
| 관련 커밋 | `f3c0893` — "OCP 적용 — SemanticRule 인터페이스로 규칙 외부화" |
| 관련 커밋 | `8269186` — "Checker에서 Expr.Visitor 구현 제거 — scanExpr()로 통합" |

**Before** — Checker가 `Expr.Visitor` 구현 + Scope 관리 + 의미 검사 모두 담당

```java
// Checker.java (커밋 8f25b28 이전)
// implements Stmt.Visitor<Void>, Expr.Visitor<Void>  ← Expr.Visitor까지 구현
public class Checker implements Stmt.Visitor<Void>, Expr.Visitor<Void> {

    // ① Scope 관리 자료구조가 Checker 내부에 직접 존재
    private final Scope globalScope = new Scope();
    private final Deque<Scope> scopes = new ArrayDeque<>();

    private void beginScope() { scopes.push(new Scope()); }  // ← 관리 책임
    private void endScope()   { scopes.pop(); }

    // ② Expr.Visitor 구현 (visitBinary, visitLiteral 등 모두)
    @Override public Void visitBinary(Expr.Binary expr) { ... }
    @Override public Void visitLiteral(Expr.Literal expr) { return null; }
    // ... 각 Expr 타입마다 visit 메서드

    // ③ 의미 검사 규칙
    @Override public Void visitVar(Stmt.Var stmt) { declare(stmt.name); ... }
}
// 문제: Checker 변경 이유가 3가지 (스코프 구조, Expr 방문 방식, 검사 규칙)
```

**After** — 각 책임을 분리

```java
// 커밋 8f25b28: Scope 관리 → ScopeStack으로 분리
class ScopeStack {
    private final Deque<Map<String, Boolean>> stack = new ArrayDeque<>();
    public void beginScope() { stack.push(new HashMap<>()); }
    public void endScope()   { stack.pop(); }
    public boolean isDeclared(String name) { ... }
    public int distanceTo(String name) { ... }
}

// 커밋 8269186: Expr.Visitor 구현 제거 → scanExpr() 내부 switch로 통합
// implements Stmt.Visitor<Void>만 남김 (Expr.Visitor 제거)
public class Checker implements Stmt.Visitor<Void> {  // ← Expr.Visitor 제거

    private Object scanExpr(Expr expr) {
        return switch (expr) {       // sealed switch로 처리
            case Expr.Literal l   -> l.value;
            case Expr.Binary b    -> { ... yield result; }
            // ...
        };
    }
}
// 커밋 f3c0893: SemanticRule 인터페이스로 규칙 외부화
// 새 검사 규칙 추가 시 Checker 수정 없이 구현체만 추가 가능
```

**BP 포인트**

- **SRP 적용 근거**: 변경 이유가 3가지(스코프 구조 변경, Expr 방문 방식, 검사 규칙)였던 Checker를 각각 단독 변경 가능하도록 분리
- **이후 통합의 토대**: PR #31에서 ScopeStack이 Environment로 흡수 — 이 리팩터가 없었다면 통합 범위가 훨씬 컸을 것
- **Expr.Visitor 제거 효과**: sealed switch가 완전성을 보장하므로 visitXxx 메서드 오버라이드 없이도 모든 케이스 처리

---

### 3.3 PR #14 — Logger DI 도입

**참고 위치**

| 구분 | 위치 |
|------|------|
| PR | [PR #14](https://github.com/kwonsunbin/CodeFabInterpreter/pull/14) `refactor/logger` |
| 핵심 커밋 | `cb3f388` — "Logger 출력 스트림 의존성 주입 — System.err 하드코딩 제거" |
| 테스트 커밋 | `2be773f` — "Logger 단위 테스트 추가 — 출력/verbose 게이팅 검증" |

**Before** — `System.err` 하드코딩으로 테스트 불가

```java
// Logger.java (커밋 cb3f388 이전)
public class Logger {
    private boolean verbose;

    public Logger(boolean verbose) { this.verbose = verbose; }

    private void err(String msg) {
        System.err.println(msg);  // ← 하드코딩 → 테스트에서 캡처 불가
    }

    public void error(CodeFabError e) { err(e.userMessage()); }
    public void executionStart()      { if (verbose) err("[exec] start"); }
}
// 테스트 시 System.err를 가로채야 하는데 전역 상태 조작이라 위험
// → "verbose=false일 때 출력이 없다"를 검증하는 테스트 작성 불가
```

**After** — PrintStream DI → 테스트용 스트림 주입 가능

```java
// Logger.java (커밋 cb3f388 이후)
public class Logger {
    private final boolean     verbose;
    private final PrintStream err;   // ← DI로 변경

    public Logger(boolean verbose) { this(verbose, System.err); }  // 프로덕션용
    public Logger(boolean verbose, PrintStream err) {               // 테스트용
        this.verbose = verbose;
        this.err     = err;
    }

    private void err(String msg) { err.println(msg); }  // 주입된 스트림 사용
}

// LoggerTest.java (커밋 2be773f) — 이제 테스트 가능
@Test void verbose_false_suppressesLifecycleLogs() {
    var buf = new ByteArrayOutputStream();
    var log = new Logger(false, new PrintStream(buf));
    log.lifecycle("should not appear");
    assertEquals("", buf.toString().strip());  // 출력 없음 검증 가능
}

@Test void verbose_true_emitsLifecycleLogs() {
    var buf = new ByteArrayOutputStream();
    var log = new Logger(true, new PrintStream(buf));
    log.lifecycle("hello");
    assertTrue(buf.toString().contains("hello"));
}
```

**BP 포인트**

- **테스트 불가 → 설계 개선 신호**: `System.err` 하드코딩이 테스트 작성을 막음 → DI 도입의 직접적 계기
- **전역 상태 제거**: `System.err` 전역 조작 없이 각 테스트가 독립된 스트림 사용 → 테스트 간 간섭 없음
- **하위 호환**: 기본 생성자 `Logger(verbose)`는 유지 → 기존 코드 무수정

---

## 부록: 파일별 Quick Reference

```
src/main/java/org/example/codefab/
├── assembler/
│   ├── Assembler.java          — Facade (14줄, 3단계 진입점)
│   ├── Lexer.java              — 토큰화, KEYWORDS Map
│   └── Parser.java             — 재귀하강 파서, leftAssoc(), varDeclaration()
├── ast/
│   ├── Expr.java               — sealed class, Visitor<R>, Builder
│   └── Stmt.java               — sealed class, Visitor<R>
├── checker/
│   ├── Checker.java            — scanExpr() sealed switch, 정적 바인딩 depth 계산
│   ├── CheckResult.java        — errors/warnings 분리
│   └── Diagnostic.java         — record (4줄)
├── executor/
│   ├── Executor.java           — visitBinary(폴딩), visitVariable(depth), visitCall
│   ├── Environment.java        — 공유 스코프, getAt(O(1)), distanceOf()
│   ├── ExecutionListener.java  — Observer 인터페이스
│   ├── CodeFabCallable.java    — 함수 호출 추상화
│   ├── CodeFabFunction.java    — 클로저 구현
│   └── ReturnException.java    — 함수 반환 스택 언와인딩
├── error/
│   ├── CodeFabError.java       — Strategy 기반 에러 계층
│   ├── LexError.java
│   ├── ParseError.java
│   └── RuntimeError.java
├── shell/
│   ├── Pipeline.java           — 3단계 협주 Facade
│   ├── Shell.java              — REPL
│   ├── FileRunner.java         — 파일 실행 모드
│   └── debug/
│       ├── Debugger.java       — Observer 구현, commandLoop(), breakpoint 관리
│       └── LineExtractor.java  — Visitor 기반 줄번호 추출 (singleton)
└── log/
    └── Logger.java             — verbose 게이팅, DI 지원

src/test/java/org/example/codefab/
├── EndToEndTest.java                       — 70건+, run() / runSession() 격리 전략
├── assembler/
│   ├── ParserTest.java                     — 아키텍처 규칙 테스트 포함
│   └── LexerTest.java                      — 100% 브랜치 커버리지
├── checker/CheckerTest.java                — 스코프·의미 규칙 검사
├── executor/
│   ├── ExecutorTest.java                   — 150건 Hand AST
│   └── OptimizationIntegrationTest.java    — 폭탄식 최적화 검증 11건
└── shell/
    ├── PipelineTest.java                   — stdin 없이 Pipeline 독립 테스트
    └── SubmissionBufferTest.java           — REPL 입력 누적·완결 판정 테스트
```
