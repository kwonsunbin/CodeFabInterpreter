# 04. AST와 Visitor 패턴

## 왜 Visitor 패턴인가?

AST는 여러 노드 타입을 가지며, 노드 위에서 **여러 종류의 연산**을 수행해야 합니다 (정적 분석, 실행, 출력 등). 두 가지 방식을 비교합니다.

### 방식 1: 각 노드에 instanceof 분기 (나쁜 예)

```java
// 이렇게 하면 안 됩니다
void check(Stmt stmt) {
    if (stmt instanceof Stmt.Var v)   { /* ... */ }
    if (stmt instanceof Stmt.If i)    { /* ... */ }
    if (stmt instanceof Stmt.For f)   { /* ... */ }
    // ...노드 종류가 늘어날 때마다 수정 필요
}
```

**문제:** 새 연산(출력, 최적화 등)을 추가할 때마다 모든 조건문 코드를 복사하거나 수정해야 합니다 → **OCP(개방-폐쇄 원칙) 위반**.

### 방식 2: Visitor 패턴 (실제 구현)

새 연산은 **새 Visitor 클래스**를 추가하는 것만으로 완성됩니다. 기존 AST 노드 코드는 건드리지 않습니다.

---

## 구조 — sealed class + 내부 Visitor 인터페이스

### `Stmt.java:11-24`

```java
public abstract sealed class Stmt
        permits Stmt.Var, Stmt.If, Stmt.For,
                Stmt.Print, Stmt.Block, Stmt.Expression {

    // ① Visitor 인터페이스: 노드 종류마다 visit 메서드
    public interface Visitor<R> {
        R visitVar(Var stmt);
        R visitIf(If stmt);
        R visitFor(For stmt);
        R visitPrint(Print stmt);
        R visitBlock(Block stmt);
        R visitExpression(Expression stmt);
    }

    // ② 이중 디스패치 진입점
    public abstract <R> R accept(Visitor<R> visitor);
}
```

### `Expr.java:10-26`

```java
public abstract sealed class Expr
        permits Expr.Binary, Expr.Logical, Expr.Comparison,
                Expr.Unary, Expr.Grouping, Expr.Literal,
                Expr.Variable, Expr.Assign {

    public interface Visitor<R> {
        R visitBinary(Binary expr);
        R visitLogical(Logical expr);
        R visitComparison(Comparison expr);
        R visitUnary(Unary expr);
        R visitGrouping(Grouping expr);
        R visitLiteral(Literal expr);
        R visitVariable(Variable expr);
        R visitAssign(Assign expr);
    }

    public abstract <R> R accept(Visitor<R> visitor);
}
```

---

## 더블 디스패치 원리

Visitor 패턴의 핵심은 **두 번의 동적 디스패치**입니다.

```
1차 디스패치: stmt.accept(visitor)
  → 런타임에 stmt의 실제 타입(예: Stmt.Var)에 따라 Stmt.Var.accept() 호출

2차 디스패치: visitor.visitVar(this)
  → 런타임에 visitor의 실제 타입(예: Checker)에 따라 Checker.visitVar() 호출
```

### 예: `Stmt.Var.accept()` (Stmt.java:35)

```java
public static final class Var extends Stmt {
    public final Token name;
    public final Expr initializer;
    // ...
    @Override public <R> R accept(Visitor<R> v) { return v.visitVar(this); }
    //                                                    ^^^^^^^^^^^^^^^^^
    //                              this = Stmt.Var 인스턴스, v = Checker 또는 Executor
}
```

### 예: Checker의 방문 (Checker.java:92-97)

```java
@Override
public Void visitVar(Stmt.Var stmt) {
    declare(stmt.name);                  // 선언 상태로 표시
    if (stmt.initializer != null)
        scanExpr(stmt.initializer);      // 초기화식 검사
    define(stmt.name);                   // 정의 완료
    return null;
}
```

### 예: Executor의 방문 (Executor.java:50-54)

```java
@Override
public Void visitVar(Stmt.Var stmt) {
    Object value = stmt.initializer != null
            ? evaluate(stmt.initializer)
            : null;
    environment.define(stmt.name.origin(), value);
    return null;
}
```

---

## sealed class가 Visitor를 강화하는 방법

`sealed class`는 Java 17+에서 허용된 하위 타입을 컴파일 타임에 제한합니다.

```java
public abstract sealed class Stmt
        permits Stmt.Var, Stmt.If, Stmt.For, Stmt.Print, Stmt.Block, Stmt.Expression
```

이로 인해:
1. **컴파일러가 `switch` 완전성을 검사합니다.** `Checker.scanExpr()`에서 sealed `Expr`의 모든 케이스를 처리하지 않으면 컴파일 오류가 납니다.
2. **Visitor 인터페이스의 모든 `visit` 메서드 구현을 강제합니다.** 구현체가 누락하면 컴파일 오류.

```java
// Checker.java:142-170 — sealed Expr에 대한 완전한 switch
private void scanExpr(Expr expr) {
    switch (expr) {
        case Expr.Variable v   -> { /* ... */ }
        case Expr.Assign a     -> { /* ... */ }
        case Expr.Binary b     -> { /* ... */ }
        case Expr.Logical l    -> { /* ... */ }
        case Expr.Comparison c -> { /* ... */ }
        case Expr.Unary u      -> { /* ... */ }
        case Expr.Grouping g   -> { /* ... */ }
        case Expr.Literal ignored -> {}
    }
    // 새 Expr 하위 타입이 permits에 추가되면 여기도 수정 강제
}
```

---

## 두 Visitor 구현체 비교

| 항목 | `Checker` | `Executor` |
|---|---|---|
| 구현 인터페이스 | `Stmt.Visitor<Void>` | `Stmt.Visitor<Void>`, `Expr.Visitor<Object>` |
| 표현식 처리 방식 | `scanExpr(Expr)` 내부 switch 사용 | `Expr.Visitor<Object>` 직접 구현 |
| 반환 타입 | `Void` (사이드 이펙트: `CheckResult` 누적) | Stmt: `Void`, Expr: `Object` (평가 결과) |
| 실행 시점 | 파싱 후, 실행 전 | Checker 통과 후 |
| 목적 | 정적 검사, 진단 수집 | 값 계산, 출력, 상태 변경 |
| 실패 방식 | `result.addError()` 수집 (계속 진행) | `RuntimeError` throw (즉시 중단) |

### Checker의 표현식 처리

Checker는 `Expr.Visitor<Object>`를 구현하지 않고, 대신 `scanExpr(Expr)` 안에서 sealed switch를 씁니다. 이유: Checker는 표현식의 **값**이 필요 없고 변수 스코프 규칙만 검사합니다. switch는 필요한 케이스만 선택적으로 처리합니다.

### Executor의 표현식 처리

Executor는 `Expr.Visitor<Object>`를 완전히 구현합니다. 모든 표현식이 **값으로 평가**되어야 하기 때문입니다. `evaluate(Expr)` → `expr.accept(this)` 호출이 Visitor 더블 디스패치를 통해 올바른 `visit` 메서드를 찾습니다.

```java
// Executor.java:195
private Object evaluate(Expr expr) { return expr.accept(this); }
```

---

## 노드 불변성 (Immutable AST)

모든 AST 노드는 `public final` 필드만 가지며 setter가 없습니다.

```java
// Expr.java:29-39
public static final class Binary extends Expr {
    public final Expr left;
    public final Token op;
    public final Expr right;
    // setter 없음 → AST는 생성 후 변경 불가
    ...
}
```

AST가 불변이면:
- Checker와 Executor가 같은 AST를 순서대로 순회해도 안전합니다
- 디버깅 시 AST 상태가 달라지지 않습니다

---

## 요약: Visitor 패턴의 적용 효과

```
AST 노드들 (Expr/Stmt 하위 타입)
   │  accept(visitor)
   ▼
Visitor 인터페이스                     ← 확장 포인트
   ├── Checker  (정적 분석)
   └── Executor (런타임 평가)
       └── [미래] Printer, Optimizer, ...  ← 노드 수정 없이 추가 가능
```

새로운 연산이 필요하면 Visitor 구현체 하나만 추가하면 되며, 기존 `Expr.java` / `Stmt.java`는 변경하지 않습니다.
