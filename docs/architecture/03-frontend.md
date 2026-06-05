# 03. 프론트엔드 — Lexer, Parser, Assembler

## 전체 흐름

```
소스 텍스트 (String)
      │
      ▼ Assembler.assemble()   ← Facade
      │
      ├─► Lexer.scanTokens()   ← 문자 → 토큰
      │         │
      │    List<Token>
      │         │
      └─► Parser.parse()       ← 토큰 → AST
               │
          List<Stmt>
```

---

## Lexer — `assembler/Lexer.java`

### 역할

소스 텍스트를 한 문자씩 읽어 **토큰 목록**을 생성합니다. 주석(`//`)은 줄 끝까지 버립니다.

### 핵심 필드

```java
// Lexer.java:32-37
private final String    source;
private final List<Token> tokens = new ArrayList<>();
private int start   = 0;   // 현재 토큰 시작 위치
private int current = 0;   // 탐색 커서
private int line    = 1;   // 현재 줄 번호
```

### 메인 루프 — `scanTokens()` (line 43-50)

```java
public List<Token> scanTokens() {
    while (!isAtEnd()) {
        start = current;
        scanToken();              // 한 토큰 스캔
    }
    tokens.add(new Token(TokenType.EOF, "", null, line));
    return tokens;
}
```

### 단일 문자 디스패치 — `scanToken()` (line 52-91)

`switch`로 첫 문자를 보고 토큰 종류를 결정합니다.

```java
switch (c) {
    case '(' -> addToken(TokenType.LEFT_PAREN);
    // ... 단일 문자 토큰들 ...

    case '/' -> {
        if (match('/')) {
            while (!isAtEnd() && peek() != '\n') advance();  // 주석 스킵
        } else {
            addToken(TokenType.SLASH);
        }
    }

    case '>' -> addMatchToken('=', TokenType.GREATER_EQUAL, TokenType.GREATER);  // >= 또는 >
    case '=' -> addMatchToken('=', TokenType.EQUAL_EQUAL,   TokenType.EQUAL);    // == 또는 =
    case '!' -> addMatchToken('=', TokenType.BANG_EQUAL,    TokenType.BANG);     // != 또는 !

    case '"' -> string();         // 문자열 리터럴
    case ' ', '\r', '\t' -> {}    // 공백 무시
    case '\n' -> line++;          // 줄 번호 증가
    default -> {
        if (isDigit(c)) number();
        else if (isAlpha(c)) identifier();
        else throw new LexError(line, "Unexpected character: " + c);
    }
}
```

### `addMatchToken()` 패턴

`>=`, `==`, `!=` 등 **2문자 복합 연산자**를 처리하는 헬퍼입니다. 다음 문자가 `expected`이면 긴 토큰, 아니면 짧은 토큰을 선택합니다.

### 문자열 리터럴 — `string()` (line 93-106 부근)

`"` 로 시작해 닫는 `"` 까지 소비한 후, 따옴표를 제거한 내용을 `String` 값으로 저장합니다. 미종료 문자열은 `LexError`를 던집니다.

### 숫자 리터럴 — `number()` (line 108-115 부근)

연속된 숫자를 소비하고, `.` 뒤에 숫자가 이어지면 소수점도 포함합니다. 값은 `Double.parseDouble()`로 파싱해 토큰에 저장합니다.

### 식별자 / 키워드 — `identifier()` (line 125-135 부근)

영문자/밑줄로 시작하는 단어를 소비한 후, 미리 정의된 **키워드 맵** (`Lexer.java:18-30`)에서 조회합니다. 맵에 없으면 `IDENTIFIER` 토큰으로 처리됩니다.

```java
private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
    Map.entry("var",    TokenType.VAR),
    Map.entry("if",     TokenType.IF),
    Map.entry("else",   TokenType.ELSE),
    Map.entry("for",    TokenType.FOR),
    Map.entry("print",  TokenType.PRINT),
    Map.entry("true",   TokenType.TRUE),
    Map.entry("false",  TokenType.FALSE),
    Map.entry("and",    TokenType.AND),
    Map.entry("or",     TokenType.OR),
    Map.entry("Func",   TokenType.FUNC),
    Map.entry("return", TokenType.RETURN)
);
```

### Token 레코드 — `token/Token.java`

```java
// token/Token.java (record)
public record Token(TokenType type, String origin, Object value, int line) {}
```

- `origin`: 소스에서 추출한 원본 텍스트 (`">="`  등)
- `value`: 파싱된 값 — 숫자는 `Double`, 문자열은 `String`, 그 외 `null`
- `line`: 1-기반 소스 줄 번호 (에러 메시지 및 디버거에서 사용)

---

## Parser — `assembler/Parser.java`

### 역할

토큰 목록을 **재귀 하강(Recursive Descent)** 방식으로 소비해 AST(`List<Stmt>`)를 생성합니다.

### 재귀 하강이란?

문법의 각 규칙을 **메서드 하나**에 대응시키고, 메서드가 서로를 재귀 호출하며 파싱합니다. 높은 우선순위 규칙일수록 호출 체인의 더 깊은 곳에 위치해 자연스럽게 우선순위가 표현됩니다.

```
expression()          ← 가장 낮은 우선순위 진입점
  └─ assignment()
       └─ or()
            └─ and()
                 └─ comparison()
                      └─ term()
                           └─ factor()
                                └─ unary()
                                     └─ primary()  ← 가장 높은 우선순위
```

### 왼쪽 결합 헬퍼 — `leftAssoc()` (line 116-120)

좌결합 이진 연산을 반복 처리하는 **범용 헬퍼**입니다. `or()`, `and()`, `comparison()`, `term()`, `factor()` 모두 이 메서드를 사용합니다.

```java
// Parser.java:116-120
private Expr leftAssoc(Supplier<Expr> operand, NodeBuilder builder, TokenType... ops) {
    Expr expr = operand.get();                              // 왼쪽 피연산자
    while (match(ops))                                      // 연산자가 있으면 반복
        expr = builder.build(expr, previous(), operand.get()); // 새 노드로 감싸기
    return expr;
}
```

`1 + 2 + 3`은 `((1 + 2) + 3)`으로 좌결합됩니다.

### 대입 — `assignment()` (line 129-137): 우결합

```java
// Parser.java:129-137
private Expr assignment() {
    Expr expr = or();
    if (match(TokenType.EQUAL)) {
        Expr value = assignment();                             // 재귀 → 우결합
        if (expr instanceof Expr.Variable v)
            return new Expr.Assign(v.name, value);
        throw new ParseError(peek().line(), "Invalid assignment target.");
    }
    return expr;
}
```

대입 대상이 변수 참조(`Expr.Variable`)가 아니면 `ParseError`를 던집니다. 예: `1 + 2 = 5`는 파스 오류입니다.

### 구문 파싱 — `statement()` (line 38-45)

```java
// Parser.java:38-45
private Stmt statement() {
    if (match(TokenType.VAR))        return varDeclaration();
    if (match(TokenType.IF))         return ifStatement();
    if (match(TokenType.FOR))        return forStatement();
    if (match(TokenType.PRINT))      return printStatement();
    if (match(TokenType.LEFT_BRACE)) return block();
    return expressionStatement();                              // 기본: 표현식 구문
}
```

### `forStatement()` 핵심 (line 72-84)

```java
private Stmt forStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'for'.");
    Stmt initializer;
    if (match(SEMICOLON))  initializer = null;
    else if (match(VAR))   initializer = varDeclaration();
    else                   initializer = expressionStatement();
    Expr condition  = check(SEMICOLON) ? null : expression();
    consume(SEMICOLON, "Expect ';' after for condition.");
    Expr increment  = check(RIGHT_PAREN) ? null : expression();
    consume(RIGHT_PAREN, "Expect ')' after for clauses.");
    consume(LEFT_BRACE,  "Expect '{' before for body.");
    return new Stmt.For(initializer, condition, increment, block());
}
```

초기화/조건/증가 세 절이 모두 선택적(`nullable`)입니다. 조건이 `null`이면 Executor에서 무한 루프로 처리합니다.

### primary() — 리터럴과 괄호 (line 170-180)

```java
private Expr primary() {
    if (match(NUMBER, STRING, TRUE, FALSE))
        return new Expr.Literal(previous().value(), previous().line());
    if (match(IDENTIFIER)) return new Expr.Variable(previous());
    if (match(LEFT_PAREN)) {
        Expr expr = expression();
        consume(RIGHT_PAREN, "Expect ')' after expression.");
        return new Expr.Grouping(expr);
    }
    throw new ParseError(peek().line(), "Expect expression.");
}
```

---

## Assembler — `assembler/Assembler.java`

### 역할: **Facade 패턴**

Lexer와 Parser를 숨기고 `assemble(source)` 단일 메서드를 제공합니다.

```java
// Assembler.java:25-30
public List<Stmt> assemble(String source) {
    List<Token> tokens = (lexer != null)
            ? lexer.scanTokens()           // 테스트 주입 시
            : new Lexer(source).scanTokens();
    return new Parser(tokens).parse();
}
```

- 테스트에서 `Assembler(Lexer)` 생성자로 가짜 Lexer를 주입할 수 있습니다 (`Assembler.java:21`).
- 호출자(`Pipeline`)는 Lexer와 Parser의 존재를 알 필요 없이 소스 → AST 변환을 얻습니다.

> 에러 처리: Lexer에서 `LexError`, Parser에서 `ParseError`를 던지며, Pipeline에서 `CodeFabError`로 잡아 사용자에게 표시합니다.
