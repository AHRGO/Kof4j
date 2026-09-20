package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * #549 / §370 — o call-site de um `extern` obedece a MESMA regra de conversão
 * numérica do KOF que uma chamada comum (TypeChecker.isAssignable: widening +
 * `Double→Float` "o lowering emite D2F"). Antes: o Native empilhava o argumento
 * CRU e o marshaling SysV reinterpretava os bits pelo slot C — `fmid(1, 2)` num
 * slot `Float` imprimia `3.0E-45`, `fmid(1.0, 2.0)` imprimia `0.0` (valor errado
 * silencioso, R6) e a JVM lançava CCE em `Double→Float`.
 *
 * <p>Sem fixture C: só símbolos reais da libm/libc (`fmaxf`, `ldexpf`, `sqrt`,
 * `pow`, `labs`, `abs`) — o teste roda onde há Linux + toolchain Native. O golden é
 * a conversão explícita (`as Float`, o contrato que já funcionava) e a saída IEEE
 * exata; JVM, Native e JS-host têm de concordar byte a byte. Rejeições
 * (String/Bool/narrowing) travam o diagnóstico SEM014 no call-site nos 3 alvos.
 */
class FfiExternTypeConversionTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static void requireLinux() {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "FFI real usa libm/libc (Linux)");
    }

    private static String run(Process p, String label) throws IOException {
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        try {
            assertTrue(p.waitFor(120, TimeUnit.SECONDS), label + " timeout");
            assertEquals(0, p.exitValue(), () -> label + " exit code, output: " + o);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        return o;
    }

    private String jvm(Path dir, String base, String kof) throws IOException {
        Path src = dir.resolve(base + "-jvm.kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-" + base + "-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), () -> "JVM compile " + base + ": " + r.diagnostics().getDiagnostics());
        return run(new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-cp", out.toString(), "Default.Main")
                .directory(dir.toFile()).redirectErrorStream(true).start(), "JVM " + base);
    }

    private String nativeBin(Path dir, String base, String kof) throws IOException {
        Path src = dir.resolve(base + "-nat.kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-" + base + "-nat");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), () -> "NATIVE compile " + base + ": " + r.diagnostics().getDiagnostics());
        return run(new ProcessBuilder(out.resolve("Default/Main").toString())
                .directory(dir.toFile()).redirectErrorStream(true).start(), "NATIVE " + base);
    }

    private String js(Path dir, String base, String kof) throws IOException {
        Path src = dir.resolve(base + "-js.kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-" + base + "-js");
        CompilationResult r = driver.compile(src, out, Target.JS);
        assertTrue(r.success(), () -> "JS compile " + base + ": " + r.diagnostics().getDiagnostics());
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), buf,
                new ByteArrayInputStream(new byte[0]), buf);
        String o = buf.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, ec, () -> "JS exit code, output: " + o);
        return o;
    }

    /** O MESMO fonte, os 3 alvos, a MESMA saída exata (regra 5 do freeze). */
    private void assertAllTargets(Path dir, String base, String kof, String expected) throws IOException {
        requireLinux();
        assertEquals(expected, jvm(dir, base, kof), "JVM " + base);
        assertEquals(expected, nativeBin(dir, base, kof), "NATIVE " + base);
        assertEquals(expected, js(dir, base, kof), "JS-host " + base);
    }

    // ── slot Float ────────────────────────────────────────────────────────
    @Test
    void floatSlotExplicitCastIsTheUnchangedControl(@TempDir Path dir) throws Exception {
        assertAllTargets(dir, "fexplicit", """
                extern "libm.so.6" fmaxf(Float a, Float b): Float

                main() {
                    println(fmaxf(1.0 as Float, 2.5 as Float))
                }
                """, "2.5");
    }

    @Test
    void floatSlotAcceptsIntLiteralAndConvertsIntToFloat(@TempDir Path dir) throws Exception {
        // RED antes do fix: Native imprimia o inteiro reinterpretado como float
        // (denormal ~1e-45); a JVM só acertava por acidente do Number do runtime.
        assertAllTargets(dir, "fint", """
                extern "libm.so.6" fmaxf(Float a, Float b): Float

                main() {
                    println(fmaxf(1, 2))
                }
                """, "2.0");
    }

    @Test
    void floatSlotAcceptsDoubleLiteralAndNarrowsDoubleToFloat(@TempDir Path dir) throws Exception {
        // RED antes do fix: Native imprimia 0.0 (bits de double no slot float) e a
        // JVM lançava ClassCastException Double→Float em execução.
        assertAllTargets(dir, "fdouble", """
                extern "libm.so.6" fmaxf(Float a, Float b): Float

                main() {
                    println(fmaxf(1.0, 2.5))
                }
                """, "2.5");
    }

    @Test
    void floatSlotConvertsVariablesOfEveryNumericTypeNotOnlyLiterals(@TempDir Path dir) throws Exception {
        assertAllTargets(dir, "fvars", """
                extern "libm.so.6" fmaxf(Float a, Float b): Float
                extern "libm.so.6" ldexpf(Float m, Int e): Float

                main() {
                    Int i = 7
                    Long l = 9
                    Double d = 3.5
                    Float f = 4.25 as Float
                    println(fmaxf(i, l))
                    println(fmaxf(d, f))
                    println(ldexpf(i, 2))
                }
                """, "9.0\n4.25\n28.0");
    }

    // ── slot Double / Long ────────────────────────────────────────────────
    @Test
    void doubleSlotConvertsIntLongAndFloatArguments(@TempDir Path dir) throws Exception {
        // Mesma classe do bug: `sqrt(9)` empilhava o inteiro cru no slot xmm.
        assertAllTargets(dir, "dslot", """
                extern "libm.so.6" sqrt(Double x): Double
                extern "libm.so.6" pow(Double x, Double y): Double

                main() {
                    Long n = 16
                    Float f = 2.25 as Float
                    println(sqrt(9))
                    println(sqrt(n))
                    println(sqrt(f))
                    println(pow(2, 10))
                }
                """, "3.0\n4.0\n1.5\n1024.0");
    }

    @Test
    void longSlotConvertsIntArgument(@TempDir Path dir) throws Exception {
        assertAllTargets(dir, "lslot", """
                extern "libc.so.6" labs(Long x): Long

                main() {
                    Int i = -5
                    println(labs(i))
                    println(labs(-7))
                }
                """, "5\n7");
    }

    @Test
    void idempotentSecondCallGivesTheSameResult(@TempDir Path dir) throws Exception {
        assertAllTargets(dir, "twice", """
                extern "libm.so.6" fmaxf(Float a, Float b): Float

                main() {
                    println(fmaxf(1, 2))
                    println(fmaxf(1, 2))
                }
                """, "2.0\n2.0");
    }

    // ── rejeições no call-site (compile-time, frontend compartilhado) ────
    private void assertRejected(Path dir, String base, String kof, String code) throws IOException {
        for (Target t : new Target[] {Target.JVM, Target.NATIVE, Target.JS}) {
            Path src = dir.resolve(base + "-" + t + ".kf");
            Files.writeString(src, kof);
            CompilationResult r = new CompilerDriver().compile(src, dir.resolve("out-" + base + "-" + t), t);
            assertFalse(r.success(), t + " " + base + ": deve rejeitar no compile-time, nunca linkar bit-garbage");
            String diags = r.diagnostics().getDiagnostics().toString();
            assertTrue(diags.contains(code), t + " " + base + ": esperado " + code + ", veio: " + diags);
        }
    }

    @Test
    void stringInFloatSlotIsRejectedSem014(@TempDir Path dir) throws Exception {
        assertRejected(dir, "strfloat", """
                extern "libm.so.6" fmaxf(Float a, Float b): Float

                main() {
                    println(fmaxf("a", 1))
                }
                """, "SEM014");
    }

    @Test
    void narrowingDoubleToIntSlotIsRejectedSem014(@TempDir Path dir) throws Exception {
        assertRejected(dir, "narrow", """
                extern "libc.so.6" abs(Int x): Int

                main() {
                    println(abs(2.5))
                }
                """, "SEM014");
    }

    @Test
    void boolInNumericSlotIsRejectedSem014(@TempDir Path dir) throws Exception {
        assertRejected(dir, "boolint", """
                extern "libc.so.6" abs(Int x): Int

                main() {
                    println(abs(true))
                }
                """, "SEM014");
    }

    @Test
    void wrongArityStillRejectedSem013(@TempDir Path dir) throws Exception {
        assertRejected(dir, "arity", """
                extern "libm.so.6" fmaxf(Float a, Float b): Float

                main() {
                    println(fmaxf(1))
                }
                """, "SEM013");
    }
}
