package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * R6 sweep guard: domain namespaces must refuse unsupported targets with a
 * documented compile-time gap code — never a silent stub nor a link break.
 * Each case pins a row of {@code docs/bugs-and-gaps/backend-parity.md}
 * (Documented Gaps). Measured on the CLI 17/09; this keeps the matrix honest.
 *
 * The web-gate cases pin the code the corpus promises for each feature
 * (TLS {@code WEB002}, SSE {@code WEB003}, WebSocket {@code WEB004},
 * security middleware {@code WEB006}) — the {@code app.serveDir} /
 * {@code WEB005} drift of §275 happened exactly because the catch-all
 * emitted {@code WEB001} while only the docs knew {@code WEB005}.
 */
class DomainGapCodesTest {
    private final CompilerDriver driver = new CompilerDriver();

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
