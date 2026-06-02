# CodeFab Interpreter — 4인 TDD 업무 분담

CodeFabInterpreter는 **Red-Green-Refactor TDD** 방식으로 4명이 동시에 구현합니다.  
스켈레톤 코드에 80개의 테스트가 이미 작성되어 있고 (79개 Red), 각자 담당 컴포넌트를 Green으로 만들어 가는 방식으로 진행합니다.

---

## TDD 사이클

```
Red   → 실패하는 테스트 하나 선택, 단건 실행으로 실패 확인
Green → 테스트가 통과할 최소한의 구현 작성
Refactor → Green 상태를 유지하며 코드 정리
반복  → 다음 테스트로 넘어감
```

### 단건 테스트 실행 (예시)

```bash
# 특정 테스트 하나만 실행
JAVA_HOME=~/.jdks/temurin-21.0.11 ./gradlew test --tests "org.example.codefab.assembler.LexerTest.singleCharTokens"

# 특정 클래스 전체 실행
JAVA_HOME=~/.jdks/temurin-21.0.11 ./gradlew test --tests "org.example.codefab.assembler.LexerTest"

# 전체 테스트 실행
JAVA_HOME=~/.jdks/temurin-21.0.11 ./gradlew test
```

> **주의:** M4(최종 통합) 전까지 `./gradlew build`는 E2E 테스트 미완성으로 실패합니다.  
> 담당 테스트 클래스만 `--tests` 필터로 Green 확인하며 진행하세요.

---

## 팀원별 담당

| 팀원 | 브랜치 | 담당 파일 | 전용 테스트 | 분량/난이도 |
|---|---|---|---|---|
| **A — Lexer & Shell** | `feat/lexer` | `assembler/Lexer.java`<br>`shell/Shell.java`<br>`log/Logger.java` | `LexerTest` (11개) | Lexer 약 150줄 (쉬움) → **가장 먼저 완성·머지하여 B 언블록**<br>이후 Shell (~100줄) + 통합 코디네이터 역할 |
| **B — Parser** | `feat/parser` | `assembler/Parser.java`<br>`assembler/Assembler.java` | `ParserTest` (20개) | 최고 난이도 (~270줄, 재귀 하강 파서 + 연산자 우선순위)<br>Assembler 파사드 (~5줄) 포함 |
| **C — Checker** | `feat/checker` | `checker/Checker.java` | `CheckerTest` (8개) | 중간 (~160줄, 2단계 declare/define + 스코프 스택) |
| **D — Executor & Buffer** | `feat/executor` | `executor/Executor.java`<br>`executor/Environment.java`<br>`shell/SubmissionBuffer.java` | `SubmissionBufferTest` (9개) | Executor 중상 (~220줄) + Environment (~40줄)<br>SubmissionBuffer (~55줄) **완전 독립 → 첫날 머지 가능** |

### EndToEndTest (32개) 책임 분담

E2E 테스트는 통합 마일스톤(M4) 기준이지만, 실패 시 각자 담당:

| E2E 테스트 그룹 | 담당 |
|---|---|
| Parse 에러 4건 (`missingSemicolon`, `missingClosingParen`, `invalidAssignmentTarget`, `expectExpression`) | B |
| Check 에러 2건 (`selfReferenceInInitializer_checkError`, `duplicateDeclInSameScope_checkError`) | C |
| 나머지 26건 (산술, 변수, 블록, 반복문, 런타임 에러 등) | D |
| 통합 조율 | A |

---

## 의존성 그래프

```
Lexer ──▶ Parser ──▶ Assembler
                          │
                  ┌───────┴───────┐
                  ▼               ▼
               Checker         Executor
                                  │
                             Environment
Shell ◀── SubmissionBuffer (독립)
Logger (독립)
```

**Lexer → Parser → Checker/Executor** 순으로 의존하지만, **병렬 시작**합니다.

---

## 병렬 시작 전략 (손 AST / 손 Token)

Assembler가 완성되기 전에도 B, C, D는 아래 방법으로 즉시 개발을 시작합니다.

### B (Parser): 손으로 Token 리스트 만들기

```java
// Lexer 없이 Parser 직접 테스트
import static org.example.codefab.token.TokenType.*;

List<Token> tokens = List.of(
    new Token(PRINT, "print", null, 1),
    new Token(NUMBER, "1",    1.0,  1),
    new Token(PLUS,  "+",    null, 1),
    new Token(NUMBER, "2",   2.0,  1),
    new Token(SEMICOLON, ";", null, 1),
    new Token(EOF,   "",     null, 1)
);
List<Stmt> stmts = new Parser(tokens).parse();
```

### C (Checker) / D (Executor): 손으로 AST 만들기

```java
// print 1 + 2; 를 손으로 구성
var left  = new Expr.Literal(1.0);
var op    = new Token(TokenType.PLUS, "+", null, 1);
var right = new Expr.Literal(2.0);
var sum   = new Expr.Binary(left, op, right);
var stmt  = new Stmt.Print(sum);

// Executor 테스트
executor.run(List.of(stmt));

// Checker 테스트
CheckResult result = checker.check(List.of(stmt));
assertTrue(result.ok());
```

C/D는 손 AST 기반 **보조 테스트 파일**을 직접 작성하는 것이 자기 주도 Red 단계입니다.  
기존 `CheckerTest`/`EndToEndTest`는 M3~M4 인수(acceptance) 기준으로 활용합니다.

---

## 브랜치 & PR 규칙

### 브랜치 생성

```bash
# 각자 본인 브랜치 생성 후 시작
git checkout -b feat/lexer   # A
git checkout -b feat/parser  # B
git checkout -b feat/checker # C
git checkout -b feat/executor # D
```

### 머지 조건

PR을 main에 머지하려면:

1. **컴파일 통과** — `./gradlew compileJava compileTestJava` 오류 없음
2. **담당 테스트 클래스 Green** — `--tests` 필터로 전용 테스트 모두 통과
3. **기 머지된 테스트 무회귀** — 이미 Green인 다른 팀의 테스트가 깨지지 않음

```bash
# PR 전 로컬 검증 예시 (A 기준)
JAVA_HOME=~/.jdks/temurin-21.0.11 ./gradlew test --tests "org.example.codefab.assembler.LexerTest"
```

### PR 설명 템플릿

```
## 담당
- 파일: assembler/Lexer.java
- 테스트: LexerTest (11/11 green)

## TDD 진행 내역
- [Red] singleCharTokens → ...
- [Green] scanToken switch 구현
- [Refactor] ...

## 머지 후 언블록되는 팀원
- B (Parser): ParserTest 실행 가능해짐
```

---

## 마일스톤

| 단계 | 완료 조건 | 언블록 효과 |
|---|---|---|
| **M0** | 브랜치 생성 완료, D가 `SubmissionBuffer` 구현·PR·머지 | SubmissionBufferTest 9 green |
| **M1** | A가 `Lexer` 구현·PR·머지 | LexerTest 11 green, B의 ParserTest 언블록 |
| **M2** | B가 `Parser` + `Assembler` 구현·PR·머지 | ParserTest 20 green, CheckerTest·E2E parse 에러 언블록 |
| **M3** | C가 `Checker` 머지, D가 `Executor`+`Environment` 머지 | CheckerTest 8 green |
| **M4** | E2E 통합: A가 `Shell`+`Logger` 머지, 수동 REPL 스모크 테스트 | **80/80 green, `./gradlew build` 성공** |

---

## 컴포넌트별 구현 힌트

구현 시 `README.md`의 **EBNF 문법**, **에러 메시지 표**, **동작 예시**를 적극 참고하세요.  
각 파일의 `// TODO` 주석에 구현해야 할 내용이 설명되어 있습니다.

### A — Lexer (`assembler/Lexer.java`)

- `scanTokens()`: `while (!isAtEnd())` 루프로 `scanToken()` 반복 호출, 마지막에 EOF 추가
- `scanToken()`: `advance()`로 문자 읽고 `switch`로 분기. `//` 주석은 줄 끝까지 skip
- `string()`: `"` 만날 때까지 문자 소비, 미종료 시 `LexError` 던지기
- `number()`: 정수부 소비 후 `.숫자` 패턴이면 소수점 포함 파싱
- `identifier()`: 영숫자·`_` 소비 후 `KEYWORDS` 맵에서 타입 조회, `true`/`false`는 value도 설정

### A — Shell (`shell/Shell.java`)

- `run()`: `SubmissionBuffer` 사용, `isComplete()` 될 때만 `runPipeline()` 호출
- `runPipeline()`: assemble → check(경고 출력, 에러 시 return) → execute, `CodeFabError` catch 후 `log.error()`

### A — Logger (`log/Logger.java`)

- `executionStart/Complete()`: verbose 플래그가 true일 때만 `err("[exec] start/done")`
- `error()`: 항상 `err(e.userMessage())`
- `diagnostic()`: 항상 `err(msg)`

### B — Parser (`assembler/Parser.java`)

- 재귀 하강: `expression → assignment → or → and → comparison → term → factor → unary → primary`
- `parse()`: `while (!isAtEnd())` 루프로 `statement()` 호출
- `assignment()`: 오른쪽 결합 — `or()` 파싱 후 `EQUAL` 있으면 재귀 `assignment()`
- `for` 본문은 반드시 블록 — `consume(LEFT_BRACE, ...)` 후 `block()` 호출
- dangling-else: `ELSE`를 만나면 즉시 `statement()` 재귀 → 자동으로 nearest-if에 바인딩

### B — Assembler (`assembler/Assembler.java`)

```java
public List<Stmt> assemble(String source) {
    List<Token> tokens = new Lexer(source).scanTokens();
    return new Parser(tokens).parse();
}
```

### C — Checker (`checker/Checker.java`)

- `check()`: `result = new CheckResult()`, 첫 호출 시 `scopes.push(globalScope)`, 각 stmt `execute()`
- `declare()`: 현재 스코프에 이름 있으면 에러, 없으면 `scope.declare()`
- `visitVar()`: `declare(name)` → `evaluate(initializer)` → `define(name)` 순서 엄수 (self-reference 감지 핵심)
- `visitVariable()`: 현재 스코프에서 `DECLARING` 상태면 자기 참조 에러
- `visitFor()`: `beginScope()` → init/cond/incr/body 방문 → `endScope()`
- `visitBlock()`: `beginScope()` → statements 방문 → `endScope()`

### D — Executor (`executor/Executor.java`)

- `run()`: `log.executionStart()` → for-each execute → `log.executionComplete()`
- `visitFor()`: `new Environment(environment)`로 루프 환경 생성, `try-finally`로 환경 복원
- `visitBlock()`: `executeBlock(stmt.statements, new Environment(environment))`
- `executeBlock()`: `environment = blockEnv` → execute all → `finally { environment = previous }`
- `visitBinary()`: PLUS는 Double+Double 또는 String+String만 허용, 나머지는 `RuntimeError`
- `stringify()`: `Double`이 정수값이면 `.0` 없이 출력 (예: `5.0` → `"5"`)

### D — Environment (`executor/Environment.java`)

- `define()`: `values.put(name, value)` (현재 스코프에만)
- `get()`: 현재 스코프 → enclosing 체인 순서로 탐색, 없으면 `RuntimeError`
- `assign()`: 현재 스코프 → enclosing 체인 순서로 탐색 후 덮어쓰기, 없으면 `RuntimeError`

### D — SubmissionBuffer (`shell/SubmissionBuffer.java`)

```
for each char:
    if inString:
        if '"' → inString = false
        continue
    '"' → inString = true
    '(' → parenDepth++
    ')' → parenDepth--; if parenDepth < 0 → return true (stray)
    '{' → braceDepth++
    '}' → braceDepth--; if braceDepth < 0 → return true (stray)

return !inString && parenDepth <= 0 && braceDepth <= 0
```
