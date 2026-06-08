package org.example.codefab.shell.debug;

import org.example.codefab.ast.Expr;
import org.example.codefab.ast.Stmt;
import org.example.codefab.checker.CheckResult;
import org.example.codefab.checker.Diagnostic;
import org.example.codefab.error.CodeFabError;
import org.example.codefab.executor.Environment;
import org.example.codefab.executor.ExecutionListener;
import org.example.codefab.executor.Executor;
import org.example.codefab.shell.Pipeline;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;

/**
 * Debug shell: 소스를 구문(Stmt) 단위로 멈추며 실행 상태를 점검하는 모드.
 *
 * Observer 패턴:
 *   Executor(Observable) → onStatement() → Debugger(Observer) → commandLoop()
 *
 * 정지 모드:
 *   STEP     — 매 구문마다 정지 (블록 내부로 진입)
 *   NEXT     — 현재 스코프 깊이에서만 정지 (블록/함수 내부 미진입)
 *   CONTINUE — 다음 breakpoint 라인까지 연속 실행
 */
public class Debugger implements ExecutionListener {

    // ── 정지 모드 ─────────────────────────────────────────────────────────────

    private enum Mode { STEP, NEXT, CONTINUE }

    private Mode mode       = Mode.STEP;
    private int  scopeDepth = 0;
    private int  pauseDepth = 0;

    // ── 상태 ──────────────────────────────────────────────────────────────────

    private final Set<Integer> breakpoints = new TreeSet<>();
    private final List<String> watches     = new ArrayList<>();

    /** 정지 메시지에 소스 라인 텍스트를 보여주기 위해 보관 */
    private String[] sourceLines;

    // ── 의존성 ────────────────────────────────────────────────────────────────

    private final Pipeline       pipeline;
    private final BufferedReader reader;
    private final PrintStream    out;

    public Debugger(Pipeline pipeline, InputStream in, PrintStream out) {
        this.pipeline = pipeline;
        this.reader   = new BufferedReader(new InputStreamReader(in));
        this.out      = out;
    }

    // ── 진입점 ────────────────────────────────────────────────────────────────

    public void runFile(String filePath) {
        String source;
        try {
            source = Files.readString(Path.of(filePath));
        } catch (NoSuchFileException e) {
            out.println("[error] 파일을 찾을 수 없습니다: " + filePath);
            System.exit(1);
            return;
        } catch (IOException e) {
            out.println("[error] 파일 읽기 오류: " + e.getMessage());
            System.exit(1);
            return;
        }
        out.println("[DEBUG] 소스코드 로딩: " + filePath);
        run(source);
    }

    public void run(String source) {
        this.sourceLines = source.split("\n", -1);

        List<Stmt> program;
        try {
            program = pipeline.assembler().assemble(source);
        } catch (CodeFabError e) {
            out.println(e.userMessage());
            return;
        }

        CheckResult cr = pipeline.checker().check(program);
        for (Diagnostic d : cr.warnings) out.println(d);
        if (!cr.ok()) {
            for (Diagnostic d : cr.errors) out.println(d);
            return;
        }

        Executor executor = pipeline.executor();
        executor.setListener(this);
        try {
            executor.run(program);
            out.println("[DEBUG] 프로그램 종료.");
        } catch (CodeFabError e) {
            out.println(e.userMessage());
        } finally {
            executor.setListener(null);
        }
    }

    // ── ExecutionListener (Observer) ──────────────────────────────────────────

    @Override
    public void onStatement(Stmt stmt) {
        // 블록·for 문은 구조적 컨테이너일 뿐 실행 줄이 아니므로 정지점에서 제외.
        // 실제 정지는 그 안의 실행 구문(초기화식·본문 구문)에서 일어난다.
        // (제외하지 않으면 컨테이너와 첫 구문이 같은 줄에서 이중 정지)
        if (stmt instanceof Stmt.Block || stmt instanceof Stmt.For) return;

        int line = lineOf(stmt);

        boolean shouldPause = switch (mode) {
            case STEP     -> true;
            case NEXT     -> scopeDepth <= pauseDepth;
            case CONTINUE -> line >= 0 && breakpoints.contains(line);
        };

        if (shouldPause) {
            boolean bpHit = mode == Mode.CONTINUE && line >= 0 && breakpoints.contains(line);
            pauseDepth = scopeDepth;
            commandLoop(line, bpHit);
        }
    }

    @Override
    public void onEnterScope() { scopeDepth++; }

    @Override
    public void onExitScope()  { scopeDepth--; }

    // ── 명령어 루프 ───────────────────────────────────────────────────────────

    void commandLoop(int line, boolean bpHit) {
        String where = (line < 0 ? "?" : String.valueOf(line)) + "번째 줄에서 정지";
        String mark  = bpHit ? " (breakpoint)" : "";
        String src   = sourceText(line);
        out.println("[DEBUG] " + where + mark + (src.isEmpty() ? "" : " → " + src));
        printWatchValues();

        while (true) {
            out.print("> ");
            out.flush();

            String input;
            try {
                input = reader.readLine();
            } catch (IOException e) {
                break;
            }
            if (input == null) break;

            String[] parts = input.strip().split("\\s+", 2);
            String   cmd   = parts[0];
            String   arg   = parts.length > 1 ? parts[1].strip() : "";

            switch (cmd) {
                case "step"     -> { mode = Mode.STEP; return; }
                case "next"     -> { mode = Mode.NEXT; pauseDepth = scopeDepth; return; }
                case "continue" -> { mode = Mode.CONTINUE; return; }

                case "break" -> {
                    if (arg.isEmpty()) { out.println("사용법: break <줄번호>"); break; }
                    try {
                        int bp = Integer.parseInt(arg);
                        breakpoints.add(bp);
                        out.println("[DEBUG] " + bp + "번째 줄에 breakpoint 설정");
                    } catch (NumberFormatException e) {
                        out.println("줄번호는 정수여야 합니다.");
                    }
                }
                case "breakpoints" -> {
                    if (breakpoints.isEmpty()) out.println("[DEBUG] 설정된 breakpoint 없음");
                    else breakpoints.forEach(bp -> out.println("[DEBUG] breakpoint: " + bp + "번째 줄"));
                }
                case "remove" -> {
                    if (arg.isEmpty()) { out.println("사용법: remove <줄번호>"); break; }
                    try {
                        int bp = Integer.parseInt(arg);
                        if (breakpoints.remove(bp)) out.println("[DEBUG] " + bp + "번째 줄 breakpoint 제거");
                        else                         out.println("[DEBUG] " + bp + "번째 줄에 breakpoint 없음");
                    } catch (NumberFormatException e) {
                        out.println("줄번호는 정수여야 합니다.");
                    }
                }

                case "watch" -> {
                    if (arg.isEmpty()) { out.println("사용법: watch <변수명>"); break; }
                    if (!watches.contains(arg)) watches.add(arg);
                    out.println("[WATCH] '" + arg + "' 감시 등록");
                }
                case "unwatch" -> {
                    if (watches.remove(arg)) out.println("[WATCH] '" + arg + "' 감시 해제");
                    else                      out.println("[WATCH] 감시 목록에 없음: '" + arg + "'");
                }
                case "watches" -> printWatchValues();

                case "inspect" -> printInspect();

                case "help" -> printHelp();

                case "" -> { /* 빈 줄 무시 */ }
                default  -> out.println("알 수 없는 명령어: '" + cmd + "' (help 입력 시 명령어 목록)");
            }
        }
    }

    // ── 출력 헬퍼 ─────────────────────────────────────────────────────────────

    private void printWatchValues() {
        if (watches.isEmpty()) return;
        Environment env = pipeline.executor().getEnvironment();
        for (String name : watches) {
            String val = env.has(name)
                    ? Executor.stringify(env.getByName(name))
                    : "(정의되지 않음)";
            out.println("[WATCH] " + name + " = " + val);
        }
    }

    private void printInspect() {
        out.println("[INSPECT] 현재 스코프 변수");

        // allScopes(): 인덱스 0 = 전역(outermost), 마지막 = 현재(innermost)
        List<Map<String, Object>> scopes = pipeline.executor().getEnvironment().allScopes();

        boolean printedLocal = false;
        // 전역(index 0)을 제외한 안쪽 스코프 = 로컬 (안쪽부터 출력)
        for (int i = scopes.size() - 1; i > 0; i--) {
            for (Map.Entry<String, Object> v : scopes.get(i).entrySet()) {
                out.println("[로컬] " + v.getKey() + " = "
                        + Executor.stringify(v.getValue()) + " (" + displayType(v.getValue()) + ")");
                printedLocal = true;
            }
        }

        // 전역 스코프 (index 0)
        Map<String, Object> globals = scopes.get(0);
        if (printedLocal && !globals.isEmpty()) out.println();
        globals.forEach((k, v) -> out.println("[전역] " + k + " = "
                + Executor.stringify(v) + " (" + displayType(v) + ")"));
    }

    private void printHelp() {
        out.println("[DEBUG] 명령어: step / next / continue / break <줄> / breakpoints / remove <줄>");
        out.println("[DEBUG]         watch <변수> / unwatch <변수> / watches / inspect / help");
    }

    // ── 유틸 ──────────────────────────────────────────────────────────────────

    /** 1-기반 줄번호의 소스 텍스트(앞뒤 공백 제거)를 반환. 범위를 벗어나면 빈 문자열 */
    private String sourceText(int line) {
        if (sourceLines == null || line < 1 || line > sourceLines.length) return "";
        return sourceLines[line - 1].strip();
    }

    /** 디버그 표시용 타입명 (대문자 시작) */
    private static String displayType(Object value) {
        if (value == null)             return "Null";
        if (value instanceof Double)   return "Number";
        if (value instanceof Boolean)  return "Boolean";
        if (value instanceof String)   return "String";
        return value.getClass().getSimpleName();
    }

    /** 구문에서 대표 줄번호를 추출. 토큰이 없는 경우 -1 */
    private int lineOf(Stmt stmt) {
        if (stmt instanceof Stmt.Var s)        return s.name.line();
        if (stmt instanceof Stmt.Print s)      return exprLine(s.expression);
        if (stmt instanceof Stmt.Expression s) return exprLine(s.expression);
        if (stmt instanceof Stmt.If s)         return exprLine(s.condition);
        if (stmt instanceof Stmt.For s)
            return s.initializer != null ? lineOf(s.initializer) : exprLine(s.condition);
        if (stmt instanceof Stmt.Block s)
            return s.statements.isEmpty() ? -1 : lineOf(s.statements.get(0));
        return -1;
    }

    /** 표현식에서 대표 줄번호를 추출. 토큰이 없는 리터럴은 -1 */
    private int exprLine(Expr expr) {
        if (expr == null)                       return -1;
        if (expr instanceof Expr.Assign e)      return e.name.line();
        if (expr instanceof Expr.Variable e)    return e.name.line();
        if (expr instanceof Expr.Binary e)      return e.op.line();
        if (expr instanceof Expr.Logical e)     return e.op.line();
        if (expr instanceof Expr.Comparison e)  return e.op.line();
        if (expr instanceof Expr.Unary e)       return e.op.line();
        if (expr instanceof Expr.Grouping e)    return exprLine(e.expression);
        if (expr instanceof Expr.Literal e)     return e.line;
        return -1;
    }
}
