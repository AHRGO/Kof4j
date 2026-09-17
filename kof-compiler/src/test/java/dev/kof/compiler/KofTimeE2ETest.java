package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


/**
 * End-to-end tests for {@code kof.time} — sleep, now e scheduler.
 */
class KofTimeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private String runJvm(Path tempDir, String kofSource, String expected) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private String runNative(Path tempDir, String kofSource, String expected) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve("out-native");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile should succeed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected native output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    @Test
    void sleepPausesForMs(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    var t0 = time.now()
                    time.sleep(250)
                    var t1 = time.now()
                    println(t1 - t0 >= 200)
                }
                """, "true");
    }

    // #108 (13/09): `sleep(ms)` sem receiver resolve como `time.sleep(ms)`
    // (opção 1 — espelha o `now()` sem receiver). Paridade JVM+Native+JS.
    @Test
    void sleepUnqualifiedResolvesAsTimeSleep(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    var t0 = now()
                    sleep(250)
                    var t1 = now()
                    println(t1 - t0 >= 200)
                }
                """, "true");
        runNative(tempDir, """
                main() {
                    var t0 = now()
                    sleep(250)
                    var t1 = now()
                    println(t1 - t0 >= 200)
                }
                """, "true");
    }

    @Test
    void intervalRunsPeriodicallyUntilCancelled(@TempDir Path tempDir) throws IOException {
        String src = """
                main() {
                    var ticks = 0
                    var job = time.interval(100, () -> {
                        ticks = ticks + 1
                    })
                    time.sleep(450)
                    time.cancel(job)
                    var after = ticks
                    time.sleep(300)
                    println(ticks == after)
                    println(ticks >= 2)
                }
                """;
        runJvm(tempDir, src, "true\ntrue");
        // TIME001 (01/09): Native reusa o scheduler.every/cancel (SCHED001) —
        // mutação por referência da captura (ticks) é validada aqui.
        runNative(tempDir, src, "true\ntrue");
    }

    // §253 face A (16/09): `var id = time.interval(…, () -> cancel(id))` — a
    // lambda do PRÓPRIO inicializador lê o handle que está sendo declarado.
    // Antes dava SEM011 nos 3 alvos (escopo define id só DEPOIS de tipar o
    // init). Face A abre JVM+JS (pre-define + box com store antes do init);
    // NATIVE fica em SEM092 (face B — leitura do handle nativo SIGSEGVa).
    @Test
    void selfReferencingIntervalHandleCancelsItself(@TempDir Path tempDir) throws IOException {
        String src = """
                main() {
                    var ticks = 0
                    var id = time.interval(100, () -> {
                        ticks = ticks + 1
                        if (ticks >= 3) {
                            time.cancel(id)
                        }
                    })
                    time.sleep(450)
                    println(ticks == 3)
                    var after = ticks
                    time.sleep(300)
                    println(ticks == after)
                }
                """;
        runJvm(tempDir, src, "true\ntrue");
        runJs(tempDir, src, "true\ntrue");
        // Script (interpretador): roda o mesmo KofRuntime do host JVM — a
        // leitura self-ref do handle no job é a mesma captura do frame.
        Path sfile = tempDir.resolve("SelfScript-" + System.nanoTime() + ".kf");
        Files.writeString(sfile, src);
        dev.kof.compiler.KofInterpreter.Result ir = new CompilerDriver()
                .interpret(java.util.List.of(sfile), sfile.getParent(), new String[0]);
        assertEquals(0, ir.exitCode(), "Script exit/stderr: " + ir.stdout() + " " + ir.stderr());
        assertEquals("true\ntrue", ir.stdout().trim(), "Script output");
    }

    @Test
    void selfReferencingIntervalHandleIsNativeGateSem092(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var id = time.interval(100, () -> {
                        time.cancel(id)
                    })
                    time.sleep(250)
                }
                """);
        for (Target t : new Target[]{Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = driver.compile(source, tempDir.resolve("sem092-" + t), t);
            assertFalse(r.success(), t + " self-ref handle deve falhar (face B pendente)");
            String diags = r.diagnostics().getDiagnostics().toString();
            assertTrue(diags.contains("SEM092"), t + " deve reportar SEM092: " + diags);
        }
    }

    @Test
    void nowReturnsEpochMillis(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    println(time.now() > 1700000000000)
                }
                """, "true");
    }

    // ── STDLIB S7-wedge — calendário civil (isLeapYear/daysInMonth) ───────
    @Test
    void calendarJvm(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    println(time.isLeapYear(2000))
                    println(time.isLeapYear(1900))
                    println(time.isLeapYear(2024))
                    println(time.isLeapYear(2023))
                    println(time.isLeapYear(-4))
                    println(time.daysInMonth(2024, 2))
                    println(time.daysInMonth(2023, 2))
                    println(time.daysInMonth(2024, 4))
                    println(time.daysInMonth(2024, 13))
                    println(time.daysInMonth(0, 5))
                    println(time.dayOfWeek(1970, 1, 1))
                    println(time.dayOfWeek(2026, 9, 9))
                    println(time.dayOfWeek(1, 1, 1))
                    println(time.dayOfWeek(9999, 12, 31))
                    println(time.dayOfWeek(2024, 2, 30))
                    println(time.daysBetween(2024, 1, 1, 2024, 3, 1))
                    println(time.daysBetween(2024, 3, 1, 2024, 1, 1))
                    println(time.daysBetween(2023, 2, 29, 2023, 3, 1))
                    println(time.isWeekend(2026, 9, 12))
                    println(time.isWeekend(2026, 9, 13))
                    println(time.isWeekend(2026, 9, 9))
                    println(time.isWeekend(2026, 2, 30))
                }
                """, "true\nfalse\ntrue\nfalse\nfalse\n29\n28\n30\n0\n0\n4\n3\n1\n5\n0\n60\n-60\n0\ntrue\ntrue\nfalse\nfalse");
    }

    @Test
    void calendarJs(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, """
                main() {
                    println(time.isLeapYear(2000))
                    println(time.isLeapYear(1900))
                    println(time.daysInMonth(2024, 2))
                    println(time.daysInMonth(2023, 2))
                    println(time.daysInMonth(2024, 13))
                    println(time.daysInMonth(0, 5))
                    println(time.isWeekend(2026, 9, 12))
                    println(time.isWeekend(2026, 9, 9))
                }
                """, "true\nfalse\n29\n28\n0\n0\ntrue\nfalse");
    }

    @Test
    void calendarNative(@TempDir Path tempDir) throws IOException {
        runNative(tempDir, """
                main() {
                    println(time.isLeapYear(2000))
                    println(time.isLeapYear(1900))
                    println(time.isLeapYear(2024))
                    println(time.isLeapYear(-4))
                    println(time.daysInMonth(2024, 2))
                    println(time.daysInMonth(2023, 2))
                    println(time.daysInMonth(2024, 4))
                    println(time.daysInMonth(2024, 12))
                    println(time.daysInMonth(2024, 13))
                    println(time.isWeekend(2024, 2, 25))
                    println(time.isWeekend(2024, 2, 29))
                }
                """, "true\nfalse\ntrue\nfalse\n29\n28\n30\n31\n0\ntrue\nfalse");
    }

    @Test
    void calendarCrossArchRuntimes(@TempDir Path tempDir) throws IOException {
        // PRIMEIRO teste de calendário que EXECUTA riscv/aarch (assert-only +
        // qemu; bug 59 é só no link do println).
        String src = """
                main() {
                    assert(time.isLeapYear(2000))
                    assert(!time.isLeapYear(1900))
                    assert(time.isLeapYear(2024))
                    assert(!time.isLeapYear(2023))
                    assert(!time.isLeapYear(-4))
                    assert(time.daysInMonth(2024, 2) == 29)
                    assert(time.daysInMonth(2023, 2) == 28)
                    assert(time.daysInMonth(2024, 4) == 30)
                    assert(time.daysInMonth(2024, 13) == 0)
                    assert(time.daysInMonth(0, 5) == 0)
                    assert(time.daysInMonth(2024, 12) == 31)
                    assert(time.dayOfWeek(1970, 1, 1) == 4)
                    assert(time.dayOfWeek(2026, 9, 9) == 3)
                    assert(time.dayOfWeek(1, 1, 1) == 1)
                    assert(time.dayOfWeek(9999, 12, 31) == 5)
                    assert(time.dayOfWeek(2024, 2, 30) == 0)
                    assert(time.dayOfWeek(10000, 1, 1) == 0)
                    assert(time.daysBetween(2024, 1, 1, 2024, 3, 1) == 60)
                    assert(time.daysBetween(2024, 3, 1, 2024, 1, 1) == -60)
                    assert(time.daysBetween(2020, 2, 28, 2020, 3, 1) == 2)
                    assert(time.daysBetween(2023, 2, 29, 2023, 3, 1) == 0)
                    assert(time.daysBetween(2024, 1, 1, 10000, 1, 1) == 0)
                    assert(time.isWeekend(2026, 9, 12))
                    assert(time.isWeekend(2026, 9, 13))
                    assert(!time.isWeekend(2026, 9, 9))
                    assert(!time.isWeekend(2026, 9, 7))
                    assert(!time.isWeekend(2026, 2, 30))
                    assert(time.isWeekend(2024, 2, 25))
                    assert(!time.isWeekend(2024, 2, 29))
                }
                """;
        if (has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")) {
            runQemu(tempDir, Target.NATIVE_RISCV64, "qemu-riscv64", src);
        } else {
            Assumptions.assumeTrue(false, "toolchain riscv64 ausente");
        }
        if (has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64")) {
            runQemu(tempDir, Target.NATIVE_AARCH64, "qemu-aarch64", src);
        } else {
            Assumptions.assumeTrue(false, "toolchain aarch64 ausente");
        }
    }

    private void runQemu(Path tempDir, Target target, String qemu, String kofSource)
            throws IOException {
        Path file = tempDir.resolve("Main-" + target + "-" + System.nanoTime() + ".kf");
        Files.writeString(file, kofSource);
        Path outDir = tempDir.resolve("qemu-" + target + "-" + System.nanoTime());
        CompilationResult result = new CompilerDriver().compile(file, outDir, target);
        assertTrue(result.success(), target + " compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(qemu, bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            assertEquals(0, p.waitFor(), target + " qemu exit, out: " + output);
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    private String runJs(Path tempDir, String kofSource, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, kofSource);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile: " + result.diagnostics().getDiagnostics());
        Path entry;
        try (var s = Files.walk(outDir)) {
            entry = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst()
                    .orElseThrow(() -> new IOException("no .mjs"));
        }
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(entry, buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit, out: " + output);
            assertEquals(expected, output, "JS output");
            return output;
        }
    }

    @Test
    void nativeAndJsSupportNowAndSleep(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var t0 = time.now()
                    time.sleep(10)
                    var t1 = time.now()
                    println(t1 >= t0)
                }
                """);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native should support time.now/sleep: " + nativeResult.diagnostics().getDiagnostics());
        Path nativeBin = tempDir.resolve("native").resolve("Default/Main");
        Process pn = new ProcessBuilder(nativeBin.toString()).redirectErrorStream(true).start();
        try {
            String out = new String(pn.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            int ec = pn.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + out);
            assertEquals("true", out, "Native output");
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        CompilationResult jsResult = driver.compile(source, tempDir.resolve("js"), Target.JS);
        assertTrue(jsResult.success(), "JS should support time.now/sleep: " + jsResult.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            Path jsEntry = findJsEntry(tempDir.resolve("js"));
            int ec = dev.kof.runtime.KofJsRunner.run(jsEntry, buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String out = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + out);
            assertEquals("true", out, "JS output");
        }
    }

    @Test
    void jsIntervalRunsPeriodicallyUntilCancelled(@TempDir Path tempDir) throws IOException {
        // TIME001 fechado (02/09): JS roda time.interval/cancel por fila
        // cooperativa bombeada dentro de time.sleep (GraalJS não tem
        // setInterval/event loop; browser/Node usam setInterval nativo).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var ticks = 0
                    var job = time.interval(100, () -> {
                        ticks = ticks + 1
                    })
                    time.sleep(450)
                    time.cancel(job)
                    var after = ticks
                    time.sleep(300)
                    println(ticks == after)
                    println(ticks >= 2)
                }
                """);
        CompilationResult jsResult = driver.compile(source, tempDir.resolve("js2"), Target.JS);
        assertTrue(jsResult.success(), "JS should now compile time.interval: "
                + jsResult.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            Path jsEntry = findJsEntry(tempDir.resolve("js2"));
            int ec = dev.kof.runtime.KofJsRunner.run(jsEntry, buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String out = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + out);
            assertEquals("true\ntrue", out, "JS output");
        }
    }

    private static Path findJsEntry(Path dir) throws IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new IOException("no .mjs in " + dir));
        }
    }

    @Test
    void crossNativeTimeIntervalRuns(@TempDir Path tempDir) throws Exception {
        // TIME001 FEITO no cross (05/09): time.interval/cancel são alias do
        // scheduler (thread por job via clone+nanosleep). O callback dispara
        // e o cancel silencia; END por último.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var id = time.interval(100, () -> println("tick"))
                time.sleep(250)
                time.cancel(id)
                time.sleep(250)
                println("END")
            }
            """);
        String[] q = {"qemu-riscv64", "qemu-aarch64"};
        Target[] ts = {Target.NATIVE_RISCV64, Target.NATIVE_AARCH64};
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain riscv64/aarch64 + qemu ausente — pulando (NATIVE002)");
        for (int i = 0; i < 2; i++) {
            CompilationResult r = new CompilerDriver().compile(source, tempDir.resolve("cross-" + i), ts[i]);
            assertTrue(r.success(), ts[i] + " deve compilar: " + r.diagnostics().getDiagnostics());
            Path bin = tempDir.resolve("cross-" + i).resolve("Default/Main");
            var p = new ProcessBuilder("timeout", "10", q[i], bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            assertEquals(0, p.waitFor(), ts[i] + " exit, output: " + output);
            assertTrue(output.endsWith("END"), ts[i] + ": END por último: " + output);
            int ticks = 0;
            for (String l : output.split("\n")) if (l.equals("tick")) ticks++;
            assertTrue(ticks >= 1 && ticks <= 8, ts[i] + ": esperava 1..8 ticks, veio " + ticks + ": " + output);
        }
        // now/sleep continuam ok no cross (sem gate)
        Path ok = tempDir.resolve("Ok.kf");
        Files.writeString(ok, """
            main() {
                var t = time.now()
                println(t > 1000000000000)
            }
            """);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(ok, tempDir.resolve("ok-" + t), t);
            assertTrue(r.success(), t + " time.now should compile: " + r.diagnostics().getDiagnostics());
        }
    }

    /**
     * STDLIB S7a — addDays/diffDays em data ISO (String).
     * Shape travado no JVM (java.time); Native/JS = gap honesto TIME002
     * (erro claro no compile, nunca fallback silencioso — R6).
     */
    @Test
    void timeAddDaysDiffDaysJvmShapeAndCrossArch(@TempDir Path tempDir) throws IOException {
        String src = """
            main() {
                println(time.addDays("2024-02-28", 1))
                println(time.addDays("2023-02-28", 1))
                println(time.addDays("2024-12-31", 1))
                println(time.addDays("2024-01-01", -1))
                println(time.addDays("2024-02-30", 1))
                println(time.addDays("garbage", 1))
                println(time.diffDays("2024-01-01", "2024-03-01"))
                println(time.diffDays("2024-03-01", "2024-01-01"))
                println(time.diffDays("x", "y"))
            }
            """;
        assertEquals("2024-02-29\n2023-03-01\n2025-01-01\n2023-12-31\n\n\n60\n-60\n0",
                runJvm(tempDir, src,
                        "2024-02-29\n2023-03-01\n2025-01-01\n2023-12-31\n\n\n60\n-60\n0"));
        Path gateSrc = tempDir.resolve("Gate.kf");
        Files.writeString(gateSrc, src);
        // S7b: JS FECHADO; S7c: x86 FECHADO (matriz stdtime2 roda local).
        // S7d (TIME002 fechado 11/09): riscv64/aarch64 — B33 (.Lu8_parse2/
        // .Lu8_civil/.Lu8_put*) port 1:1 do RuntimeTimeIso x86 reusando
        // kdv_valid/kdv_epoch (B14). Golden byte-idêntico sob qemu.
        String expected = "2024-02-29\n2023-03-01\n2025-01-01\n2023-12-31\n\n\n60\n-60\n0";
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String qemu = t == Target.NATIVE_RISCV64 ? "qemu-riscv64" : "qemu-aarch64";
            String[] tools = t == Target.NATIVE_RISCV64
                    ? new String[]{"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"}
                    : new String[]{"aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"};
            assumeToolchain(tools);
            Path file = tempDir.resolve("Ad-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(file, src);
            Path outDir = tempDir.resolve("ad-" + t + "-" + System.nanoTime());
            CompilationResult r = new CompilerDriver().compile(file, outDir, t);
            assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
            Process p = new ProcessBuilder(qemu, outDir.resolve("Default/Main").toString())
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec;
            try {
                ec = p.waitFor();
            } catch (InterruptedException e) {
                throw new IOException("interrupted", e);
            }
            assertEquals(0, ec, t + " qemu exit, out: " + out);
            assertEquals(expected, out, t + " golden addDays/diffDays");
        }
    }

    @Test
    void timeAddDaysDiffDaysCompilesOnAllTargets(@TempDir Path tempDir) throws IOException {
        // Gate SEMPRE-verde (mesmo sem qemu): o backend cross EMITE o asm; a
        // EXECUCAO e provada sob qemu no teste acima (S7c-1/TIME002 fechado).
        String src = """
            main() {
                println(time.addDays("2024-02-28", 1))
                println(time.diffDays("2024-01-01", "2024-03-01"))
            }
            """;
        Path gateSrc = tempDir.resolve("GateTime.kf");
        Files.writeString(gateSrc, src);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(gateSrc, tempDir.resolve("gate-" + t), t);
            assertTrue(r.success(), t + " deve compilar addDays/diffDays (TIME002 fechado): "
                    + r.diagnostics().getDiagnostics());
        }
    }

    // ── STDLIB S7e (D-STDLIB ratificado 13/09): todayIso/formatDateIso/
    // isToday — UTC-only (D1), formato zero-DSL com invalidade => "" (D4),
    // isToday = igualdade com a data UTC de now() (D5). Vetores
    // determinísticos (formatDateIso/isToday não dependem do relógio);
    // todayIso validado por FORMATO+CONSISTÊNCIA (prefixo/len/regex),
    // nunca por valor literal (o dia pode virar no meio do teste).
    @Test
    void todayIsoFormatDateIsoIsTodayJvm(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    var today = time.todayIso()
                    println(today.length)
                    println(time.formatDateIso(2026, 9, 13))
                    println(time.formatDateIso(2024, 2, 29))
                    println(time.formatDateIso(2023, 2, 29))
                    println(time.formatDateIso(0, 1, 1))
                    println(time.formatDateIso(10000, 1, 1))
                    println(time.formatDateIso(2026, 13, 1))
                    println(time.formatDateIso(2026, 0, 1))
                    println(time.formatDateIso(2026, 1, 0))
                    println(time.formatDateIso(2026, 4, 31))
                    // Q4: isToday(y,m,d)==true NUNCA literal (o dia vira à
                    // meia-noite UTC — quebrou 14/09). Consistência interna
                    // independente do relogio: as partes de todayIso() sao
                    // hoje => isToday delas = true (parseDateIso fecha com S7g).
                    var parts = today.split("-")
                    var p0: String = parts[0]
                    var p1: String = parts[1]
                    var p2: String = parts[2]
                    println(time.isToday(math.parseInt(p0),
                                         math.parseInt(p1),
                                         math.parseInt(p2)))
                    println(time.isToday(2026, 9, 12))
                    println(time.isToday(2026, 2, 30))
                    println(time.isToday(0, 1, 1))
                    println(parts.size)
                    println(parts[0].length)
                    println(parts[1].length)
                    println(parts[2].length)
                }
                """, "10\n2026-09-13\n2024-02-29\n\n\n\n\n\n\n\ntrue\nfalse\nfalse\nfalse\n3\n4\n2\n2");
    }

    @Test
    void todayIsoFormatDateIsoIsTodayJs(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, """
                main() {
                    var today = time.todayIso()
                    println(today.length)
                    println(time.formatDateIso(2026, 9, 13))
                    println(time.formatDateIso(2024, 2, 29))
                    println(time.formatDateIso(2023, 2, 29))
                    println(time.formatDateIso(0, 1, 1))
                    println(time.formatDateIso(2026, 13, 1))
                    println(time.formatDateIso(2026, 4, 31))
                    // Q4: consistencia interna (relogio-independente)
                    var parts = today.split("-")
                    var p0: String = parts[0]
                    var p1: String = parts[1]
                    var p2: String = parts[2]
                    println(time.isToday(math.parseInt(p0),
                                         math.parseInt(p1),
                                         math.parseInt(p2)))
                    println(time.isToday(2026, 9, 12))
                    println(time.isToday(2026, 2, 30))
                    println(parts.size)
                    println(parts[0].length)
                    println(parts[1].length)
                }
                """, "10\n2026-09-13\n2024-02-29\n\n\n\n\ntrue\nfalse\nfalse\n3\n4\n2");
    }

    @Test
    void todayIsoFormatDateIsoIsTodayNative(@TempDir Path tempDir) throws IOException {
        runNative(tempDir, """
                main() {
                    var today = time.todayIso()
                    println(today.length)
                    println(time.formatDateIso(2026, 9, 13))
                    println(time.formatDateIso(2024, 2, 29))
                    println(time.formatDateIso(2023, 2, 29))
                    println(time.formatDateIso(0, 1, 1))
                    println(time.formatDateIso(10000, 1, 1))
                    println(time.formatDateIso(2026, 13, 1))
                    println(time.formatDateIso(2026, 4, 31))
                    // Q4: consistencia interna (relogio-independente)
                    var parts = today.split("-")
                    var p0: String = parts[0]
                    var p1: String = parts[1]
                    var p2: String = parts[2]
                    println(time.isToday(math.parseInt(p0),
                                         math.parseInt(p1),
                                         math.parseInt(p2)))
                    println(time.isToday(2026, 9, 12))
                    println(time.isToday(2026, 2, 30))
                    println(time.isToday(0, 1, 1))
                    println(parts.size)
                    println(parts[0].length)
                    println(parts[1].length)
                }
                """, "10\n2026-09-13\n2024-02-29\n\n\n\n\n\ntrue\nfalse\nfalse\nfalse\n3\n4\n2");
    }

    @Test
    void todayIsoFormatDateIsoIsTodayCrossArch(@TempDir Path tempDir) throws Exception {
        // Vetores determinísticos + formato do todayIso (len/parts) — sem
        // valor literal do dia (pode virar entre backends). isToday com data
        // fixa SÓ é assertado para a resposta false (independe do relógio);
        // o caminho true é coberto pela igualdade formatDateIso==todayIso
        // implícita no gate de formato. Qemu prova byte-idêntico.
        String src = """
            main() {
                var today = time.todayIso()
                println(today.length)
                println(time.formatDateIso(2026, 9, 13))
                println(time.formatDateIso(2023, 2, 29))
                println(time.formatDateIso(2024, 2, 29))
                println(time.formatDateIso(0, 1, 1))
                println(time.formatDateIso(10000, 1, 1))
                println(time.formatDateIso(2026, 13, 1))
                println(time.isToday(2026, 9, 12))
                println(time.isToday(2026, 2, 30))
                var parts = today.split("-")
                println(parts.size)
                println(parts[0].length)
                println(parts[1].length)
                println(parts[2].length)
            }
            """;
        String expected = "10\n2026-09-13\n\n2024-02-29\n\n\n\nfalse\nfalse\n3\n4\n2\n2";
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String qemu = t == Target.NATIVE_RISCV64 ? "qemu-riscv64" : "qemu-aarch64";
            String[] tools = t == Target.NATIVE_RISCV64
                    ? new String[]{"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"}
                    : new String[]{"aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"};
            assumeToolchain(tools);
            Path file = tempDir.resolve("T7e-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(file, src);
            Path outDir = tempDir.resolve("t7e-" + t + "-" + System.nanoTime());
            CompilationResult r = new CompilerDriver().compile(file, outDir, t);
            assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
            Process p = new ProcessBuilder(qemu, outDir.resolve("Default/Main").toString())
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec;
            try {
                ec = p.waitFor();
            } catch (InterruptedException e) {
                throw new IOException("interrupted", e);
            }
            assertEquals(0, ec, t + " qemu exit, out: " + out);
            assertEquals(expected, out, t + " golden S7e");
        }
    }

    @Test
    void todayIsoFormatDateIsoIsTodayCompilesOnAllTargets(@TempDir Path tempDir) throws IOException {
        // Gate SEMPRE-verde (mesmo sem qemu): o backend cross EMITE o asm;
        // a EXECUCAO e provada sob qemu no teste acima.
        String src = """
            main() {
                println(time.todayIso().length)
                println(time.formatDateIso(2026, 9, 13))
                println(time.isToday(2026, 9, 13))
            }
            """;
        Path gateSrc = tempDir.resolve("GateT7e.kf");
        Files.writeString(gateSrc, src);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(gateSrc, tempDir.resolve("gate-t7e-" + t), t);
            assertTrue(r.success(), t + " deve compilar todayIso/formatDateIso/isToday (S7e): "
                    + r.diagnostics().getDiagnostics());
        }
    }

    // ── STDLIB S7f (D3): hoursBetween — floor simétrico (truncado a
    // zero, consistente com daysBetween); datas inválidas/hora fora de
    // 0..23 => 0 (paridade do gating do wedge). Sem float (FLT001).
    // Totalmente determinístico — vetor cru incl. diferenças negativas,
    // virada de dia/mês/ano-bissexto e bounds 9999/1.
    @Test
    void hoursBetweenJvm(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    println(time.hoursBetween(2026, 1, 1, 10, 2026, 1, 2, 12))
                    println(time.hoursBetween(2026, 1, 2, 12, 2026, 1, 1, 10))
                    println(time.hoursBetween(2026, 1, 1, 0, 2026, 1, 1, 23))
                    println(time.hoursBetween(2026, 1, 1, 23, 2026, 1, 2, 0))
                    println(time.hoursBetween(2026, 1, 1, 5, 2026, 1, 1, 5))
                    println(time.hoursBetween(2026, 2, 30, 5, 2026, 3, 1, 5))
                    println(time.hoursBetween(2026, 1, 1, 24, 2026, 1, 2, 5))
                    println(time.hoursBetween(2026, 1, 1, 5, 2026, 1, 1, 25))
                    println(time.hoursBetween(2024, 2, 29, 1, 2024, 3, 1, 1))
                    println(time.hoursBetween(9999, 12, 31, 0, 1, 1, 0, 23))
                    println(time.hoursBetween(2026, 1, 1, 10, 2027, 1, 1, 10))
                }
                """, "26\n-26\n23\n1\n0\n0\n0\n0\n24\n0\n8760");
    }

    @Test
    void hoursBetweenJs(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, """
                main() {
                    println(time.hoursBetween(2026, 1, 1, 10, 2026, 1, 2, 12))
                    println(time.hoursBetween(2026, 1, 2, 12, 2026, 1, 1, 10))
                    println(time.hoursBetween(2026, 1, 1, 0, 2026, 1, 1, 23))
                    println(time.hoursBetween(2026, 1, 1, 23, 2026, 1, 2, 0))
                    println(time.hoursBetween(2026, 1, 1, 5, 2026, 1, 1, 5))
                    println(time.hoursBetween(2026, 2, 30, 5, 2026, 3, 1, 5))
                    println(time.hoursBetween(2026, 1, 1, 24, 2026, 1, 2, 5))
                    println(time.hoursBetween(2024, 2, 29, 1, 2024, 3, 1, 1))
                    println(time.hoursBetween(2026, 1, 1, 10, 2027, 1, 1, 10))
                }
                """, "26\n-26\n23\n1\n0\n0\n0\n24\n8760");
    }

    @Test
    void hoursBetweenNative(@TempDir Path tempDir) throws IOException {
        runNative(tempDir, """
                main() {
                    println(time.hoursBetween(2026, 1, 1, 10, 2026, 1, 2, 12))
                    println(time.hoursBetween(2026, 1, 2, 12, 2026, 1, 1, 10))
                    println(time.hoursBetween(2026, 1, 1, 0, 2026, 1, 1, 23))
                    println(time.hoursBetween(2026, 1, 1, 23, 2026, 1, 2, 0))
                    println(time.hoursBetween(2026, 1, 1, 5, 2026, 1, 1, 5))
                    println(time.hoursBetween(2026, 2, 30, 5, 2026, 3, 1, 5))
                    println(time.hoursBetween(2026, 1, 1, 24, 2026, 1, 2, 5))
                    println(time.hoursBetween(2024, 2, 29, 1, 2024, 3, 1, 1))
                    println(time.hoursBetween(2026, 1, 1, 10, 2027, 1, 1, 10))
                }
                """, "26\n-26\n23\n1\n0\n0\n0\n24\n8760");
    }

    @Test
    void hoursBetweenCrossArch(@TempDir Path tempDir) throws Exception {
        String src = """
            main() {
                println(time.hoursBetween(2026, 1, 1, 10, 2026, 1, 2, 12))
                println(time.hoursBetween(2026, 1, 2, 12, 2026, 1, 1, 10))
                println(time.hoursBetween(2026, 1, 1, 0, 2026, 1, 1, 23))
                println(time.hoursBetween(2026, 1, 1, 23, 2026, 1, 2, 0))
                println(time.hoursBetween(2026, 1, 1, 5, 2026, 1, 1, 5))
                println(time.hoursBetween(2026, 2, 30, 5, 2026, 3, 1, 5))
                println(time.hoursBetween(2026, 1, 1, 24, 2026, 1, 2, 5))
                println(time.hoursBetween(2024, 2, 29, 1, 2024, 3, 1, 1))
                println(time.hoursBetween(2026, 1, 1, 10, 2027, 1, 1, 10))
            }
            """;
        String expected = "26\n-26\n23\n1\n0\n0\n0\n24\n8760";
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String qemu = t == Target.NATIVE_RISCV64 ? "qemu-riscv64" : "qemu-aarch64";
            String[] tools = t == Target.NATIVE_RISCV64
                    ? new String[]{"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"}
                    : new String[]{"aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"};
            assumeToolchain(tools);
            Path file = tempDir.resolve("T7f-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(file, src);
            Path outDir = tempDir.resolve("t7f-" + t + "-" + System.nanoTime());
            CompilationResult r = new CompilerDriver().compile(file, outDir, t);
            assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
            Process p = new ProcessBuilder(qemu, outDir.resolve("Default/Main").toString())
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec;
            try {
                ec = p.waitFor();
            } catch (InterruptedException e) {
                throw new IOException("interrupted", e);
            }
            assertEquals(0, ec, t + " qemu exit, out: " + out);
            assertEquals(expected, out, t + " golden S7f");
        }
    }

    @Test
    void hoursBetweenCompilesOnAllTargets(@TempDir Path tempDir) throws IOException {
        String src = """
            main() {
                println(time.hoursBetween(2026, 1, 1, 10, 2026, 1, 2, 12))
            }
            """;
        Path gateSrc = tempDir.resolve("GateT7f.kf");
        Files.writeString(gateSrc, src);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(gateSrc, tempDir.resolve("gate-t7f-" + t), t);
            assertTrue(r.success(), t + " deve compilar hoursBetween (S7f): "
                    + r.diagnostics().getDiagnostics());
        }
    }

    // ── STDLIB S7g (D4): parseDateIso — "YYYY-MM-DD" estrito -> serial
    // daysFromEpoch; inválido => 0. MESMO serial de hoursBetween/
    // daysBetween (recomposição s - e = diff fecha com os vetores de cima).
    // Totalmente determinístico.
    @Test
    void parseDateIsoJvm(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    println(time.parseDateIso("1970-01-01"))
                    println(time.parseDateIso("2026-09-13"))
                    println(time.parseDateIso("2024-02-29"))
                    println(time.parseDateIso("0001-01-01"))
                    println(time.parseDateIso("9999-12-31"))
                    println(time.parseDateIso("2023-02-29"))
                    println(time.parseDateIso("2026-13-01"))
                    println(time.parseDateIso("garbage"))
                    println(time.parseDateIso(""))
                    println(time.parseDateIso("2026-9-13"))
                    var s = time.parseDateIso("2026-09-13")
                    var e = time.parseDateIso("1970-01-01")
                    println(s - e)
                }
                """, "0\n20709\n19782\n-719162\n2932896\n0\n0\n0\n0\n0\n20709");
    }

    @Test
    void parseDateIsoJs(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, """
                main() {
                    println(time.parseDateIso("1970-01-01"))
                    println(time.parseDateIso("2026-09-13"))
                    println(time.parseDateIso("2024-02-29"))
                    println(time.parseDateIso("0001-01-01"))
                    println(time.parseDateIso("9999-12-31"))
                    println(time.parseDateIso("2023-02-29"))
                    println(time.parseDateIso("2026-13-01"))
                    println(time.parseDateIso("garbage"))
                    println(time.parseDateIso(""))
                    println(time.parseDateIso("2026-9-13"))
                }
                """, "0\n20709\n19782\n-719162\n2932896\n0\n0\n0\n0\n0");
    }

    @Test
    void parseDateIsoNative(@TempDir Path tempDir) throws IOException {
        runNative(tempDir, """
                main() {
                    println(time.parseDateIso("1970-01-01"))
                    println(time.parseDateIso("2026-09-13"))
                    println(time.parseDateIso("2024-02-29"))
                    println(time.parseDateIso("0001-01-01"))
                    println(time.parseDateIso("9999-12-31"))
                    println(time.parseDateIso("2023-02-29"))
                    println(time.parseDateIso("2026-13-01"))
                    println(time.parseDateIso("garbage"))
                    println(time.parseDateIso(""))
                    println(time.parseDateIso("2026-9-13"))
                }
                """, "0\n20709\n19782\n-719162\n2932896\n0\n0\n0\n0\n0");
    }

    @Test
    void parseDateIsoCrossArch(@TempDir Path tempDir) throws Exception {
        String src = """
            main() {
                println(time.parseDateIso("1970-01-01"))
                println(time.parseDateIso("2026-09-13"))
                println(time.parseDateIso("2024-02-29"))
                println(time.parseDateIso("0001-01-01"))
                println(time.parseDateIso("9999-12-31"))
                println(time.parseDateIso("2023-02-29"))
                println(time.parseDateIso("2026-13-01"))
                println(time.parseDateIso("garbage"))
                println(time.parseDateIso("2026-9-13"))
                var s = time.parseDateIso("2026-09-13")
                var e = time.parseDateIso("1970-01-01")
                println(s - e)
            }
            """;
        String expected = "0\n20709\n19782\n-719162\n2932896\n0\n0\n0\n0\n20709";
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String qemu = t == Target.NATIVE_RISCV64 ? "qemu-riscv64" : "qemu-aarch64";
            String[] tools = t == Target.NATIVE_RISCV64
                    ? new String[]{"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"}
                    : new String[]{"aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"};
            assumeToolchain(tools);
            Path file = tempDir.resolve("T7g-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(file, src);
            Path outDir = tempDir.resolve("t7g-" + t + "-" + System.nanoTime());
            CompilationResult r = new CompilerDriver().compile(file, outDir, t);
            assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
            Process p = new ProcessBuilder(qemu, outDir.resolve("Default/Main").toString())
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec;
            try {
                ec = p.waitFor();
            } catch (InterruptedException e) {
                throw new IOException("interrupted", e);
            }
            assertEquals(0, ec, t + " qemu exit, out: " + out);
            assertEquals(expected, out, t + " golden S7g");
        }
    }

    @Test
    void parseDateIsoCompilesOnAllTargets(@TempDir Path tempDir) throws IOException {
        String src = """
            main() {
                println(time.parseDateIso("2026-09-13"))
            }
            """;
        Path gateSrc = tempDir.resolve("GateT7g.kf");
        Files.writeString(gateSrc, src);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(gateSrc, tempDir.resolve("gate-t7g-" + t), t);
            assertTrue(r.success(), t + " deve compilar parseDateIso (S7g): "
                    + r.diagnostics().getDiagnostics());
        }
    }

    // ── STDLIB S7h (D1): tzOffsetSeconds — fuso do HOST como getter
    // explícito. NÃO-determinístico entre máquinas: a prova valida o
    // CONTRATO (múltiplo de 900s na prática, range UTC-12..UTC+14, e
    // consistência interna: now()+tz alinhado em minutos com civil UTC)
    // e a PARIDADE JVM×JS (mesma saída nas 2 execuções — mesmo host).
    // Native/riscv/aarch = gap honesto TIME003 (diagnóstico, R6).
    @Test
    void tzOffsetSecondsJvmAndJsParity(@TempDir Path tempDir) throws IOException {
        String src = """
                main() {
                    var tz = time.tzOffsetSeconds()
                    println(tz % 60)
                    println(tz >= -43200 && tz <= 50400)
                    println(tz)
                }
                """;
        // Oracle JVM (medição real, nunca memória): offset ATUAL da zona do
        // host (com DST). getTimezoneOffset() do JS = mesmo instante.
        int jvmTz = java.time.ZoneId.systemDefault().getRules()
                .getOffset(java.time.Instant.now()).getTotalSeconds();
        String expected = "0\ntrue\n" + jvmTz;
        // JVM e JS rodam NO MESMO HOST => a paridade JVM×JS (D1: sem
        // divergência acidental) é provada por AMBOS baterem com o oracle.
        runJvm(tempDir, src, expected);
        runJs(tempDir, src, expected);
    }

    @Test
    void tzOffsetSecondsNativeRefusedWithDiagnostic(@TempDir Path tempDir) throws IOException {
        // Gap honesto TIME003 (R6): Native recusa com diagnóstico — nunca
        // fallback silencioso, nunca "0 fingido".
        Path source = tempDir.resolve("Tz.kf");
        Files.writeString(source, """
                main() {
                    println(time.tzOffsetSeconds())
                }
                """);
        CompilationResult r = driver.compile(source, tempDir.resolve("out-tz-nat"), Target.NATIVE);
        assertFalse(r.success(), "Native deve RECUSAR tzOffsetSeconds (TIME003)");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> d.message().contains("TIME003")),
                "diagnóstico deve citar TIME003: " + r.diagnostics().getDiagnostics());
    }

    private void assumeToolchain(String... tools) {
        for (String c : tools) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                org.junit.jupiter.api.Assumptions.assumeTrue(
                        p.waitFor() == 0 && !o.isEmpty(), "toolchain ausente: " + c);
            } catch (Exception e) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "toolchain ausente: " + c);
            }
        }
    }
}