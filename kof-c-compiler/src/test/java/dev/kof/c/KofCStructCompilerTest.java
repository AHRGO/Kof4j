package dev.kof.c;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fatia C3 ({@code docs/development/kof-c-cross.md}): tipos {@code struct} e
 * struct por valor em parâmetro. Cada campo {@code int} é um C {@code int} de
 * 4 bytes (matches {@code AbiLayout.Scalar.INT}); um struct de até 8 B é um
 * eightbyte, então atravessa em UM registrador de argumento — o mesmo caminho
 * do {@code div_t} da libc. Oráculo x86_64 + concordância riscv64/aarch64 sob
 * qemu; golden de medição real (REGRA 5).
 */
class KofCStructCompilerTest {

    private record Prog(String name, String src, String golden) {}

    private static final List<Prog> PROGRAMS = List.of(
            new Prog("local struct field round trip", """
                    struct Pair { int a; int b; };
                    void main() {
                      struct Pair s;
                      s.a = 7;
                      s.b = 8;
                      print_arg = s.a;
                      print();
                      print_arg = s.b;
                      print();
                    }
                    """, "7\n8"),
            new Prog("struct passed by value", """
                    struct Pair { int a; int b; };
                    int take(struct Pair p) { return p.a + p.b; }
                    void main() {
                      struct Pair s;
                      s.a = 30;
                      s.b = 12;
                      print_arg = take(s);
                      print();
                    }
                    """, "42"),
            new Prog("negative field sign-extends", """
                    struct Pair { int a; int b; };
                    void main() {
                      struct Pair s;
                      s.a = 0 - 5;
                      s.b = 100;
                      print_arg = s.a + s.b;
                      print();
                    }
                    """, "95"),
            new Prog("struct and scalar arguments mixed", """
                    struct Pair { int a; int b; };
                    int addto(struct Pair p, int k) { return (p.a + p.b) + k; }
                    void main() {
                      struct Pair s;
                      s.a = 1;
                      s.b = 2;
                      print_arg = addto(s, 39);
                      print();
                    }
                    """, "42"),
            new Prog("global struct fields", """
                    struct Pair { int a; int b; };
                    struct Pair g;
                    void main() { g.a = 11; g.b = 31; print_arg = g.a + g.b; print(); }
                    """, "42"));

    private static boolean has(String... cmds) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String c : cmds) {
            boolean found = false;
            for (String d : path.split(File.pathSeparator)) {
                if (Files.isExecutable(Path.of(d, c))) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    private static void requireTools(KofCTarget t) {
        assumeTrue(has(t.assembler().get(0), t.linker()) && (t.qemu() == null || has(t.qemu())),
                "toolchain " + t + " + qemu ausente — pulando (NATIVE002)");
    }

    private static String run(KofCTarget target, Path tmp, String source) throws Exception {
        Files.createDirectories(tmp);
        Path c = tmp.resolve("prog.c");
        Files.writeString(c, source);
        KofCCompiler.CompileResult res = KofCCompiler.compile(c, tmp.resolve("out"), target);
        assertTrue(res.success(), "compile " + target + " falhou: " + res.diagnostics());
        List<String> cmd = new ArrayList<>();
        if (target.qemu() != null) cmd.add(target.qemu());
        cmd.add(res.binary().toString());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new AssertionError(target + " não terminou em 30s (saída: '" + out + "')");
        }
        assertEquals(0, p.exitValue(), "exit != 0 em " + target + " (saída: '" + out + "')");
        return out;
    }

    private static void assertAllPrograms(KofCTarget target, Path tmp) throws Exception {
        for (Prog p : PROGRAMS) {
            assertEquals(p.golden(), run(target, tmp.resolve(p.name().replace(' ', '_')), p.src()),
                    target + " / " + p.name());
        }
    }

    @Test
    void x86OracleMatchesEveryGolden(@TempDir Path tmp) throws Exception {
        requireTools(KofCTarget.X86_64);
        assertAllPrograms(KofCTarget.X86_64, tmp);
    }

    @Test
    void riscv64MatchesEveryGolden(@TempDir Path tmp) throws Exception {
        requireTools(KofCTarget.RISCV64);
        assertAllPrograms(KofCTarget.RISCV64, tmp);
    }

    @Test
    void aarch64MatchesEveryGolden(@TempDir Path tmp) throws Exception {
        requireTools(KofCTarget.AARCH64);
        assertAllPrograms(KofCTarget.AARCH64, tmp);
    }

    private static KofCCompiler.CompileResult compile(Path tmp, String source) throws Exception {
        Files.createDirectories(tmp);
        Path c = tmp.resolve("bad.c");
        Files.writeString(c, source);
        return KofCCompiler.compile(c, tmp.resolve("out"), KofCTarget.X86_64);
    }

    @Test
    void oversizedStructIsRejected(@TempDir Path tmp) throws Exception {
        String src = "struct T { int a; int b; int c; };\nvoid main() { struct T t; t.a = 1; }\n";
        var res = compile(tmp, src);
        assertFalse(res.success(), "struct > 8 B não é suportado ainda (R6)");
        assertTrue(res.diagnostics().contains("at most 8"), res.diagnostics());
    }

    @Test
    void unknownFieldIsRejected(@TempDir Path tmp) throws Exception {
        String src = "struct Pair { int a; int b; };\nvoid main() { struct Pair s; s.c = 1; }\n";
        var res = compile(tmp, src);
        assertFalse(res.success(), "campo inexistente deve falhar");
        assertTrue(res.diagnostics().contains("has no field c"), res.diagnostics());
    }

    @Test
    void fieldOnNonStructIsRejected(@TempDir Path tmp) throws Exception {
        var res = compile(tmp, "void main() { int x; x.a = 1; }\n");
        assertFalse(res.success(), "acesso de campo em não-struct deve falhar");
        assertTrue(res.diagnostics().contains("is not a struct variable"), res.diagnostics());
    }

    @Test
    void unknownStructTypeIsRejected(@TempDir Path tmp) throws Exception {
        var res = compile(tmp, "struct Nope n;\nvoid main() { print_arg = 0; print(); }\n");
        assertFalse(res.success(), "tipo struct desconhecido deve falhar");
        assertTrue(res.diagnostics().contains("unknown struct Nope"), res.diagnostics());
    }
}
