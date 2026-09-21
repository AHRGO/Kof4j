package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/**
 * R6 sweep guard: domain namespaces must refuse unsupported targets with a
 * documented compile-time gap code — never a silent stub nor a link break.
 * Each case pins a row of {@code docs/backend-parity.md} (Documented Gaps).
 * Measured on the CLI 17/09; this keeps the matrix honest.
 *
 * The web-gate cases pin the code the corpus promises for each feature
 * (TLS {@code WEB002}, SSE {@code WEB003}, WebSocket {@code WEB004},
 * security middleware {@code WEB006}) — the {@code app.serveDir} /
 * {@code WEB005} drift of §275 happened exactly because the catch-all
 * emitted {@code WEB001} while only the docs knew {@code WEB005}.
 *
 * {@link #everyPinnedGapIsDocumentedInTheParityMatrix()} closes the other
 * direction: a code this guard proves the compiler EMITS must also be in the
 * matrix (R6 — "every domain gap has a code + an entry in the parity
 * matrix"). The ledger is derived from this file's own {@code assertGap}
 * calls, so a new pin cannot be added without documenting it.
 */
class DomainGapCodesTest {
    private final CompilerDriver driver = new CompilerDriver();

    private static final Pattern GAP_CODE = Pattern.compile("\"([A-Z]{2,6}[0-9]{3})\"");

    @Test
    void processRunOnNativeIsProc001(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.NATIVE, "PROC001", """
            main() {
                val r = process.run("echo", "hi")
                println(r.stdout)
            }
            """);
    }

    @Test
    void processSpawnOnNativeIsProc001(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.NATIVE, "PROC001", """
            main() {
                val h = process.spawn("echo", "hi")
                println(if (h.alive()) "alive" else "dead")
            }
            """);
    }

    @Test
    void processSpawnOnJsHasNoGap(@TempDir Path tmp) throws Exception {
        // JS face landed 19/09 (KofJsProcessBridge host binding, F10 parity):
        // process.spawn must compile on JS now — the E2E parity lives in
        // ProcessSpawnE2ETest. Native keeps the PROC001 pin above.
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
            main() {
                val h = process.spawn("echo", "hi")
                println(if (h.alive()) "alive" else "dead")
            }
            """);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.JS);
        assertTrue(result.success(), "JS process.spawn must compile: "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void processSpawnOnJvmHasNoGap(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
            main() {
                val h = process.spawn("echo", "hi")
                println(if (h.alive()) "alive" else "dead")
            }
            """);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.JVM);
        assertTrue(result.success(), "JVM process.spawn must compile: "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void chacha20OnNativeIsSecn002(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.NATIVE, "SECN002", """
            main() {
                println(crypto.encryptChacha20("k", "m"))
            }
            """);
    }

    @Test
    void cookiesOnNativeIsSecn006(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.NATIVE, "SECN006", """
            main() {
                println(security.cookieSet("a", "b"))
            }
            """);
    }

    @Test
    void securityOnRiscvIsSecn000(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.NATIVE_RISCV64, "SECN000", """
            main() {
                println(crypto.sha256("x"))
            }
            """);
    }

    @Test
    void configOnCrossIsConf001(@TempDir Path tmp) throws Exception {
        // §425: riscv64/aarch64 have no kof_config_* runtime (the asm stub
        // echoes the default) — honest compile-time refusal, never wrong
        // values on the cross; JVM/x86/JS keep the real implementation.
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            assertGap(tmp, t, "CONF001", """
                main() {
                    println(config.str("server.port", "8080"))
                }
                """);
        }
    }

    @Test
    void configOnJsAndX86HasNoGap(@TempDir Path tmp) throws Exception {
        Path jsv = tmp.resolve("Main-js-" + System.nanoTime() + ".kf");
        Files.writeString(jsv, """
            main() {
                println(config.str("server.port", "8080"))
            }
            """);
        CompilationResult js = driver.compile(jsv, tmp.resolve("out-js"), Target.JS);
        assertTrue(js.success(), "JS config.str must compile (kof_platform): "
                + js.diagnostics().getDiagnostics());
        Path x86 = tmp.resolve("Main-x86-" + System.nanoTime() + ".kf");
        Files.writeString(x86, """
            main() {
                println(config.str("server.port", "8080"))
            }
            """);
        CompilationResult nat = driver.compile(x86, tmp.resolve("out-x86"), Target.NATIVE);
        assertTrue(nat.success(), "Native x86_64 config.str must compile (own asm): "
                + nat.diagnostics().getDiagnostics());
    }

    @Test
    void collectOnJsIsTime004(@TempDir Path tmp) throws Exception {
        // §426: JS runtime has no manual-GC face (kofGcCollectNow was imported
        // but never exported -> load failure); honest compile-time refusal.
        assertGap(tmp, Target.JS, "TIME004", """
            main() {
                time.collect()
            }
            """);
    }

    @Test
    void collectOnJvmAndX86HasNoGap(@TempDir Path tmp) throws Exception {
        for (Target t : new Target[]{Target.JVM, Target.NATIVE}) {
            Path file = tmp.resolve("Main-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(file, """
                main() {
                    time.collect()
                }
                """);
            CompilationResult r = driver.compile(file, tmp.resolve("out-" + t + "-" + System.nanoTime()), t);
            assertTrue(r.success(), t + " time.collect must compile (real GC runtime): "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void exportSpansOnNativeIsObs003(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.NATIVE, "OBS003", """
            main() {
                println(observability.exportSpans())
            }
            """);
    }

    @Test
    void webTlsOnNonJvmIsWeb002(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.JS, "WEB002", """
            main() {
                val app = web.app()
                app.listenSecure(8443)
            }
            """);
        assertGap(tmp, Target.NATIVE, "WEB002", """
            main() {
                val app = web.app()
                app.listenSecure(8443)
            }
            """);
    }

    @Test
    void webSseOnNativeIsWeb003(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.NATIVE, "WEB003", """
            main() {
                val app = web.app()
                app.sse("/events") { return "x" }
            }
            """);
    }

    @Test
    void webWsOnNonJvmIsWeb004(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.JS, "WEB004", """
            main() {
                val app = web.app()
                app.ws("/chat") { return "x" }
            }
            """);
        assertGap(tmp, Target.NATIVE, "WEB004", """
            main() {
                val app = web.app()
                app.ws("/chat") { return "x" }
            }
            """);
    }

    @Test
    void webSecurityMiddlewareOnNonJvmIsWeb006(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.JS, "WEB006", """
            main() {
                val app = web.app()
                app.security()
            }
            """);
        assertGap(tmp, Target.NATIVE, "WEB006", """
            main() {
                val app = web.app()
                app.security()
            }
            """);
    }

    @Test
    void processRunOnJvmHasNoGap(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
            main() {
                val r = process.run("echo", "hi")
                println(r.stdout)
            }
            """);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.JVM);
        assertTrue(result.success(), "JVM process.run must compile: "
                + result.diagnostics().getDiagnostics());
    }

    /**
     * Android reuses {@code JvmBackend} — the {@code kof.db}/{@code kof.orm}
     * over-gating of §278 was closed 20/09 by D-DB-GAPS DB-2 ("android is
     * JVM", byte-identical emission), so db now compiles clean on Android
     * (asserted right here AND byte-pinned in {@code KofDbE2ETest}). The
     * security gates stay honest refusals: §278 keeps SECN/GPU open (rule 6
     * — separate decisions not taken), so the doc gate still covers Android.
     */
    @Test
    void androidCompilesDbLikeJvmAndRefusesCryptoWithTheDocumentedCode(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Db-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
            main() {
                val c = db.connect("sqlite::memory:")
                println(c)
            }
            """);
        CompilationResult r = driver.compile(file, tmp.resolve("out-db"), Target.ANDROID);
        assertTrue(r.success(), "android compila kof.db desde DB-2 (20/09): "
                + r.diagnostics().getDiagnostics());
        assertGap(tmp, Target.ANDROID, "SECN003", """
            main() {
                println(crypto.sha512("x"))
            }
            """);
    }

    /**
     * R6 machine gate (mirrors the R1 boundary gate): every gap code this
     * guard pins — i.e. every code the compiler is proven to emit for a
     * domain namespace — must appear in {@code docs/backend-parity.md}. The
     * ledger is read from this file's own {@code assertGap} calls, so the
     * check cannot rot: adding a pin without a matrix row fails here.
     */
    @Test
    void everyPinnedGapIsDocumentedInTheParityMatrix() throws IOException {
        Path root = repoRoot();
        Set<String> pinned = new LinkedHashSet<>();
        Matcher m = GAP_CODE.matcher(Files.readString(root.resolve(
                "kof-compiler/src/test/java/dev/kof/compiler/DomainGapCodesTest.java")));
        while (m.find()) pinned.add(m.group(1));
        assertFalse(pinned.isEmpty(), "no gap codes found in this guard's assertGap calls");

        String matrix = Files.readString(root.resolve("docs/backend-parity.md"));
        for (String code : pinned) {
            assertTrue(matrix.contains(code),
                    "gap " + code + " is pinned by this guard (the compiler emits it) "
                            + "but has no entry in docs/backend-parity.md (R6)");
        }
    }

    /** Repo root, found by walking up to the parity matrix (same as
     *  {@code ConformanceMatrixDocTest}). */
    private static Path repoRoot() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++, p = p.getParent()) {
            if (Files.exists(p.resolve("docs/backend-parity.md"))) return p;
        }
        throw new IllegalStateException("backend-parity.md not found from " + p);
    }

    private void assertGap(Path tmp, Target target, String code, String source)
            throws java.io.IOException {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        CompilationResult result = driver.compile(file, tmp.resolve("out-" + System.nanoTime()), target);
        assertFalse(result.success(), target + " should refuse the call (" + code + ")");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains(code),
                "expected " + code + " for " + target + ", got: " + diags);
    }
}
