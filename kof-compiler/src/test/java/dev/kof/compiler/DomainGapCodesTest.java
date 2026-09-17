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
