# CodeFab Interpreter

CodeFab 언어의 트리 탐색(tree-walking) 인터프리터입니다.  
프롬프트 쉘(REPL)을 통해 한 줄씩 코드를 입력하면 **Assembler → Checker → Executor** 3단계 파이프라인을 거쳐 즉시 실행됩니다.

---

## 실행 방법

> **선행 조건:** `java` 명령이 PATH에 없는 경우 `JAVA_HOME`을 설정해야 합니다.

```bash
# 대화형 REPL 실행
JAVA_HOME=~/.jdks/temurin-21.0.11 ./gradlew run --console=plain -q

# 상세 실행 로그(verbose) 포함
JAVA_HOME=~/.jdks/temurin-21.0.11 ./gradlew run --console=plain -q --args="--verbose"

# 전체 테스트 실행
JAVA_HOME=~/.jdks/temurin-21.0.11 ./gradlew test
```

REPL 예시:
```
CodeFab Interpreter — type 'exit' to quit.
>>> var a = 5;
>>> var b = 10;
>>> print a + b;
15
>>> exit
Bye!
```

---

## 아키텍처

```
[입력 소스]
    │
    ▼
┌─────────────────────────────────────┐
│          Assembler Unit             │
│  Lexer  ──▶  Parser  ──▶  AST      │
│ (Token화)   (구문 분석)  (문법 트리)  │
└─────────────────────────────────────┘
    │  List<Stmt>
    ▼
┌─────────────────────────────────────┐
│          Checker Unit               │
│  AST를 DFS로 탐색하여               │
│  정적 의미 규칙 검증 (에러 수집)     │
└─────────────────────────────────────┘
    │  CheckResult (에러 없을 때만)
    ▼
┌─────────────────────────────────────┐
│          Executor Unit              │
│  AST를 DFS로 탐색하여               │
│  실제 실행 및 출력                   │
└─────────────────────────────────────┘
```

### 핵심 설계 규칙

| 규칙 | 내용 |
|---|---|
| **Expr-Stmt 분리** | `Expr` 노드는 절대 `Stmt`를 자식으로 가질 수 없음 (반사(reflection) 테스트로 강제) |
| **DFS 탐색** | Checker와 Executor 모두 Visitor 패턴으로 AST를 재귀 DFS 탐색 |
| **지속 환경** | 전역 Scope와 Environment가 REPL 세션 동안 유지됨 |
| **에러 생존** | 어떤 단계의 에러도 REPL 루프를 죽이지 않음 |

---

## 패키지 구조

```
src/main/java/org/example/codefab/
├── Main.java                        # 진입점
├── token/
│   ├── TokenType.java               # 22가지 토큰 타입 열거형
│   └── Token.java                   # record(type, origin, value, line)
├── ast/
│   ├── Expr.java                    # 표현식 노드 (Sealed class + Visitor)
│   └── Stmt.java                    # 구문 노드 (Sealed class + Visitor)
├── assembler/
│   ├── Lexer.java                   # 소스 → Token 리스트
│   ├── Parser.java                  # Token 리스트 → AST
│   └── Assembler.java               # Lexer + Parser 파사드
├── checker/
│   ├── Scope.java                   # 단일 어휘 스코프 (DECLARING / DEFINED)
│   ├── Diagnostic.java              # 진단 메시지 record (ERROR / WARNING)
│   ├── CheckResult.java             # 에러·경고 목록
│   └── Checker.java                 # 정적 분석 (DFS Visitor)
├── executor/
│   ├── Environment.java             # 런타임 변수 환경 (enclosing 체인)
│   └── Executor.java                # 런타임 실행 (DFS Visitor)
├── log/
│   └── Logger.java                  # 생명주기 로그 (verbose 게이팅)
├── shell/
│   ├── SubmissionBuffer.java        # 괄호/중괄호 균형 감지 (연속 입력)
│   └── Shell.java                   # REPL 루프 (>>> / ... 프롬프트)
└── error/
    ├── CodeFabError.java            # 추상 기반 예외
    ├── LexError.java
    ├── ParseError.java
    └── RuntimeError.java
```

---

## 언어 사양

### 문법 (EBNF)

```ebnf
program     = statement* EOF ;

statement   = varDecl | ifStmt | forStmt | printStmt | block | exprStmt ;

varDecl     = "var" IDENTIFIER ( "=" expression )? ";" ;
ifStmt      = "if" "(" expression ")" statement ( "else" statement )? ;
forStmt     = "for" "(" ( varDecl | exprStmt | ";" ) expression? ";" expression? ")" block ;
printStmt   = "print" expression ";" ;
block       = "{" statement* "}" ;
exprStmt    = expression ";" ;

expression  = assignment ;
assignment  = IDENTIFIER "=" assignment | logic_or ;
logic_or    = logic_and ( "or" logic_and )* ;
logic_and   = comparison ( "and" comparison )* ;
comparison  = term ( ( ">" | "<" ) term )* ;
term        = factor ( ( "+" | "-" ) factor )* ;
factor      = unary ( ( "*" | "/" ) unary )* ;
unary       = "-" unary | primary ;
primary     = NUMBER | STRING | "true" | "false" | IDENTIFIER | "(" expression ")" ;
```

### 지원 토큰

| 분류 | 토큰 |
|---|---|
| 구분자 | `(` `)` `{` `}` `;` |
| 산술 연산자 | `+` `-` `*` `/` |
| 비교 연산자 | `>` `<` |
| 논리 연산자 | `and` `or` |
| 할당 | `=` |
| 키워드 | `var` `if` `else` `for` `print` `true` `false` |
| 리터럴 | `NUMBER` (double) `STRING` |

### AST 노드

**Expr 노드** (값을 반환):

| 노드 | 설명 |
|---|---|
| `Binary` | 산술: `+` `-` `*` `/` |
| `Logical` | 논리 단락: `and` `or` |
| `Comparison` | 비교: `>` `<` |
| `Unary` | 단항 부정: `-x` |
| `Grouping` | 괄호: `(expr)` |
| `Literal` | 리터럴 값 |
| `Variable` | 변수 읽기 |
| `Assign` | 할당 표현식 |

**Stmt 노드** (동작 수행):

| 노드 | 설명 |
|---|---|
| `Var` | 변수 선언 |
| `If` | 조건 분기 |
| `For` | C 스타일 반복문 |
| `Print` | 출력 |
| `Block` | 블록 스코프 |
| `Expression` | 표현식 구문 |

---

## 동작 예시

### 기본 산술 및 변수

```
>>> var a = 5;
>>> var b = 10;
>>> print a + b;
15
>>> print 1 + 2 * 3;
7
>>> print (1 + 2) * 3;
9
>>> print -3 + 2;
-1
```

### 숫자 출력 형식

```
>>> print 5.0;
5
>>> print 3.14;
3.14
```

### 문자열 연결

```
>>> print "Hello, " + "CodeFab!";
Hello, CodeFab!
```

### 비교 및 논리

```
>>> print 1 < 2;
true
>>> print 3 > 5;
false
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

### 조건문

```
>>> if (true) print "yes"; else print "no";
yes
```

### for 반복문

```
>>> for (var i = 0; i < 3; i = i + 1) {
...   print i;
... }
0
1
2
```

### 에러 처리 (REPL 생존)

```
>>> print notDefined;
[runtime] line 1: Undefined variable 'notDefined'.
>>> print 1 + "hi";
[runtime] line 1: Operands must be two numbers or two strings.
>>> print 1 + 2;
3
```

---

## 에러 메시지

| 단계 | 원인 | 메시지 |
|---|---|---|
| Lex | 미종료 문자열 | `Unterminated string.` |
| Parse | `;` 누락 | `Expect ';' after value.` |
| Parse | `)` 누락 | `Expect ')' after expression.` |
| Parse | 잘못된 할당 대상 | `Invalid assignment target.` |
| Parse | 표현식 예상 위치에 잘못된 토큰 | `Expect expression.` |
| Check | 자기 참조 초기화 | `Can't read local variable in initializer.` |
| Check | 같은 스코프 중복 선언 | `Already a variable with this name in this scope.` |
| Runtime | 미정의 변수 참조 | `Undefined variable 'name'.` |
| Runtime | 타입 불일치 (`+`) | `Operands must be two numbers or two strings.` |
| Runtime | 단항 부정에 비수치 | `Operand must be a number.` |
| Runtime | 0으로 나누기 | `Division by zero.` |

---

## 테스트

총 **80개 테스트**, 5개 클래스로 구성:

| 테스트 클래스 | 건수 | 내용 |
|---|---|---|
| `LexerTest` | 11 | 토큰 타입, 리터럴 값, 줄 번호, 주석 처리, 에러 케이스 |
| `ParserTest` | 20 | 연산자 우선순위, 결합 방향, Unary, for 절, dangling-else, 아키텍처 규칙 반사 테스트 |
| `CheckerTest` | 8 | 중복 선언, 자기 참조, 섀도잉 허용, for 스코프, 다중 에러 수집 |
| `SubmissionBufferTest` | 9 | 괄호 균형 감지, 문자열 내부 무시, 연속 입력 시나리오 |
| `EndToEndTest` | 32 | 정상 동작 전체 + 구문/정적/런타임 에러 검출 (사용자 테스트 스크립트 기반) |

```bash
JAVA_HOME=~/.jdks/temurin-21.0.11 ./gradlew test
```

---

## 확장 포인트

- **연산자 추가** (예: `==`, `!=`): `TokenType`에 항목 추가 → Lexer에서 스캔 → Parser 해당 규칙 메서드 수정 → Executor `visitBinary`/`visitComparison`에 case 추가
- **새 구문 추가** (예: `while`, 함수 선언): `Stmt`/`Expr`에 sealed 서브클래스 추가 → Visitor 인터페이스에 메서드 추가 → Parser, Checker, Executor 각 `visitXxx` 구현
- **Checker 규칙 추가** (예: 미사용 변수 경고): `Checker.visitVariable`에서 참조 추적 후 `result.addWarning()` 호출
