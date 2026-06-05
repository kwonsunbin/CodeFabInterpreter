# 05. 정적 분석 — Checker

## 역할

`Checker`는 실행 전에 AST를 순회하며 **실행 없이 감지할 수 있는 의미 오류**를 수집합니다. 구현은 `Stmt.Visitor<Void>`를 사용한 DFS(깊이 우선 탐색)입니다.

```java
// Checker.java:25
public class Checker implements Stmt.Visitor<Void> { ... }
```

> Visitor 패턴 전체 설명: [04-ast-visitor.md](04-ast-visitor.md)

---

## 스코프 관리 — Scope와 Deque

### Scope 클래스 (`checker/Scope.java:11-22`)

하나의 렉시컬 블록(함수, for, 중괄호 블록 등)에 해당하는 변수 테이블입니다.

```java
class Scope {
    enum State { DECLARING, DEFINED }

    private final Map<String, State> names = new HashMap<>();

    boolean has(String name)   { return names.containsKey(name); }
    State   state(String name) { return names.get(name); }
    void declare(String name)  { names.put(name, State.DECLARING); }
    void define(String name)   { names.put(name, State.DEFINED); }
}
```

**DECLARING / DEFINED 2단계 상태:**

```
변수 선언 처리 순서:
  declare(name)  →  State.DECLARING   ← 초기화식 평가 중
  scanExpr(initializer)               ← 초기화식 검사 (이 시점에 같은 이름 읽으면 Rule 2)
  define(name)   →  State.DEFINED     ← 사용 가능 상태
```

### Deque<Scope> 스코프 스택 (`Checker.java:30`)

```java
private final Scope globalScope = new Scope();
private final Deque<Scope> scopes = new ArrayDeque<>();
```

- `scopes.push(new Scope())` → 새 블록 진입 (`beginScope()`, line 51)
- `scopes.pop()` → 블록 이탈 (`endScope()`, line 55)
- **전역 스코프는 영구 유지**: REPL 세션 전체에서 선언한 전역 변수가 유지됩니다

### 가시성 판단 — `isVisible()` (Checker.java:82-87)

```java
private boolean isVisible(String name) {
    for (Scope scope : scopes) {             // 안쪽 스코프 → 전역 순서로 탐색
        if (scope.has(name) && scope.state(name) == Scope.State.DEFINED)
            return true;
    }
    return false;
}
```

`DECLARING` 상태는 가시성에서 제외됩니다. 이것이 Rule 2(자기 참조 방지)를 구현합니다.

---

## 4가지 시맨틱 규칙

### Rule 1: 같은 블록에서 중복 선언 (Checker.java:64-68)

```java
private void declare(Token name) {
    Scope scope = scopes.peek();
    if (scope.has(name.origin())) {       // 현재 스코프(블록)에 이미 있으면
        result.addError(name.line(), "Already a variable with this name in this scope.");
        return;
    }
    scope.declare(name.origin());
}
```

**감지 예시:**
```codefab
var x = 1;
var x = 2;   // [check:error] line 2: Already a variable with this name in this scope.
```

### Rule 2: 초기화식에서 자기 참조 (Checker.java:147-151)

```java
case Expr.Variable v -> {
    String name = v.name.origin();
    Scope current = scopes.peek();
    if (current.has(name) && current.state(name) == Scope.State.DECLARING) {
        result.addError(v.name.line(), "Can't read local variable in initializer.");
        return;
    }
    // ...
}
```

**감지 예시:**
```codefab
var a = a + 1;   // [check:error] line 1: Can't read local variable in initializer.
```

DECLARING 상태에서 같은 이름을 읽으려 하면 잡힙니다.

### Rule 3: 미선언 변수 읽기 (Checker.java:152-155)

```java
if (!isVisible(name)) {
    result.addError(v.name.line(), "Undefined variable '" + name + "'.");
}
```

**감지 예시:**
```codefab
print z;   // [check:error] line 1: Undefined variable 'z'.
```

블록 스코프 위반도 여기서 잡힙니다:
```codefab
{
    var inner = 5;
}
print inner;   // [check:error]: Undefined variable 'inner'.
```

### Rule 4: 미선언 변수 대입 (Checker.java:157-162)

```java
case Expr.Assign a -> {
    if (!isVisible(a.name.origin())) {
        result.addError(a.name.line(), "Undefined variable '" + a.name.origin() + "'.");
    }
    scanExpr(a.value);   // 대입 값도 계속 검사
}
```

**감지 예시:**
```codefab
unknown = 10;   // [check:error]: Undefined variable 'unknown'.
```

---

## 진단 수집 전략 — 예외 vs 수집

Checker는 오류를 **예외(`throw`)로 던지지 않고** `CheckResult`에 누적합니다.

```java
// CheckResult.java:6-15
public class CheckResult {
    public final List<Diagnostic> errors   = new ArrayList<>();
    public final List<Diagnostic> warnings = new ArrayList<>();

    public boolean ok() { return errors.isEmpty(); }

    public void addError(int line, String message) {
        errors.add(new Diagnostic(Diagnostic.Severity.ERROR, line, message));
    }
}
```

```java
// Diagnostic.java:3-10 — 불변 레코드
public record Diagnostic(Severity severity, int line, String message) {
    public enum Severity { ERROR, WARNING }

    @Override
    public String toString() {
        return "[check:" + severity.name().toLowerCase() + "] line " + line + ": " + message;
    }
}
```

**수집 방식의 장점:**
- 여러 오류가 있을 때 **모두 한 번에 보고**합니다 (컴파일러처럼)
- 오류가 하나 있어도 검사를 멈추지 않아 사용자가 수정할 곳을 모두 파악할 수 있습니다

**Lexer/Parser와의 대비:**
- `LexError` / `ParseError`는 **즉시 throw** → 첫 번째 오류에서 중단
- 이유: 렉스/파스 오류가 나면 AST 자체가 불완전해 계속 검사할 의미가 없음

---

## 실행 흐름 (DFS)

```java
// Checker.java:37-46
public CheckResult check(List<Stmt> program) {
    result = new CheckResult();
    if (scopes.isEmpty()) scopes.push(globalScope);

    for (Stmt stmt : program) {
        execute(stmt);                   // stmt.accept(this) — Visitor 더블 디스패치
    }
    return result;
}
```

구문(Stmt)별 방문자 호출 흐름:

```
visitBlock() → beginScope() → for each stmt: execute() → endScope()
visitFor()   → beginScope() → check init/cond/incr/body → endScope()
visitVar()   → declare() → scanExpr(initializer) → define()
visitIf()    → scanExpr(condition) → execute(thenBranch) → execute(elseBranch?)
visitPrint() → scanExpr(expression)
visitExpression() → scanExpr(expression)
```

---

## 타입 검사를 런타임에 미루는 이유

```java
// Checker.java (주석, line 22-23)
// Runtime errors (type mismatch, division by zero) are intentionally left
// to Executor — they require actual values and cannot be detected statically.
```

다음 코드는 정적으로 오류를 감지할 수 없습니다:

```codefab
var x = input;     // input 값에 따라 타입이 결정됨
print x + 1;       // x가 string이면 오류, number이면 정상
```

CodeFab는 동적 타입 언어이므로, 타입 검사를 정적으로 하려면 타입 추론 시스템이 필요합니다. 현재 버전은 이를 생략하고 `Executor`의 `RuntimeError`에 위임합니다.

> 런타임 오류 발생 지점 상세: [06-executor.md](06-executor.md)
