# 07. 셸 / CLI 계층

## 구성 요소 개요

```
CLI 인수
  │
  ▼ Main.java
  ├─ Shell        ← REPL 모드
  ├─ FileRunner   ← 파일 실행 모드
  └─ Debugger     ← 디버그 모드
        │
  모두 공유
        ▼
     Pipeline    ← Assembler + Checker + Executor 파사드
```

---

## Shell — REPL 루프 (`shell/Shell.java`)

### 프롬프트 관리

```java
// Shell.java:16-17
private static final String PRIMARY      = ">>> ";   // 새 입력 시작
private static final String CONTINUATION = "... ";   // 멀티라인 진행 중
```

### REPL 루프 (`Shell.java:29-53`)

```java
public void run() {
    SubmissionBuffer buf = new SubmissionBuffer();
    while (true) {
        out.print(buf.isEmpty() ? PRIMARY : CONTINUATION);
        out.flush();

        String line = reader.readLine();
        if (line == null) break;                         // EOF (Ctrl+D)

        String trimmed = line.strip();
        if (buf.isEmpty() && isExitCommand(trimmed)) break;  // exit / quit
        if (buf.isEmpty() && trimmed.isEmpty()) continue;    // 빈 줄 무시

        buf.append(line);

        if (buf.isComplete()) {                          // 멀티라인 완성 판단
            pipeline.run(buf.text());
            buf.clear();
        }
    }
}
```

`exit`/`quit`는 **버퍼가 비어 있을 때만** 종료합니다. 멀티라인 입력 중 `exit`를 타이핑하면 문자열로 처리됩니다.

---

## SubmissionBuffer — 멀티라인 판정 (`shell/SubmissionBuffer.java`)

사용자가 입력한 줄들을 버퍼에 쌓고, **괄호·중괄호가 모두 균형을 이루는 시점**에 완성 신호를 줍니다.

### `isComplete()` 알고리즘 (`SubmissionBuffer.java:24-40`)

```java
public boolean isComplete() {
    boolean inString   = false;
    int     parenDepth = 0;    // ( ) 깊이
    int     braceDepth = 0;    // { } 깊이

    for (char c : buffer.toString().toCharArray()) {
        switch (c) {
            case '"' -> inString = !inString;                                  // 문자열 토글
            case '(' -> { if (!inString) parenDepth++; }
            case ')' -> { if (!inString) { parenDepth--; if (parenDepth < 0) return true; } }
            case '{' -> { if (!inString) braceDepth++; }
            case '}' -> { if (!inString) { braceDepth--; if (braceDepth < 0) return true; } }
        }
    }
    return !inString && parenDepth == 0 && braceDepth == 0;
}
```

**설계 결정:**

| 상황 | 처리 |
|---|---|
| `"..."` 안의 `(`, `{` | `inString = true` 동안 깊이 무시 |
| 불균형 닫기 `)`/`}` | depth가 음수 → 즉시 `true` 반환, Parser가 문법 오류 보고 |
| 미종료 문자열 `"abc` | `!inString` 조건으로 `false` 반환 → 계속 대기 |

**예시:**

```
>>> if (a > b) {
... print a;        ← braceDepth = 1, 아직 완성 아님
... }               ← braceDepth = 0, 완성!
>>> 
```

---

## Pipeline — 3단계 파사드 (`shell/Pipeline.java`)

### 역할: **Facade 패턴**

`Shell`, `FileRunner`, `Debugger`가 각자 Assembler/Checker/Executor를 다루지 않도록, 세 단계를 하나의 `run()` 메서드로 추상화합니다.

```java
// Pipeline.java:36-53
public void run(String source) {
    try {
        List<Stmt> program = assembler.assemble(source);   // Stage 1
        CheckResult cr = checker.check(program);           // Stage 2

        printDiagnostics(cr.warnings);                     // 경고는 항상 출력

        if (!cr.ok()) {
            printDiagnostics(cr.errors);
            return;                                        // 오류 있으면 실행 중단
        }

        executor.run(program);                             // Stage 3
    } catch (CodeFabError e) {
        log.error(e);
        out.println(e.getMessage());
    }
}
```

오류 단락(short-circuit) 흐름:
```
assemble()  →  LexError/ParseError 시 catch 처리
check()     →  cr.ok() == false 시 return (실행 안 함)
executor()  →  RuntimeError 시 catch 처리
```

### 접근자 메서드 (`Pipeline.java:61-65`)

```java
public Assembler  assembler() { return assembler; }
public Checker    checker()   { return checker; }
public Executor   executor()  { return executor; }
```

`FileRunner`와 `Debugger`는 이 접근자를 통해 내부 컴포넌트에 직접 접근해 종료코드 제어나 리스너 등록을 합니다.

---

## FileRunner — 파일 실행 모드 (`shell/FileRunner.java`)

`pipeline.run()` 대신 세 단계를 **직접 호출**하는 이유: 오류 발생 위치마다 `System.exit(1)`을 명시적으로 제어하기 위해서입니다.

```java
// FileRunner.java (핵심 흐름)
String source = Files.readString(Path.of(filePath));  // IOException → exit(1)

List<Stmt> program = pipeline.assembler().assemble(source); // LexError/ParseError → exit(1)
CheckResult cr = pipeline.checker().check(program);

// 경고 출력, 오류 있으면 exit(1)
for (Diagnostic d : cr.warnings) out.println(d);
if (!cr.ok()) { cr.errors.forEach(out::println); System.exit(1); }

pipeline.executor().run(program);                     // RuntimeError → exit(1)
```

**종료 코드:**
- `System.exit(0)` — 성공 (묵시적)
- `System.exit(1)` — 파일 읽기 오류, 파스 오류, 검사 오류, 런타임 오류

---

## Debugger — Observer 구현 (`shell/debug/Debugger.java`)

### Observer 패턴

```
Executor (Observable / Subject)
  └─ execute(stmt) 호출 시 → listener.onStatement(stmt)
  └─ 스코프 진입/이탈 시  → listener.onEnterScope() / onExitScope()

Debugger (Observer / ConcreteObserver)
  └─ ExecutionListener 구현
  └─ executor.setListener(this) 로 등록 (Debugger.java:98)
```

`Executor`는 `listener` 필드만 가지며 Debugger의 존재를 모릅니다. 인터페이스를 통한 느슨한 결합입니다.

### 3가지 실행 모드 (`Debugger.java:34-38`)

```java
private enum Mode { STEP, NEXT, CONTINUE }

private Mode mode       = Mode.STEP;   // 초기: 모든 구문에서 정지
private int  scopeDepth = 0;
private int  pauseDepth = 0;
```

| 모드 | 정지 조건 | 용도 |
|---|---|---|
| `STEP` | 모든 구문 | 한 줄씩 진행 |
| `NEXT` | `scopeDepth <= pauseDepth` | 블록 내부로 들어가지 않고 같은 레벨에서만 |
| `CONTINUE` | breakpoints에 줄 번호 있을 때 | 지정 지점까지 빠르게 이동 |

### 정지 여부 판단 (`Debugger.java:120-130`)

```java
@Override
public void onStatement(Stmt stmt) {
    // 블록/for는 컨테이너 — 정지에서 제외해 이중 정지 방지
    if (stmt instanceof Stmt.Block || stmt instanceof Stmt.For) return;

    int line = lineOf(stmt);

    boolean shouldPause = switch (mode) {
        case STEP     -> true;
        case NEXT     -> scopeDepth <= pauseDepth;
        case CONTINUE -> line >= 0 && breakpoints.contains(line);
    };

    if (shouldPause) {
        pauseDepth = scopeDepth;
        commandLoop(line, bpHit);    // 사용자 명령 대기
    }
}
```

`Stmt.Block`과 `Stmt.For`를 제외하는 이유: for 문이 실행될 때 Executor가 `visitFor()` → `execute(body)` → `visitBlock()` → `execute(각 stmt)` 순으로 콜백을 발생시키는데, for 자체에서 한 번, body의 첫 구문에서 또 한 번 정지하면 동일 위치에서 이중 정지가 발생하기 때문입니다.

### 명령어 목록 (`Debugger.java:164-212`)

| 명령 | 인자 | 동작 |
|---|---|---|
| `step` | — | STEP 모드로 다음 구문 이동 |
| `next` | — | NEXT 모드로 같은 스코프 레벨 이동 |
| `continue` | — | CONTINUE 모드로 breakpoint까지 진행 |
| `break` | `<줄번호>` | 해당 줄에 breakpoint 설정 |
| `breakpoints` | — | 설정된 breakpoint 목록 출력 |
| `remove` | `<줄번호>` | breakpoint 제거 |
| `watch` | `<변수명>` | 정지 시 변수 값 자동 표시 |
| `unwatch` | `<변수명>` | watch 해제 |
| `watches` | — | 현재 watch 중인 변수들 표시 |
| `inspect` | — | 전체 스코프 체인 변수 덤프 |
| `help` | — | 명령어 목록 출력 |

### inspect 구현 (`Debugger.java:228-253`)

```java
private void printInspect() {
    List<Environment> chain = new ArrayList<>();
    for (Environment e = pipeline.executor().getEnvironment(); e != null; e = e.enclosing()) {
        chain.add(e);                         // 안쪽 → 전역 순서로 수집
    }
    // 전역 제외 로컬 스코프
    for (int i = 0; i < chain.size() - 1; i++) {
        chain.get(i).snapshot().forEach((k, v) ->
            out.println("[로컬] " + k + " = " + Executor.stringify(v) + " (" + displayType(v) + ")"));
    }
    // 전역 스코프
    chain.get(chain.size() - 1).snapshot().forEach((k, v) ->
        out.println("[전역] " + k + " = " + Executor.stringify(v) + " (" + displayType(v) + ")"));
}
```

`Environment.snapshot()`은 읽기 전용 맵을 반환해 Debugger가 런타임 상태를 변경하지 못하도록 합니다 (`Environment.java:74-76`).

---

## Logger — verbose 게이팅 (`log/Logger.java`)

```java
// Logger.java:14-39
public class Logger {
    private boolean verbose;
    private final PrintStream err;

    public Logger(boolean verbose) { this(verbose, System.err); }
    public Logger(boolean verbose, PrintStream err) {   // 테스트용 스트림 주입
        this.verbose = verbose;
        this.err = err;
    }

    public void executionStart()    { if (verbose) err("[exec] start"); }
    public void executionComplete() { if (verbose) err("[exec] done");  }
    public void error(CodeFabError e) { err(e.userMessage()); }  // 항상 출력
    public void diagnostic(String msg) { if (verbose) err(msg); }
}
```

**설계 원칙:**
- 실행 라이프사이클 로그(`[exec] start/done`)는 `--verbose` 플래그가 있을 때만 출력
- 오류는 verbose 여부와 관계없이 **항상** stderr에 출력
- `print` 구문 출력은 Logger를 거치지 않고 Executor → `System.out.println()` 직접 호출
- `PrintStream` 주입으로 테스트에서 `ByteArrayOutputStream`으로 교체 가능

---

## factory 래퍼 스크립트

### Bash (`factory` — Linux/macOS/MSYS)

```bash
# OS 감지 후 JAVA_HOME 결정
case "$(uname -s)" in
  Linux*)  JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/temurin-21.0.11}" ;;
  Darwin*) JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}" ;;
  MINGW*|MSYS*|CYGWIN*)  exec ./gradlew.bat "$@"; exit ;;
esac

# Gradle 실행 (인수 없으면 REPL, 있으면 --args 전달)
if [ $# -eq 0 ]; then
    ./gradlew -q --console=plain run
else
    ./gradlew -q --console=plain run --args="$*"
fi
```

인수가 없을 때 `--args=""`를 전달하면 빈 문자열 인수로 처리되어 Gradle이 실패합니다. 그래서 `$#`으로 분기합니다.

### Windows Batch (`factory.bat`)

```bat
chcp 65001 > nul           ← UTF-8 코드 페이지 설정 (한글 깨짐 방지)

if "%1"=="" (
    gradlew.bat -q --console=plain run
) else (
    gradlew.bat -q --console=plain run --args="%*"
)
```

`chcp 65001`은 Windows cmd.exe에서 UTF-8 출력을 가능하게 합니다. 없으면 한글이 CP949로 인코딩되어 깨집니다.
