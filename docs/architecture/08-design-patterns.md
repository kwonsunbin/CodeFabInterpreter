# 08. 디자인 패턴 총정리

## 패턴 맵

| 패턴 | 위치 | 핵심 클래스 |
|---|---|---|
| **Visitor** | AST 순회 | `Expr.Visitor`, `Stmt.Visitor` / `Checker`, `Executor` |
| **Facade** | 단계 추상화 | `Assembler` (Lexer+Parser), `Pipeline` (3단계) |
| **Observer** | 디버거 연동 | `ExecutionListener` / `Debugger` |
| **Chain of Responsibility** | 스코프 변수 탐색 | `Environment.enclosing` 체인 |
| **Dependency Injection** | 컴포넌트 조립 | `Main` → 모든 컴포넌트 |
| **Template Method (변형)** | 에러 포맷 | `CodeFabError.userMessage()` |

---

## 1. Visitor 패턴

**목적:** AST 노드 구조와 노드 위의 연산을 분리합니다.

```
Expr / Stmt  (sealed class + Visitor<R> 인터페이스)
    │ accept(visitor)
    ▼
Checker  → Stmt.Visitor<Void>                    // 정적 분석
Executor → Stmt.Visitor<Void>, Expr.Visitor<Object>  // 런타임 평가
```

**핵심 코드 (`Expr.java:15-26`, `Stmt.java:15-24`):**

```java
public interface Visitor<R> {
    R visitBinary(Binary expr);
    // ...각 노드마다 visit 메서드
}
public abstract <R> R accept(Visitor<R> visitor);
```

**더블 디스패치 흐름:**
```
executor.run(stmts)
  → execute(stmt)
    → stmt.accept(executor)         ← 1차: stmt 타입으로 분기
      → executor.visitVar(this)     ← 2차: executor 타입으로 분기
```

**sealed class와의 시너지:** `permits` 목록이 닫혀 있어 컴파일러가 Visitor 구현의 완전성을 검사합니다. 새 노드 타입을 추가하면 모든 Visitor에서 컴파일 오류가 나 누락을 방지합니다.

> 상세 분석: [04-ast-visitor.md](04-ast-visitor.md)

---

## 2. Facade 패턴

**목적:** 복잡한 하위 시스템을 단일 인터페이스로 감쌉니다.

### Assembler 파사드 (`assembler/Assembler.java:25-30`)

```java
public List<Stmt> assemble(String source) {
    List<Token> tokens = new Lexer(source).scanTokens();
    return new Parser(tokens).parse();
}
```

호출자(`Pipeline`)는 Lexer와 Parser를 직접 알 필요가 없습니다.

### Pipeline 파사드 (`shell/Pipeline.java:36-53`)

```java
public void run(String source) {
    List<Stmt> program = assembler.assemble(source);   // Stage 1
    CheckResult cr = checker.check(program);           // Stage 2
    if (!cr.ok()) { /* 진단 출력 후 */ return; }
    executor.run(program);                             // Stage 3
}
```

`Shell`은 `pipeline.run(text)` 한 줄로 전체 컴파일+실행 파이프라인을 실행합니다.

**리팩토링 이력:** `Pipeline`은 원래 `Shell` 내부에 있던 실행 로직을 SRP 관점에서 분리한 결과입니다 (커밋 `[REFACTOR] Shell에서 Pipeline 추출`).

---

## 3. Observer 패턴

**목적:** Executor(실행 엔진)를 Debugger(관찰자)와 분리합니다. Executor는 디버거의 존재를 몰라도 됩니다.

### ExecutionListener 인터페이스 (`executor/ExecutionListener.java:11-21`)

```java
public interface ExecutionListener {
    void onStatement(Stmt stmt);         // 구문 실행 직전

    default void onEnterScope() {}       // default no-op
    default void onExitScope()  {}       // default no-op
}
```

`default` 구현으로 REPL/파일 모드에서 null 체크만 하면 되고, 구현 부담이 없습니다.

### Subject (Executor) (`Executor.java:197-200`, `186-190`)

```java
private void execute(Stmt stmt) {
    if (listener != null) listener.onStatement(stmt);
    stmt.accept(this);
}

// executeBlock() 내부
if (listener != null) listener.onEnterScope();
// ...
if (listener != null) listener.onExitScope();
```

### Observer (Debugger) (`Debugger.java:30`, `98`)

```java
public class Debugger implements ExecutionListener { ... }

executor.setListener(this);   // 등록
// 실행 종료 후
executor.setListener(null);   // 해제
```

> 상세 분석: [07-shell-cli.md](07-shell-cli.md)

---

## 4. Chain of Responsibility — 스코프 체인

**목적:** 변수 조회·대입 요청을 스코프 체인을 따라 위임합니다.

```java
// Environment.java:41-49
public Object get(Token name) {
    if (values.containsKey(name.origin()))
        return values.get(name.origin());      // 현재 스코프에서 찾음
    if (enclosing != null)
        return enclosing.get(name);            // 부모에게 위임
    throw new RuntimeError(name, "Undefined variable ...");
}
```

```
For Env → Block Env → Global Env → RuntimeError (chain 끝)
```

Checker도 동일한 개념을 `Deque<Scope>`으로 구현합니다 (`isVisible()`, `Checker.java:82-87`).

---

## 5. Dependency Injection

**목적:** 컴포넌트가 자신의 의존성을 직접 생성하지 않아 테스트 가능성과 교체 용이성을 높입니다.

**조립 지점 (`Main.java:30-34`):**

```java
Logger    log      = new Logger(verbose);
Assembler assembler = new Assembler();
Checker   checker  = new Checker();
Executor  executor = new Executor(log);             // Logger 주입
Pipeline  pipeline = new Pipeline(assembler, checker, executor, log, System.out);
```

**Logger의 스트림 주입 (`Logger.java:24-27`):**

```java
public Logger(boolean verbose, PrintStream err) {
    this.verbose = verbose;
    this.err     = err;
}
```

테스트에서 `new Logger(false, myStream)`으로 출력을 가로챌 수 있습니다.

---

## 6. 에러 계층 — Template Method 변형

**목적:** 모든 에러가 일관된 포맷 `[stage] line N: message`로 출력되도록 강제합니다.

```java
// CodeFabError.java:3-17
public abstract class CodeFabError extends RuntimeException {
    private final int line;

    public abstract String stage();           // 하위 클래스에서 구현

    public String userMessage() {             // 템플릿 메서드
        return "[" + stage() + "] line " + line + ": " + getMessage();
    }
}
```

| 클래스 | `stage()` 반환값 | 발생 위치 |
|---|---|---|
| `LexError` | `"lex"` | `Lexer.java` |
| `ParseError` | `"parse"` | `Parser.java` |
| `RuntimeError` | `"runtime"` | `Executor.java`, `Environment.java` |

**에러 전파 경로:**

```
LexError / ParseError → Assembler.assemble() →
    Pipeline.run() catch CodeFabError → 출력
    FileRunner → System.exit(1)

RuntimeError → Executor.run() →
    Shell.pipeline.run() catch CodeFabError → 출력 (REPL 계속)
    FileRunner → System.exit(1)

Checker 오류 → CheckResult 수집 (예외 없음) →
    Pipeline: cr.ok() == false → 출력 후 return
```

---

## SOLID 관점

| 원칙 | 적용 사례 |
|---|---|
| **SRP** (단일 책임) | Shell(REPL I/O), Pipeline(파이프라인), FileRunner(종료코드), Debugger(정지 로직), Logger(로그 출력) 각자 단일 역할 |
| **OCP** (개방-폐쇄) | 새 연산 추가 시 새 Visitor 클래스만 추가, 기존 AST 노드 수정 불요. `SemanticRule` 인터페이스로 규칙 외부화 (리팩토링 커밋 근거) |
| **LSP** (리스코프 치환) | `LexError`, `ParseError`, `RuntimeError` 모두 `CodeFabError` 자리를 대체 가능, `userMessage()` 행동 동일 |
| **ISP** (인터페이스 분리) | `ExecutionListener`의 `onEnterScope()`/`onExitScope()`는 `default` no-op — 구현체가 필요한 것만 오버라이드 |
| **DIP** (의존성 역전) | `Executor`는 `ExecutionListener` 인터페이스에 의존, 구체 `Debugger` 클래스를 모름 |

---

## 테스트 전략 개요

```
총 215개 테스트 (JUnit 5)
```

| 클래스 | 개수 | 범위 |
|---|---|---|
| `LexerTest` | ~25 | 토큰 스캔, 리터럴, 주석, 오류 케이스 |
| `ParserTest` | ~34 | 우선순위, 결합성, AST 구조 |
| `CheckerTest` | ~14 | 4가지 스코프 규칙, 섀도잉 |
| `ExecutorTest` | ~55 | 산술/논리/비교, 변수, 스코프, 런타임 오류 |
| `EnvironmentTest` | ~8 | define/get/assign, 체인 탐색 |
| `SubmissionBufferTest` | ~12 | 괄호 균형, 문자열 리터럴 내 괄호, 멀티라인 |
| `PipelineTest` | ~5 | 3단계 통합 (assemble→check→execute) |
| `LoggerTest` | ~8 | verbose 게이팅, stderr 출력, 오류 포맷 |
| `EndToEndTest` | ~54 | 전체 실행 경로: 파스 오류, 검사 오류, 타입 오류, 기능 테스트 |

**의존성 주입의 테스트 효과:**
- `Logger(false, myStream)` → 출력 캡처 테스트
- `Assembler(mockLexer)` → Lexer 단독 주입 테스트
- `Pipeline`에서 각 컴포넌트를 교체해 단계별 격리 테스트
