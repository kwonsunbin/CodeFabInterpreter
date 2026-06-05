@echo off
rem CodeFab 공장 제어 쉘 래퍼 (Windows cmd / PowerShell)
rem
rem 사용법:
rem   factory                    REPL (대화형 프롬프트 모드)
rem   factory run <file.txt>     파일 모드 — 스크립트 실행
rem   factory debug <file.txt>   디버그 모드 — 구문 단위 점검
rem   --verbose                  라이프사이클 로그 출력 (어느 모드든 사용 가능)

setlocal
rem 인터프리터가 UTF-8로 출력하므로 콘솔 코드페이지를 UTF-8로 전환 (한글 깨짐 방지)
chcp 65001 >nul
rem Gradle 클라이언트 런처도 UTF-8로 고정 (CP949 콘솔에서 출력 재인코딩 깨짐 방지)
set "GRADLE_OPTS=-Dfile.encoding=UTF-8 %GRADLE_OPTS%"
set "SCRIPT_DIR=%~dp0"
rem 인자가 없으면(REPL 모드) --args를 넘기지 않는다 — Gradle은 빈 --args=""를 거부한다.
if "%~1"=="" (
    call "%SCRIPT_DIR%gradlew.bat" -q --console=plain run
) else (
    call "%SCRIPT_DIR%gradlew.bat" -q --console=plain run --args="%*"
)
endlocal
