# CodeFab 아키텍처 문서

CodeFab 인터프리터의 설계 결정, 디자인 패턴, 전체 실행 흐름을 정리한 문서 모음입니다.

## 문서 목록

| 파일 | 내용 |
|---|---|
| [01-overview.md](01-overview.md) | 전체 아키텍처, 데이터 흐름, 3가지 실행 모드, 컴포넌트 조립(DI) |
| [02-language-spec.md](02-language-spec.md) | CodeFab 언어 명세: 토큰, 문법(BNF), AST 노드 타입, 예제 |
| [03-frontend.md](03-frontend.md) | Lexer, Parser(재귀 하강), Assembler 파사드 |
| [04-ast-visitor.md](04-ast-visitor.md) | **Visitor 패턴** 상세: sealed class, 더블 디스패치, Checker/Executor 비교 |
| [05-checker.md](05-checker.md) | 정적 분석: Scope 2단계 선언, 4가지 시맨틱 규칙, 진단 수집 전략 |
| [06-executor.md](06-executor.md) | Tree-walking 인터프리터: Environment 체이닝, 스코프, 단락 평가, Observer 훅 |
| [07-shell-cli.md](07-shell-cli.md) | Shell, SubmissionBuffer, Pipeline, FileRunner, Debugger, Logger, factory |
| [08-design-patterns.md](08-design-patterns.md) | 패턴 총정리, SOLID 관점, 테스트 전략 |

## 한눈에 보는 컴포넌트 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│                        CLI Entry                            │
│                       Main.java                             │
│  --verbose  │  (no args)  │  run <file>  │  debug <file>   │
└──────┬──────┴──────┬───────┴──────┬───────┴───────┬─────────┘
       │             │              │               │
       │         ┌───▼────┐   ┌────▼──────┐  ┌────▼──────┐
       │         │ Shell  │   │FileRunner │  │ Debugger  │
       │         │ (REPL) │   │(exit code)│  │(Observer) │
       │         └───┬────┘   └────┬──────┘  └────┬──────┘
       │             │              │               │
       └─────────────▼──────────────▼───────────────┘
                         │
                  ┌──────▼──────┐
                  │  Pipeline   │  ← Facade
                  │ (3-stage)   │
                  └──────┬──────┘
           ┌─────────────┼─────────────┐
    ┌──────▼──────┐ ┌────▼─────┐ ┌────▼──────┐
    │  Assembler  │ │ Checker  │ │ Executor  │
    │  (Facade)   │ │(Visitor) │ │(Visitor)  │
    └──────┬──────┘ └──────────┘ └─────┬─────┘
    ┌──────┴──────┐                    │
    │      │      │             ┌──────▼──────┐
  Lexer  Parser  AST           │ Environment │
                               │(parent chain)│
                               └─────────────┘
```

## 핵심 데이터 흐름

```
소스 텍스트
    │
    ▼ Lexer.scanTokens()
List<Token>
    │
    ▼ Parser.parse()
List<Stmt>  (AST)
    │
    ▼ Checker.check()
CheckResult (오류 없으면 통과)
    │
    ▼ Executor.run()
출력 / 사이드 이펙트
```
