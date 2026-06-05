package org.example.codefab.shell.debug;

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
        run(source);
    }

    public void run(String source) {
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

        out.println("[debug] 디버그 모드 시작.");
        out.println("[debug] 명령어: step / next / continue / break <줄> / breakpoints / remove <줄>");
        out.println("[debug]         watch <변수> / unwatch <변수> / watches / inspect");

        Executor executor = pipeline.executor();
        executor.setListener(this);
        try {
            executor.run(program);
            out.println("[debug] 프로그램 종료.");
        } catch (CodeFabError e) {
            out.println(e.userMessage());
        } finally {
            executor.setListener(null);
        }
    }

    // ── ExecutionListener (Observer) ──────────────────────────────────────────

    @Override
    public void onStatement(Stmt stmt) {
        int line = lineOf(stmt);

        boolean shouldPause = switch (mode) {
            case STEP     -> true;
            case NEXT     -> scopeDepth <= pauseDepth;
            case CONTINUE -> line >= 0 && breakpoints.contains(line);
        };

        if (shouldPause) {
            pauseDepth = scopeDepth;
            commandLoop(line);
        }
    }

    @Override
    public void onEnterScope() { scopeDepth++; }

    @Override
    public void onExitScope()  { scopeDepth--; }

    // ── 명령어 루프 ───────────────────────────────────────────────────────────

    void commandLoop(int line) {
        out.println("[debug] 정지 — line " + (line < 0 ? "?" : line));
        printWatchValues();

        while (true) {
            out.print("(debug) ");
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
                        breakpoints.add(Integer.parseInt(arg));
                        out.println("breakpoint 설정: line " + arg);
                    } catch (NumberFormatException e) {
                        out.println("줄번호는 정수여야 합니다.");
                    }
                }
                case "breakpoints" -> {
                    if (breakpoints.isEmpty()) out.println("(설정된 breakpoint 없음)");
                    else breakpoints.forEach(bp -> out.println("  break " + bp));
                }
                case "remove" -> {
                    if (arg.isEmpty()) { out.println("사용법: remove <줄번호>"); break; }
                    try {
                        int bp = Integer.parseInt(arg);
                        if (breakpoints.remove(bp)) out.println("breakpoint 제거: line " + bp);
                        else                         out.println("해당 줄에 breakpoint 없음: " + bp);
                    } catch (NumberFormatException e) {
                        out.println("줄번호는 정수여야 합니다.");
                    }
                }

                case "watch" -> {
                    if (arg.isEmpty()) { out.println("사용법: watch <변수명>"); break; }
                    if (!watches.contains(arg)) watches.add(arg);
                    out.println("watch 추가: " + arg);
                }
                case "unwatch" -> {
                    if (watches.remove(arg)) out.println("watch 제거: " + arg);
                    else                      out.println("watch 목록에 없음: " + arg);
                }
                case "watches" -> printWatchValues();

                case "inspect" -> printInspect();

                case "" -> { /* 빈 줄 무시 */ }
                default  -> out.println("알 수 없는 명령어: '" + cmd + "'");
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
            out.println("  watch " + name + " = " + val);
        }
    }

    private void printInspect() {
        Map<String, Object> vars = pipeline.executor().getEnvironment().snapshot();
        if (vars.isEmpty()) {
            out.println("(현재 스코프에 변수 없음)");
        } else {
            vars.forEach((k, v) -> out.println("  " + k + " = " + Executor.stringify(v)));
        }
    }

    // ── 유틸 ──────────────────────────────────────────────────────────────────

    private int lineOf(Stmt stmt) {
        if (stmt instanceof Stmt.Var s) return s.name.line();
        return -1;
    }
}
