# 06. 실행 엔진 — Executor

## 역할

`Executor`는 Checker를 통과한 AST를 **직접 순회하며 평가**하는 Tree-walking 인터프리터입니다. `Stmt.Visitor<Void>`와 `Expr.Visitor<Object>` 두 Visitor를 동시에 구현합니다.

```java
// Executor.java:21
public class Executor implements Stmt.Visitor<Void>, Expr.Visitor<Object> { ... }
```

- **Stmt 방문**: 반환값 `Void` — 출력, 변수 선언 등 사이드 이펙트
- **Expr 방문**: 반환값 `Object` (`Double | String | Boolean | null`) — 값 평가

> Visitor 패턴 원리: [04-ast-visitor.md](04-ast-visitor.md)

---

## Environment — 부모 체이닝 스코프

### 구조 (`executor/Environment.java:24-79`)

```java
public class Environment {
    private final Environment enclosing;           // 부모 스코프 (전역이면 null)
    private final Map<String, Object> values = new HashMap<>();

    public Environment()                    { this.enclosing = null; }     // 전역용
    public Environment(Environment enclosing) { this.enclosing = enclosing; } // 블록용
}
```

### 변수 읽기 — 체인 탐색 (`Environment.java:41-49`)

```java
public Object get(Token name) {
    String key = name.origin();
    Object val = values.get(key);
    if (val != null || values.containsKey(key))
        return val;
    if (enclosing != null)
        return enclosing.get(name);              // 부모 스코프로 탐색 재귀
    throw new RuntimeError(name, "Undefined variable '" + key + "'.");
}
```

`values.containsKey(key)` 체크가 `null != val` 앞에 오는 이유: `null`로 초기화된 변수(`var x;`)를 "미선언"으로 오인하지 않기 위해서입니다.

### 변수 대입 — 가장 가까운 스코프에 저장 (`Environment.java:52-56`)

```java
public void assign(Token name, Object value) {
    if (values.containsKey(name.origin())) {
        values.put(name.origin(), value);
        return;
    }
    if (enclosing != null) { enclosing.assign(name, value); return; }
    throw new RuntimeError(name, "Undefined variable '" + name.origin() + "'.");
}
```

`x = 5`는 **x가 선언된 가장 안쪽 스코프**에 저장됩니다. 새 선언이 아닌 기존 변수 수정입니다.

### 스코프 체인 시각화

```
Global Env:  { a: 10 }
    │ enclosing ↑
Block Env:   { b: 20 }
    │ enclosing ↑
For Env:     { i: 3 }   ← environment (현재 포인터)
```

`i` 읽기 → For Env 확인 → 있음  
`b` 읽기 → For Env 없음 → Block Env 확인 → 있음  
`a` 읽기 → For Env 없음 → Block Env 없음 → Global Env 확인 → 있음

---

## executeBlock() — 스코프 생성/복원 (`Executor.java:183-193`)

```java
public void executeBlock(List<Stmt> statements, Environment blockEnv) {
    Environment previous = environment;
    environment = blockEnv;                            // 새 스코프로 진입
    if (listener != null) listener.onEnterScope();     // Observer 알림
    try {
        for (Stmt stmt : statements) execute(stmt);
    } finally {
        if (listener != null) listener.onExitScope();  // Observer 알림
        environment = previous;                        // 이전 스코프 복원
    }
}
```

`finally`로 예외 발생 시에도 반드시 스코프를 복원합니다. `visitBlock()`이 이 메서드를 호출합니다:

```java
// Executor.java:89-92
@Override
public Void visitBlock(Stmt.Block stmt) {
    executeBlock(stmt.statements, new Environment(environment));
    return null;
}
```

`visitFor()`도 유사하게 루프 전용 스코프를 생성합니다 (`Executor.java:64-80`).

---

## 단락 평가 (Short-circuit) — `visitLogical()` (`Executor.java:170-178`)

```java
@Override
public Object visitLogical(Expr.Logical expr) {
    Object left = evaluate(expr.left);

    if (expr.op.type() == TokenType.OR) {
        if (isTruthy(left)) return left;    // OR: 왼쪽이 참이면 오른쪽 미평가
    } else {
        if (!isTruthy(left)) return left;   // AND: 왼쪽이 거짓이면 오른쪽 미평가
    }
    return evaluate(expr.right);            // 필요할 때만 오른쪽 평가
}
```

반환값은 마지막으로 평가된 **피연산자 값**입니다 (Java의 `&&`, `||`처럼).

---

## Truthiness 규칙 (`Executor.java:203-207`)

```java
private boolean isTruthy(Object value) {
    if (value == null)              return false;   // null은 거짓
    if (value instanceof Boolean b) return b;       // Boolean은 그대로
    return true;                                    // 나머지는 모두 참
}
```

---

## RuntimeError 발생 지점

| 상황 | 코드 위치 | 오류 메시지 예시 |
|---|---|---|
| 숫자 연산에 비숫자 사용 | `Executor.java:209-213` | `"string 타입에 대해 '-' 연산은 지원하지 않습니다."` |
| `+` 에 혼합 타입 | `Executor.java:142` | `"Operands must be two numbers or two strings."` |
| 0으로 나누기 | `Executor.java:148` | `"Division by zero."` |
| 미선언 변수 접근 | `Environment.java:48` | `"Undefined variable 'x'."` (방어적 assertion) |

```java
// Executor.java:215-220 — 타입 오류 메시지 생성
private void checkNumberOperands(Token op, Object left, Object right) {
    if (left instanceof Double && right instanceof Double) return;
    throw new RuntimeError(op,
            typeName(left) + " 타입과 " + typeName(right) + " 타입에 대해 '" +
            op.origin() + "' 연산은 지원하지 않습니다.");
}
```

> `RuntimeError`는 `CodeFabError`를 상속하며 `stage() = "runtime"` 을 반환합니다. 에러 계층 상세: [08-design-patterns.md](08-design-patterns.md)

---

## stringify() — 출력 포맷 (`Executor.java:234-243`)

```java
public static String stringify(Object value) {
    if (value == null) return "nil";
    if (value instanceof Double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return String.valueOf(d.longValue());  // 15.0 → "15"
        }
        return d.toString();
    }
    return value.toString();   // Boolean, String
}
```

정수값 Double은 `.0`을 제거해 출력합니다. `public static`이므로 Debugger에서도 재사용합니다 (`Debugger.java:222`).

---

## ExecutionListener 훅 위치 — Observer 연동

`Executor`는 `ExecutionListener`가 등록된 경우에만 콜백을 호출합니다. 성능 영향 없이 디버거를 붙일 수 있습니다.

```java
// Executor.java:197-200
private void execute(Stmt stmt) {
    if (listener != null) listener.onStatement(stmt);  // 구문 실행 직전
    stmt.accept(this);
}
```

스코프 진입/이탈은 `executeBlock()` 과 `visitFor()` 에서 각각 호출됩니다:

```java
if (listener != null) listener.onEnterScope();   // Executor.java:186, 68
// ... 블록 실행 ...
if (listener != null) listener.onExitScope();    // Executor.java:190, 76
```

> Observer 패턴 전체 설명 및 Debugger 구현: [07-shell-cli.md](07-shell-cli.md)

---

## REPL 전역 상태 유지

```java
// Executor.java:24-25
private final Environment globalEnv = new Environment();
private Environment environment = globalEnv;
```

`globalEnv`는 `Executor` 인스턴스 생명주기 동안 유지됩니다. REPL에서 제출(submission)마다 `executor.run(program)`을 호출해도 `globalEnv`는 재설정되지 않으므로, 이전에 선언한 변수가 다음 제출에서도 살아 있습니다.
