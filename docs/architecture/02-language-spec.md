# 02. CodeFab 언어 명세

## 토큰 타입 (`TokenType.java:3-29`)

```java
public enum TokenType {
    // 구분자 / 그룹핑
    LEFT_PAREN, RIGHT_PAREN,     // ( )
    LEFT_BRACE, RIGHT_BRACE,     // { }
    LEFT_BRACKET, RIGHT_BRACKET, // [ ]  (예약, 현재 미사용)
    SEMICOLON, COMMA,            // ; ,

    // 산술 연산자
    PLUS, MINUS, STAR, SLASH,    // + - * /

    // 대입 / 비교 연산자
    EQUAL,                       // =
    EQUAL_EQUAL,                 // ==
    GREATER, GREATER_EQUAL,      // > >=
    LESS, LESS_EQUAL,            // < <=

    // 단항 연산자
    BANG, BANG_EQUAL,            // ! !=

    // 논리 연산자 (키워드 형태)
    AND, OR,

    // 키워드
    VAR, IF, ELSE, FOR, PRINT,
    TRUE, FALSE,
    FUNC, RETURN,                // 예약됨 (현재 미구현)

    // 식별자 / 리터럴
    IDENTIFIER, NUMBER, STRING,

    EOF
}
```

---

## 문법 (BNF)

```
program    → statement* EOF

statement  → varDecl
           | ifStmt
           | forStmt
           | printStmt
           | block
           | exprStmt

varDecl    → "var" IDENTIFIER ( "=" expression )? ";"
ifStmt     → "if" "(" expression ")" statement ( "else" statement )?
forStmt    → "for" "(" (varDecl | exprStmt | ";") expression? ";" expression? ")" block
printStmt  → "print" expression ";"
block      → "{" statement* "}"
exprStmt   → expression ";"

expression → assignment
assignment → IDENTIFIER "=" assignment       ← 우결합 (right-associative)
           | or
or         → and ( "or" and )*
and        → comparison ( "and" comparison )*
comparison → term ( ( ">" | ">=" | "<" | "<=" | "==" | "!=" ) term )*
term       → factor ( ( "+" | "-" ) factor )*
factor     → unary ( ( "*" | "/" ) unary )*
unary      → ( "-" | "!" ) unary
           | primary
primary    → NUMBER | STRING | "true" | "false"
           | IDENTIFIER
           | "(" expression ")"
```

**연산자 우선순위 (낮은 순 → 높은 순):**

| 순위 | 연산자 | 결합 |
|---|---|---|
| 1 (낮음) | `=` (대입) | 우결합 |
| 2 | `or` | 좌결합 |
| 3 | `and` | 좌결합 |
| 4 | `> >= < <= == !=` | 좌결합 |
| 5 | `+ -` | 좌결합 |
| 6 | `* /` | 좌결합 |
| 7 (높음) | 단항 `- !` | 우결합 |

---

## AST 노드 타입

### 구문 노드 — `Stmt.java:11-97`

`Stmt`는 `sealed abstract class`이며 허용된 하위 타입이 명시적으로 제한됩니다.

```java
public abstract sealed class Stmt
        permits Stmt.Var, Stmt.If, Stmt.For,
                Stmt.Print, Stmt.Block, Stmt.Expression { ... }
```

| 노드 | 필드 | 설명 |
|---|---|---|
| `Var` | `name: Token`, `initializer: Expr?` | `var x = 10;` |
| `If` | `condition: Expr`, `thenBranch: Stmt`, `elseBranch: Stmt?` | `if (...) ... else ...` |
| `For` | `initializer: Stmt?`, `condition: Expr?`, `increment: Expr?`, `body: Stmt` | `for (;;) {}` |
| `Print` | `expression: Expr` | `print expr;` |
| `Block` | `statements: List<Stmt>` | `{ ... }` |
| `Expression` | `expression: Expr` | `a = 5;` 처럼 표현식을 구문으로 사용 |

### 표현식 노드 — `Expr.java:10-121`

```java
public abstract sealed class Expr
        permits Expr.Binary, Expr.Logical, Expr.Comparison,
                Expr.Unary, Expr.Grouping, Expr.Literal,
                Expr.Variable, Expr.Assign { ... }
```

| 노드 | 필드 | 예시 |
|---|---|---|
| `Binary` | `left: Expr`, `op: Token`, `right: Expr` | `a + b`, `x * 2` |
| `Logical` | `left: Expr`, `op: Token`, `right: Expr` | `a and b`, `x or y` |
| `Comparison` | `left: Expr`, `op: Token`, `right: Expr` | `a > b`, `x == 1` |
| `Unary` | `op: Token`, `operand: Expr` | `-x`, `!flag` |
| `Grouping` | `expression: Expr` | `(a + b)` |
| `Literal` | `value: Object`, `line: int` | `42`, `"hello"`, `true` |
| `Variable` | `name: Token` | `x` (변수 읽기) |
| `Assign` | `name: Token`, `value: Expr` | `x = 10` (반환값 있음) |

> `Literal.value`의 Java 타입은 `Double | String | Boolean | null` 중 하나입니다.  
> `Expr.Assign`은 표현식이므로 값을 반환하며, `Stmt.Expression`으로 래핑되어 구문으로도 사용됩니다.

---

## 데이터 타입 및 런타임 표현

| CodeFab 타입 | Java 런타임 타입 | 예시 리터럴 |
|---|---|---|
| Number | `Double` | `42`, `3.14` |
| String | `String` | `"hello"` |
| Boolean | `Boolean` | `true`, `false` |
| nil | `null` | 초기화 없는 `var x;` |

**Truthiness 규칙** (`Executor.java:203-207`):
- `Boolean` → 그 자체
- `null` → `false`
- 그 외 모든 값 → `true`

**숫자 출력** (`Executor.java:236-239`): 정수값 Double은 `.0` 없이 출력됩니다.
```
15.0 → "15"
3.14 → "3.14"
```

---

## 예제 프로그램 (`examples/hello.txt`)

```codefab
// 기본 변수 및 산술
var a = 10;
var b = 3;
print a + b;       // 출력: 13
print a - b;       // 출력: 7
print a * b;       // 출력: 30

// 문자열
var name = "CodeFab";
print "Hello, " + name + "!";   // 출력: Hello, CodeFab!

// 조건문
if (a > b) {
    print "a가 더 큽니다";
} else {
    print "b가 더 큽니다";
}

// 반복문
var sum = 0;
for (var i = 1; i < 6; i = i + 1) {
    sum = sum + i;
}
print sum;         // 출력: 15
```

---

## 제한 사항 (현재 버전)

- **함수 정의 없음**: `Func` / `return` 토큰은 Lexer가 인식하지만(`Lexer.java:28-29`) Parser에 구현 없음
- **배열 없음**: `[`, `]` 토큰은 예약됨
- **타입 검사 없음**: 타입 불일치(`number + string`)는 정적 분석에서 잡지 않고 런타임 `RuntimeError`로 처리
- **단일 파일 실행**: import/module 시스템 없음
