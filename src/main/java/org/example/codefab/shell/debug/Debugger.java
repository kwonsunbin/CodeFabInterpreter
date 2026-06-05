package org.example.codefab.shell.debug;

import org.example.codefab.ast.Stmt;
import org.example.codefab.checker.CheckResult;
import org.example.codefab.checker.Diagnostic;
import org.example.codefab.error.CodeFabError;
import org.example.codefab.shell.Pipeline;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;

/**
 * Debug shell: 소스를 구문(Stmt) 단위로 멈추며 실행 상태를 점검하는 모드.
 *
 * [현재 단계 — 스켈레톤]
 * - 파일 읽기 및 파이프라인 실행은 동작함 (FileRunner와 동일 흐름)
 * - 명령어 파싱 구조(commandLoop)만 뼈대로 존재
 * - step/next/continue/break/watch 등 실제 정지 로직은 미구현
 *   → Executor 훅(ExecutionListener) 연동 후 완성 예정
 *
 * [향후 연동 계획]
 * - Executor.setListener(this) 로 구문 실행 직전 콜백 수신
 * - onStatement(Stmt) 에서 정지 여부 판단 후 commandLoop 진입
 */
public class Debugger {

    // ── 정지 모드 ─────────────────────────────────────────────────────────────

    private enum Mode { STEP, NEXT, CONTINUE }

    private Mode mode = Mode.STEP;

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
        out.println("[debug] ※ 현재 정지 기능 미연동 — 프로그램을 끝까지 실행합니다.");

        // TODO: Executor 훅 연동 후 아래를 listener 기반 실행으로 교체
        try {
            pipeline.executor().run(program);
        } catch (CodeFabError e) {
            out.println(e.userMessage());
        }

        out.println("[debug] 프로그램 종료.");
    }

    // ── 명령어 루프 (스켈레톤) ────────────────────────────────────────────────

    /**
     * 각 구문 실행 직전에 호출될 명령 루프.
     * Executor 훅 연동 전까지는 직접 호출되지 않음.
     */
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
                case "step"     -> { mode = Mode.STEP;     return; }
                case "next"     -> { mode = Mode.NEXT;     return; }
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

                // TODO: Executor 환경 접근 연동 후 구현
                case "inspect" -> out.println("[debug] inspect: Executor 훅 연동 후 구현 예정");

                case "" -> { /* 빈 줄 무시 */ }
                default  -> out.println("알 수 없는 명령어: '" + cmd + "'");
            }
        }
    }

    // ── 출력 헬퍼 (스켈레톤) ─────────────────────────────────────────────────

    private void printWatchValues() {
        if (watches.isEmpty()) return;
        // TODO: Executor 환경 접근 연동 후 실제 값 출력
        watches.forEach(name -> out.println("  watch " + name + " = (연동 전)"));
    }
}
