# 01. 전체 아키텍처 개요

## 전체 실행 흐름

CodeFab 인터프리터는 **3단계 파이프라인** 구조입니다.

```
소스 코드 (String)
      │
      ▼  Stage 1
  Assembler                  ← Lexer + Parser 파사드
      │  List<Token>
      │  List<Stmt> (AST)
      │
      ▼  Stage 2
   Checker                   ← 정적 의미 분석 (Visitor 패턴)
      │  CheckResult
      │  오류 있으면 실행 중단
      │
      ▼  Stage 3
   Executor                  ← Tree-walking 인터프리터 (Visitor 패턴)
      │
      ▼
   출력 / 사이드 이펙트
```

> 자세한 패키지별 설명: [03-frontend.md](03-frontend.md), [04-ast-visitor.md](04-ast-visitor.md),
> [05-checker.md](05-checker.md), [06-executor.md](06-executor.md)

---

## 패키지 구조

```
src/main/java/org/example/codefab/
│
├── Main.java                        ← 진입점: 모드 분기 + 의존성 주입
│
├── token/
│   ├── Token.java                   ← 불변 레코드: (type, origin, value, line)
│   └── TokenType.java               ← 28개 토큰 타입 열거
│
├── assembler/
│   ├── Lexer.java                   ← 문자 → 토큰
│   ├── Parser.java                  ← 토큰 → AST (재귀 하강)
│   └── Assembler.java               ← Lexer + Parser 파사드
│
├── ast/
│   ├── Expr.java                    ← 표현식 노드 8종 (sealed + Visitor)
│   └── Stmt.java                    ← 구문 노드 6종 (sealed + Visitor)
│
├── checker/
│   ├── Checker.java                 ← 정적 분석 (Stmt.Visitor 구현)
│   ├── Scope.java                   ← 단일 렉시컬 스코프 (DECLARING/DEFINED 상태)
│   ├── CheckResult.java             ← 진단 컨테이너
│   └── Diagnostic.java              ← 진단 레코드 (severity, line, message)
│
├── executor/
│   ├── Executor.java                ← Tree-walking 인터프리터 (두 Visitor 구현)
│   ├── Environment.java             ← 런타임 변수 저장소 (부모 체이닝)
│   └── ExecutionListener.java       ← Observer 인터페이스 (디버거 연동)
│
├── error/
│   ├── CodeFabError.java            ← 추상 베이스: line + stage()
│   ├── LexError.java                ← stage = "lex"
│   ├── ParseError.java              ← stage = "parse"
│   └── RuntimeError.java           ← stage = "runtime"
│
├── log/
│   └── Logger.java                  ← verbose 게이팅, stderr 출력
│
└── shell/
    ├── Shell.java                   ← REPL 루프 (>>> / ... 프롬프트)
    ├── SubmissionBuffer.java        ← 멀티라인 판정 (괄호 균형)
    ├── Pipeline.java                ← 3단계 파사드
    ├── FileRunner.java              ← 파일 실행 + 종료 코드 제어
    └── debug/
        └── Debugger.java            ← Observer 구현: step/next/continue/breakpoint
```

---

## 3가지 실행 모드

`Main.java:26-49`에서 CLI 인수를 파싱해 모드를 분기합니다.

```java
// Main.java:26-49
boolean verbose = argList.contains("--verbose");         // 전역 플래그

if (effectiveArgs.size() >= 2 && effectiveArgs.get(0).equals("run")) {
    new FileRunner(pipeline, System.out).run(effectiveArgs.get(1));   // 파일 모드

} else if (effectiveArgs.size() >= 2 && effectiveArgs.get(0).equals("debug")) {
    new Debugger(pipeline, System.in, System.out).runFile(...);       // 디버그 모드

} else {
    new Shell(pipeline, System.in, System.out).run();                 // REPL 모드
}
```

| 모드 | 실행 명령 | 클래스 | 특징 |
|---|---|---|---|
| **REPL** | `./factory` | `Shell` | 대화형 프롬프트, 세션 상태 유지 |
| **파일 실행** | `./factory run <file>` | `FileRunner` | 파일 읽기→실행→종료코드 반환 |
| **디버그** | `./factory debug <file>` | `Debugger` | 구문 단위 정지, 변수 검사 |

모든 모드가 **동일한 `pipeline` 인스턴스**를 공유합니다.

---

## 컴포넌트 조립 — 의존성 주입 (DI)

`Main.java:30-34`에서 모든 컴포넌트를 수동 조립합니다. 프레임워크 없이 생성자 주입만 사용합니다.

```java
// Main.java:30-34
Logger    log      = new Logger(verbose);           // verbose 플래그 주입
Assembler assembler = new Assembler();
Checker   checker  = new Checker();
Executor  executor = new Executor(log);             // Logger 주입
Pipeline  pipeline = new Pipeline(assembler, checker, executor, log, System.out);
//                                                    └─ 세 컴포넌트 + 출력 스트림 주입
```

**주입 효과:**
- `Logger`는 `PrintStream err`도 주입받아 테스트 시 스트림 교체 가능 (`Logger.java:24`)
- `Executor`에 `Logger`가 주입되어 verbose 라이프사이클 로그를 출력 (`Executor.java:42-44`)
- `Pipeline`이 세 스테이지를 들고 있으므로 `FileRunner`와 `Debugger`가 `pipeline.assembler()` 등으로 접근 가능

> 자세한 패턴 분석: [08-design-patterns.md](08-design-patterns.md)

---

## REPL 세션 상태 유지

REPL은 한 번의 입력이 완료될 때마다 `pipeline.run(source)`를 호출합니다. 이때 `Checker`와 `Executor`는 인스턴스가 교체되지 않으므로, 각자의 전역 스코프가 유지됩니다.

- **Checker**: `private final Scope globalScope` (`Checker.java:29`) — 이전 제출에서 선언한 변수가 다음 제출에서도 보임
- **Executor**: `private final Environment globalEnv` (`Executor.java:24`) — 이전 제출의 변수 값이 런타임에서도 유지
