package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Faces UTF-16 de String nos cross-arch (bug 43 residual — B34):
 * length/charAt/substring contam code units UTF-16 (JVM/x86 idênticos),
 * não bytes UTF-8. Arquivo NOVO e isolado de propósito: as faces x86 já
 * vivem em NativeE2ETest (stringLengthUtf16 etc.) e os E2Es de cross
 * (NativeRiscv64E2ETest/NativeAarch64E2ETest) estão na lane do sweep
 * §104/§106/§107 — nada de dois agentes no mesmo arquivo (DOING.md).
 * Golden = oracle JVM medido no MESMO programa (12 valores).
 */
class NativeStringUtf16CrossTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private String runCross(Path tempDir, String source, String qemu, String archFlag)
            throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        Path outDir = tempDir.resolve("out-" + archFlag);
        CompilationResult result = driver.compile(src, outDir,
                dev.kof.compiler.Target.valueOf(archFlag));
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(qemu.substring(5), bin);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        assertEquals(0, ec, "exit code, output: " + output);
        return output;
    }

    private static final String PROGRAM = """
            main() {
                var s = "café"
                println(s.length)
                println(s.charAt(3))
                println(s.substring(1))
                println(s.substring(1).length)
                println(s.substring(3))
                var e = "a😀b"
                println(e.length)
                println(e.charAt(1) as Int)
                println(e.charAt(2) as Int)
                println(e.charAt(3))
                println(e.substring(0, 3).length)
                println(e.substring(1, 3))
                println(e.substring(3))
            }
            """;

    // Oracle JVM medido 11/09 (JVM == x86_64 == Script == riscv == aarch).
    //
    // ATUALIZADO 19/09 (#259/N2, fechamento do §333): o golden de 11/09 era
    // PRÉ-D-PRINT. O §216 (15/09) decidiu que `Char` imprime o CARÁTER, não o
    // codepoint — e o x86 já migrou (`NativeE2ETest.nativeStringCharAtUtf16`
    // espera `é` para `println(s.charAt(3))` e usa `as Int` nos surrogates
    // soltos, que é uma asserção melhor: um surrogate solto não é um caractere
    // exibível). O §333 é exatamente estender isso ao cross, então o golden
    // passa a ser o mesmo FORMATO do x86. Antes desta correção o cross ainda
    // imprimia `233`/`55357`/`56832` e este teste passava por fixar o
    // comportamento antigo.
    private static final String GOLDEN =
            "4\né\nafé\n3\né\n4\n55357\n56832\nb\n3\n😀\nb";

    @Test
    void riscv64StringUtf16Faces(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(GOLDEN, runCross(tempDir, PROGRAM, "qemu-riscv64", "NATIVE_RISCV64"));
    }

    @Test
    void aarch64StringUtf16Faces(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(GOLDEN, runCross(tempDir, PROGRAM, "qemu-aarch64", "NATIVE_AARCH64"));
    }

    // B35 (§43 cross + §102 cross): busca em CODE UNITS UTF-16 + `from`
    // respeitado (clamps do JDK 21). haystack "café😀x" = 7 units
    // (c,a,f,é,D83D,DE00,x). 16 vetores: indexOf/lastIndexOf com e sem
    // from (needle vazia, out-of-range, negative, corte de par), startsWith,
    // contains. Golden = oracle JVM medido 11/09 (mesmo programa).
    private static final String SEARCH_PROGRAM = """
            main() {
                var h = "café😀x"
                println(h.indexOf("é"))
                println(h.indexOf("😀"))
                println(h.indexOf("x"))
                println(h.indexOf(""))
                println(h.indexOf("é", 3))
                println(h.indexOf("x", 5))
                println(h.indexOf("x", 99))
                println(h.indexOf("z"))
                println(h.lastIndexOf("é"))
                println(h.lastIndexOf("café"))
                println(h.lastIndexOf(""))
                println(h.lastIndexOf("x", 2))
                println(h.lastIndexOf("x", -1))
                println(h.startsWith("café"))
                println(h.startsWith("😀x", 4))
                println(h.startsWith("x", 6))
                println(h.startsWith("x", 7))
                println(h.startsWith("x", -1))
                println(h.startsWith("", 7))
                println(h.startsWith("", 99))
                println(h.contains("é"))
                println(h.contains("z"))
            }
            """;

    private static final String SEARCH_GOLDEN =
            "3\n4\n6\n0\n3\n6\n-1\n-1\n3\n0\n7\n-1\n-1\ntrue\ntrue\ntrue\nfalse\nfalse\ntrue\nfalse\ntrue\nfalse";

    @Test
    void riscv64StringSearchUtf16(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(SEARCH_GOLDEN, runCross(tempDir, SEARCH_PROGRAM, "qemu-riscv64", "NATIVE_RISCV64"));
    }

    @Test
    void aarch64StringSearchUtf16(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(SEARCH_GOLDEN, runCross(tempDir, SEARCH_PROGRAM, "qemu-aarch64", "NATIVE_AARCH64"));
    }
}
