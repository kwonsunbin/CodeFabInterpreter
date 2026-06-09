# CodeFab Interpreter

CodeFab 언어의 트리 탐색(tree-walking) 인터프리터입니다.  
소스코드를 **Assembler → Checker → Executor** 3단계 파이프라인으로 처리하며,  
대화형 REPL, 파일 실행, 단계별 디버그 총 3가지 실행 모드를 제공합니다.

---

## 실행 방법

### factory 래퍼 (권장)

`factory` 스크립트가 OS를 자동 감지해 알맞은 경로로 분기합니다:

| OS | 동작 |
|---|---|
| **Linux** | `gradlew` 실행, `JAVA_HOME` 미설정 시 `~/.jdks/temurin-21.0.11` 기본값 사용 |
| **macOS** | `gradlew` 실행, `JAVA_HOME` 미설정 시 `/usr/libexec/java_home -v 21`로 자동 탐색 |
| **Windows (Git Bash / MSYS / Cygwin)** | `gradlew.bat`으로 위임 |
| **Windows (cmd / PowerShell)** | `factory.bat` 사용 |

> **JDK 21이 없어도 됩니다.** foojay 툴체인 리졸버가 설정되어 있어, 로컬에 JDK 21이 없으면
> Gradle이 자동으로 다운로드합니다 (Gradle 구동용 JDK 17+만 있으면 됨).

REPL / 파일 / 디버그 3개 모드 모두 **Linux와 Windows(cmd)에서 실제 실행 검증**되었습니다.

```bash
# 대화형 REPL 실행
./factory

# 파일 모드 — 스크립트 실행
./factory run examples/hello.txt

# 디버그 모드 — Stmt 단위 점검
./factory debug examples/hello.txt

# 상세 실행 로그 — 어느 모드든 사용 가능
./factory run examples/hello.txt --verbose
```

Windows 네이티브 cmd / PowerShell:

```bat
factory.bat
factory.bat run examples\hello.txt
factory.bat debug examples\hello.txt
```

> `factory.bat`은 한글 깨짐 방지를 위해 콘솔 코드페이지를 UTF-8(`chcp 65001`)로 전환합니다.

### gradlew 직접 실행

```bash
# 대화형 REPL
JAVA_HOME=~/.jdks/temurin-21.0.11 ./gradlew run --console=plain -q

# 파일 모드
JAVA_HOME=~/.jdks/temurin-21.0.11 ./gradlew run --console=plain -q --args="run examples/hello.txt"

# 전체 테스트
JAVA_HOME=~/.jdks/temurin-21.0.11 ./gradlew test
```

---

## 아키텍처

```
[입력 소스]
    │
    ▼
┌──────────────────────────────────────────────────────┐
│                   Assembler Unit                     │
│   Lexer  ──▶  Parser  ──▶  AST (Expr / Stmt)        │
│  (Token화)   (구문 분석)    (문법 트리)                │
└──────────────────────────────────────────────────────┘
    │  List<Stmt>
    ▼
┌──────────────────────────────────────────────────────┐
│                   Checker Unit                       │
│   DFS로 AST 탐색 — 3가지 작업을 한 번에 수행           │
│   ① 의미 오류 일괄 수집 (모든 오류를 한 패스에 보고)    │
│   ② 정적 바인딩: 변수마다 depth 계산 후 AST에 기록     │
│   ③ 상수 폴딩: 확정 수식 미리 계산 후 AST에 기록       │
└──────────────────────────────────────────────────────┘
    │  CheckResult (오류 없을 때만)
    ▼
┌──────────────────────────────────────────────────────┐
│                   Executor Unit                      │
│   DFS로 AST 탐색 — 실제 실행                          │
│   · Stmt: 부수 효과 수행 (환경 변경·출력)              │
│   · Expr: 값 계산 후 반환                             │
│   · foldedValue가 있으면 자식 평가 스킵 (상수 폴딩 활용)│
└──────────────────────────────────────────────────────┘
```

### 핵심 설계 규칙

| 규칙 | 내용 |
|---|---|
| **Expr-Stmt 분리** | `Expr`는 절대 `Stmt`를 자식으로 가질 수 없음 (sealed class + 반사 테스트로 강제) |
| **Visitor 패턴** | Checker·Executor 모두 Visitor로 AST를 재귀 DFS 탐색 |
| **공유 Environment** | Checker가 `defined`·`depth`를 기록하고, Executor가 같은 객체에 실제 값을 채움 |
| **에러 생존** | 어떤 단계의 에러도 REPL 루프를 죽이지 않음 |
| **오류 일괄 수집** | Checker는 첫 오류에서 멈추지 않고 전체 AST를 순회해 모든 오류를 한 번에 보고 |

---

## 패키지 구조

```
src/main/java/org/example/codefab/
├── Main.java                        # 진입점 (REPL / 파일 / 디버그 모드 분기)
├── token/
│   ├── TokenType.java               # 31가지 토큰 타입 열거형
│   └── Token.java                   # record(type, origin, value, line)
├── ast/
│   ├── Expr.java                    # 표현식 노드 (sealed + Visitor + Builder + foldedValue)
│   └── Stmt.java                    # 구문 노드 (sealed + Visitor)
├── assembler/
│   ├── Lexer.java                   # 소스 → Token 리스트
│   ├── Parser.java                  # Token 리스트 → AST (재귀 하강)
│   └── Assembler.java               # Lexer + Parser 파사드
├── checker/
│   ├── Checker.java                 # 정적 분석: 의미 검증 + 정적 바인딩 + 상수 폴딩
│   ├── CheckResult.java             # 에러·경고 목록
│   └── Diagnostic.java              # 진단 메시지 record (ERROR / WARNING)
├── executor/
│   ├── Executor.java                # 런타임 실행 (DFS Visitor)
│   ├── Environment.java             # 런타임 변수 환경 (스코프 체인, Checker와 공유)
│   ├── CodeFabCallable.java         # 호출 가능 런타임 값 인터페이스
│   ├── CodeFabFunction.java         # 사용자 정의 함수 (클로저 캡처)
│   └── ExecutionListener.java       # 디버거 연결용 Observer 인터페이스
├── error/
│   ├── CodeFabError.java            # 추상 기반 예외 ([stage] line N: msg 포맷)
│   ├── LexError.java
│   ├── ParseError.java
│   ├── RuntimeError.java
│   └── ReturnException.java         # return 제어 흐름 신호 (오류 아님)
├── log/
│   └── Logger.java                  # 생명주기 로그 (verbose 게이팅, DI)
└── shell/
    ├── Shell.java                   # REPL 루프 (>>> / ... 프롬프트)
    ├── FileRunner.java              # 파일 모드 (줄번호 오류 출력 후 즉시 종료)
    ├── Pipeline.java                # Assembler → Checker → Executor 파사드
    ├── SubmissionBuffer.java        # 괄호·중괄호 균형 감지 (멀티라인 입력)
    └── debug/
        ├── Debugger.java            # 디버그 모드 (step/break/watch/inspect)
        └── LineExtractor.java       # AST 노드 줄번호 추출 (Visitor)
```

---

## 언어 사양

### 문법 (EBNF)

```ebnf
program     = statement* EOF ;

statement   = funcDecl | varDecl | arrayDecl | returnStmt
            | ifStmt | forStmt | printStmt | block | exprStmt ;

funcDecl    = "Func" IDENTIFIER "(" params? ")" block ;
params      = IDENTIFIER ( "," IDENTIFIER )* ;
varDecl     = "var" IDENTIFIER ( "=" ( "Array" "(" expression ")" | expression ) )? ";" ;
arrayDecl   = "var" IDENTIFIER "[" expression "]" ";" ;
returnStmt  = "return" expression? ";" ;
ifStmt      = "if" "(" expression ")" statement ( "else" statement )? ;
forStmt     = "for" "(" ( varDecl | exprStmt | ";" ) expression? ";" expression? ")" block ;
printStmt   = "print" expression ";" ;
block       = "{" statement* "}" ;
exprStmt    = expression ";" ;

expression  = assignment ;
assignment  = IDENTIFIER "=" assignment
            | IDENTIFIER "[" expression "]" "=" assignment
            | logic_or ;
logic_or    = logic_and ( "or" logic_and )* ;
logic_and   = comparison ( "and" comparison )* ;
comparison  = term ( ( ">" | ">=" | "<" | "<=" | "==" | "!=" ) term )* ;
term        = factor ( ( "+" | "-" ) factor )* ;
factor      = unary ( ( "*" | "/" ) unary )* ;
unary       = ( "-" | "!" ) unary | call ;
call        = primary ( "(" arguments? ")" )* ;
primary     = NUMBER | STRING | "true" | "false"
            | IDENTIFIER ( "[" expression "]" )?
            | "(" expression ")" ;

arguments   = expression ( "," expression )* ;
```

### 지원 토큰

| 분류 | 토큰 |
|---|---|
| 구분자 | `(` `)` `{` `}` `[` `]` `;` `,` |
| 산술 연산자 | `+` `-` `*` `/` |
| 비교 연산자 | `>` `>=` `<` `<=` `==` `!=` |
| 논리 연산자 | `and` `or` `!` |
| 할당 | `=` |
| 키워드 | `var` `if` `else` `for` `print` `true` `false` `Func` `return` `Array` |
| 리터럴 | `NUMBER` (double) `STRING` |

### AST 노드

**Expr 노드** (값을 반환, `Expr.Visitor<Object>`):

| 노드 | 설명 | 예시 |
|---|---|---|
| `Binary` | 산술: `+` `-` `*` `/` | `a + b` |
| `Logical` | 논리 단락: `and` `or` | `x and y` |
| `Comparison` | 비교: `>` `>=` `<` `<=` `==` `!=` | `a >= b` |
| `Unary` | 단항: `-x` `!x` | `!flag` |
| `Grouping` | 괄호 | `(1 + 2)` |
| `Literal` | 리터럴 값 (double / String / Boolean / null) | `42` `"hi"` |
| `Variable` | 변수 읽기 (depth 기록) | `x` |
| `Assign` | 할당 표현식 (depth 기록) | `x = 5` |
| `Call` | 함수 호출 | `add(1, 2)` |
| `ArrayGet` | 배열 읽기 | `arr[i]` |
| `ArraySet` | 배열 쓰기 | `arr[i] = 7` |

**Stmt 노드** (동작 수행, `Stmt.Visitor<Void>`):

| 노드 | 설명 | 예시 |
|---|---|---|
| `Var` | 변수 선언 | `var x = 10;` |
| `If` | 조건 분기 | `if (x > 0) { ... }` |
| `For` | C 스타일 반복문 | `for (var i = 0; i < n; i = i + 1)` |
| `Print` | 출력 | `print x;` |
| `Block` | 블록 스코프 | `{ ... }` |
| `Expression` | 표현식 구문 | `add(3, 4);` |
| `Function` | 함수 선언 | `Func add(a, b) { ... }` |
| `Return` | 반환 | `return a + b;` |
| `ArrayDecl` | 배열 선언 | `var arr[3];` |

---

## 동작 예시

### 기본 산술·변수

```
>>> var a = 5;
>>> var b = 10;
>>> print a + b;
15
>>> print 1 + 2 * 3;
7
>>> print (1 + 2) * 3;
9
```

### 조건문·반복문

```
>>> if (true) print "yes"; else print "no";
yes

>>> for (var i = 0; i < 3; i = i + 1) {
...   print i;
... }
0
1
2
```

### 함수 선언·호출·재귀

```
>>> Func add(a, b) {
...   return a + b;
... }
>>> print add(3, 7);
10

>>> Func fact(n) {
...   if (n <= 1) return 1;
...   return n * fact(n - 1);
... }
>>> print fact(5);
120
```

### 배열

```
>>> var arr = Array(3);
>>> arr[0] = 10;
>>> arr[1] = 20;
>>> arr[2] = 30;
>>> print arr[1];
20
>>> print arr;
[10, 20, 30]
```

### 블록 스코프 & 변수 섀도잉

```
>>> var x = "global";
>>> {
...   var x = "inner";
...   print x;
... }
inner
>>> print x;
global
```

### 파일 모드

```bash
./factory run examples/hello.txt
```

오류 발생 시 줄 번호와 함께 출력 후 즉시 종료:
```
[runtime] line 5: Division by zero.
```

### 디버그 모드

```bash
./factory debug examples/hello.txt
```

```
[DEBUG] line 1 | var a = 0;
> step
[DEBUG] line 2 | var start = 0;
> watch a
[WATCH] 'a' 감시 등록
> continue
[WATCH] a = 0
[WATCH] a = 1
...
```

**디버그 명령어:**

| 명령 | 설명 |
|---|---|
| `step` | 현재 Stmt 실행 후 다음 Stmt에서 정지 |
| `next` | 블록·함수 내부로 진입하지 않고 다음으로 이동 |
| `continue` | 다음 breakpoint 또는 종료까지 계속 실행 |
| `break <줄번호>` | breakpoint 설정 (공백/주석 줄이면 가장 가까운 실행 라인으로 자동 이동) |
| `breakpoints` | 설정된 breakpoint 목록 출력 |
| `remove <줄번호>` | breakpoint 해제 |
| `watch <변수>` | 변수 감시 등록 (정지마다 자동 출력) |
| `unwatch <변수>` | 감시 해제 |
| `watches` | 감시 중인 변수 목록·값 출력 |
| `inspect` | 현재 스코프 전체 변수 출력 (`[로컬]` / `[전역]` 구분) |
| `help` | 명령어 목록 출력 |

---

## 에러 메시지

| 단계 | 원인 | 메시지 |
|---|---|---|
| Lex | 미종료 문자열 | `Unterminated string.` |
| Lex | 알 수 없는 문자 | `Unexpected character: 'x'` |
| Parse | `;` 누락 | `Expect ';' after value.` |
| Parse | `)` 누락 | `Expect ')' after expression.` |
| Parse | 잘못된 할당 대상 | `Invalid assignment target.` |
| Parse | 파라미터 이름 중복 | `Duplicate parameter name 'x'.` |
| Parse | 함수 본문 return 누락 | `Function 'f' must contain a return statement.` |
| Check | 자기 참조 초기화 | `Can't read local variable in initializer.` |
| Check | 같은 스코프 중복 선언 | `Already a variable with this name in this scope.` |
| Check | 미선언 변수 참조 | `Undefined variable 'name'.` |
| Check | 함수 외부 return | `Can't return from top-level code.` |
| Check | 인자 개수 불일치 | `'f' expects N argument(s) but got M.` |
| Check | 비함수 호출 | `'x' is not a function.` |
| Check | 배열 아닌 대상 인덱싱 | `'x' is not an array.` |
| Check | 배열 인덱스 타입 오류 | `Array index must be a number.` |
| Check | 배열 인덱스 범위 초과 | `Array index N is out of bounds for array of size M.` |
| Check | 배열 크기 타입 오류 | `Array size must be a number.` |
| Runtime | 미정의 변수 참조 | `Undefined variable 'name'.` |
| Runtime | 타입 불일치 (`+`) | `Operands must be two numbers or two strings.` |
| Runtime | 0으로 나누기 | `Division by zero.` |
| Runtime | 비함수 호출 | `Can only call functions.` |
| Runtime | 인자 개수 불일치 | `Expected N argument(s) but got M.` |
| Runtime | 배열 아닌 대상 인덱싱 | `'x' is not an array.` |
| Runtime | 배열 인덱스 범위 초과 | `Array index N is out of bounds for array of size M.` |
| Runtime | 배열 인덱스 타입 오류 | `Array index must be a number.` |
| Runtime | 배열 크기 타입 오류 | `Array size must be a non-negative integer.` |

---

## 테스트

총 **352개 테스트**, 10개 클래스로 구성:

| 테스트 클래스 | 건수 | 내용 |
|---|---|---|
| `LexerTest` | 25 | 토큰 타입·리터럴·줄번호·주석, FUNC/RETURN/ARRAY/BRACKET 토큰 |
| `ParserTest` | 73 | 연산자 우선순위·결합 방향, 함수·배열·호출 파싱, 아키텍처 규칙 반사 테스트 |
| `CheckerTest` | 48 | 의미 검증 (함수·배열·변수 오류 전종), 정적 바인딩 depth, 다중 오류 수집 |
| `ExecutorTest` | 98 | 산술·논리·비교 실행, 변수·스코프·블록, 함수·재귀·배열, 런타임 오류 |
| `EndToEndTest` | 68 | 전체 파이프라인 통합 (정상 동작 + 함수·배열·재귀·REPL 세션) |
| `OptimizationIntegrationTest` | 7 | 정적 바인딩 O(1) 검증, 상수 폴딩 계산 횟수 0회 검증 (Test Double) |
| `EnvironmentTest` | 8 | 변수 정의·조회·할당, 스코프 체인 탐색 |
| `SubmissionBufferTest` | 12 | 괄호 균형 감지, 문자열 내부 무시, 멀티라인 입력 시나리오 |
| `PipelineTest` | 5 | Assembler→Checker→Executor 파이프라인 통합 |
| `LoggerTest` | 8 | verbose 게이팅, stderr 출력, 에러 메시지 포맷 |

```bash
JAVA_HOME=~/.jdks/temurin-21.0.11 ./gradlew test
```

---

## 특이사항

- **배열 선언 문법 2종**: `var arr = Array(3);` 과 `var arr[3];` 모두 지원
- **배열 오류 이중 검사**: 리터럴 인덱스는 Checker가 정적으로 차단, 동적 인덱스는 Executor가 런타임에 검사
- **정적 바인딩**: Checker가 변수마다 스코프 거리(`depth`)를 AST에 미리 기록 → Executor가 `getAt(depth)` O(1)로 접근
- **상수 폴딩**: 런타임 전 확정되는 수식을 Checker가 계산해 `foldedValue`에 기록 → Executor가 자식 평가 생략
- **클로저**: 함수 선언 시 `captureForClosure()`로 현재 스코프 체인을 스냅샷 → 블록 안 재귀 호출에서도 정적 바인딩이 올바르게 동작
- **오류 일괄 수집**: Checker는 예외를 던지지 않고 전체 AST를 끝까지 순회해 모든 의미 오류를 한 번에 보고
- **REPL 멀티라인**: `SubmissionBuffer`가 괄호·중괄호 균형을 추적해 함수·블록을 여러 줄로 입력 가능
- **breakpoint 자동 이동**: 공백·주석 줄에 breakpoint 설정 시 가장 가까운 실행 가능 라인으로 자동 보정
- **REPL 프롬프트**: 새 입력 `>>>` / 연속 입력 `...`
