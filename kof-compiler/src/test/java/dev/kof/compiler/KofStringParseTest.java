package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * bug 79 (R6/paridade): String.toInt/toLong = contrato do JDK
 * (Integer/Long.parseLong(s.trim())) — entrada inválida ou overflow LANÇA
 * exceção String (capturável em try/catch), nunca número silencioso. Coberto
 * aqui nos 3 alvos golden (JVM/JS/Native-x86); riscv64/aarch64 = vetores
 * idênticos em NativeRiscv64E2ETest/NativeAarch64E2ETest (qemu).
 */
class KofStringParseTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String GOLDEN = """
            main() {
                println("42".toInt())
                println("-7".toInt())
                println("0".toInt())
                try { println("abc".toInt()); println("S1") } catch (String e) { println("T1") }
                try { println("12a34".toInt()); println("S2") } catch (String e) { println("T2") }
                println(" -42 ".toInt())
                println("+7".toInt())
                println("-2147483648".toInt())
                try { println("2147483648".toInt()); println("S3") } catch (String e) { println("T3") }
                try { println("999999999999".toInt()); println("S4") } catch (String e) { println("T4") }
                try { println("".toInt()); println("S5") } catch (String e) { println("T5") }
                println("1234567890".toLong())
                println("-9223372036854775807".toLong())
                try { println("9223372036854775808".toLong()); println("S6") } catch (String e) { println("T6") }
                var v = "-9223372036854775808".toLong()
                println(v < 0)
                println(("0".toLong()) == 0)
                println(v)
            }
            """;
    private static final String EXPECTED =
            "42\n-7\n0\nT1\nT2\n-42\n7\n-2147483648\nT3\nT4\nT5\n1234567890\n"
            + "-9223372036854775807\nT6\ntrue\ntrue\n-9223372036854775808";

    @Test
    void toIntToLongContractJvm(@TempDir Path tmp) throws Exception {
        runTarget(tmp, Target.JVM, "java");
    }

    @Test
    void toIntToLongContractNativeX86(@TempDir Path tmp) throws Exception {
        runTarget(tmp, Target.NATIVE, "bin");
    }

    // §81 (5b, 13/09): toLong no JS = BigInt — paridade 64-bit REAL. O golden
    // JS é agora IDÊNTICO ao JVM/Native (GOLDEN/EXPECTED): 2^63-1 exato,
    // overflow de Long lança (range check ±2^63), "-9223372036854775808" ok.
    // (Antes: Number double 53-bit — bug 81; a limitação ±2^53 acabou.)
    private static final String GOLDEN_JS = GOLDEN;
    private static final String EXPECTED_JS = EXPECTED;

    @Test
    void toIntToLongContractJs(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, GOLDEN_JS);
        Path out = tmp.resolve("out-js");
        CompilationResult r = driver.compile(src, out, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        Path mjs;
        try (var s = Files.walk(out)) {
            mjs = s.filter(p -> p.getFileName().toString().equals("Default.mjs"))
                   .findFirst().orElseThrow();
        }
        Process p = new ProcessBuilder("node", mjs.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JS exit " + ec + ": " + output);
        assertEquals(EXPECTED_JS, output, "JS stdout");
    }

    private void runTarget(Path tmp, Target t, String kind) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, GOLDEN);
        Path out = tmp.resolve("out-" + t);
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb;
        if (t == Target.JVM) {
            pb = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", out.toString(), "Default.Main");
        } else {
            pb = new ProcessBuilder(out.resolve("Default/Main").toString());
        }
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = proc.waitFor();
        assertEquals(0, ec, t + " exit " + ec + ": " + output);
        assertEquals(EXPECTED, output, t + " stdout");
    }

    // === bug 82 (face x86): toDouble/toFloat no contrato do JDK (parse com
    // trim, +/-, expoente, NaN/Infinity literais, throw em invalido). O
    // oracle e o proprio output JVM medido 10/09 (paridade JVM==x86==JS em
    // 24/24). NaN == NaN eh false (IEEE, idem JVM). Limites documentados
    // §82: mantissa >19 digitos LANCA no x86 (JVM parseia), formato do
    // printer NAO e testado aqui (bug 44). Cross-arch (riscv/aarch):
    // undefined reference honesta (COMP001) — pendente FLT001, §82.
    @Test
    void toDoubleToFloatContractJvm(@TempDir Path tmp) throws Exception {
        runFp(tmp, Target.JVM);
    }

    @Test
    void toDoubleToFloatContractNativeX86(@TempDir Path tmp) throws Exception {
        runFp(tmp, Target.NATIVE);
    }

    @Test
    void toDoubleToFloatContractJs(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, FP_GOLDEN);
        Path out = tmp.resolve("out-fp-js");
        CompilationResult r = driver.compile(src, out, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        Path mjs;
        try (var s = Files.walk(out)) {
            mjs = s.filter(x -> x.getFileName().toString().equals("Default.mjs"))
                   .findFirst().orElseThrow();
        }
        Process p = new ProcessBuilder("node", mjs.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JS fp exit " + ec + ": " + output);
        assertEquals(FP_EXPECTED, output, "JS fp stdout");
    }

    private void runFp(Path tmp, Target t) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, FP_GOLDEN);
        Path out = tmp.resolve("out-fp-" + t);
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), t + " fp compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = (t == Target.JVM)
                ? new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                        "-cp", out.toString(), "Default.Main")
                : new ProcessBuilder(out.resolve("Default/Main").toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = proc.waitFor();
        assertEquals(0, ec, t + " fp exit " + ec + ": " + output);
        assertEquals(FP_EXPECTED, output, t + " fp stdout");
    }

    private static final String FP_GOLDEN = """
main() {
    println("0.3".toDouble() == 0.3)
    println("123.456".toDouble() == 123.456)
    println("1.5e2".toDouble() == 150.0)
    println("-1.5e-2".toDouble() == -0.015)
    println("Infinity".toDouble() > 1e300)
    println("-Infinity".toDouble() < -1e300)
    try { println("infinity".toDouble()); println("S1") } catch (String e) { println("T1") }
    try { println("Inf".toDouble()); println("S2") } catch (String e) { println("T2") }
    println("5.e3".toDouble() == 5000.0)
    println("1e3".toDouble() == 1000.0)
    try { println("1e".toDouble()); println("S3") } catch (String e) { println("T3") }
    println(" 1.5 ".toDouble() == 1.5)
    println("+2.25".toDouble() == 2.25)
    println("0.1".toDouble() + "0.2".toDouble() == 0.30000000000000004)
    println(".5".toDouble() == 0.5)
    println("5.".toDouble() == 5.0)
    println("NaN".toDouble() == "NaN".toDouble())
    println("1e400".toDouble() > 1e300)
    println("1e-400".toDouble() == 0.0)
    println("2.5".toFloat() == 2.5)
    try { println("abc".toFloat()); println("S5") } catch (String e) { println("T5") }
    try { println("abc".toDouble()); println("S6") } catch (String e) { println("T6") }
    try { println("1.2.3".toDouble()); println("S7") } catch (String e) { println("T7") }
    println("7".toDouble() == 7.0)
    println("1e3".toFloat() == 1000.0)
    try { println("".toDouble()); println("S8") } catch (String e) { println("T8") }
    try { println("   ".toDouble()); println("S9") } catch (String e) { println("T9") }
    val ls1 = "1e-400"
    val ls2 = "2.5"
    var ln = 0
    var lok = true
    while (ln < 3) {
        val ld = ls1.toDouble()
        val lf = ls2.toFloat()
        lok = lok && (lf == 2.5) && (ld == 0.0)
        ln = ln + 1
    }
    println(lok)
}    """;

    // §175 (13/09): ""/"   " LANÇAM nos 5 alvos (T8/T9) — no x86/riscv era
    // 0.0 silencioso (.Lpdd_vazio/.Lpd_vazio devolviam xorpd/li a0,0), JVM
    // lança NumberFormatException. R6/paridade fechada; a prova do OrDefault
    // com vazio fica em KofMathTest.PARSEORD (linha `parseDoubleOrDefault("", d)`).
    private static final String FP_EXPECTED = "true\ntrue\ntrue\ntrue\ntrue\ntrue\nT1\nT2\ntrue\ntrue\nT3\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\ntrue\ntrue\ntrue\nT5\nT6\nT7\ntrue\ntrue\nT8\nT9\ntrue";
    // === bug 82 (face cross): riscv64/aarch64 definem kof_string_to_double/float
    // (NativeRiscvAsmRtB31, espelho do x86 RuntimeStringParseFp). Oracle SEM
    // print de double (print double segue FLT001 no cross) — throw vira var +
    // comparacao booleana que da o MESMO marker T# se o parse lancar (R6).
    // Esperado = JVM/x86/JS medido 10/09, idêntico nos 5 alvos (25 vetores).
    @Test
    void toDoubleToFloatContractRiscv64(@TempDir Path tmp) throws Exception {
        runFpCross("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64",
                Target.NATIVE_RISCV64, "qemu-riscv64", tmp, "out-fp-riscv");
    }

    @Test
    void toDoubleToFloatContractAarch64(@TempDir Path tmp) throws Exception {
        runFpCross("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64",
                Target.NATIVE_AARCH64, "qemu-aarch64", tmp, "out-fp-aarch");
    }

    private void runFpCross(String asBin, String ldBin, String qemu, Target t,
            String qemuBin, Path tmp, String outName) throws Exception {
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String b : new String[]{asBin, ldBin, qemu}) {
            boolean ok = false;
            try {
                Process c = new ProcessBuilder("which", b).redirectErrorStream(true).start();
                String o = new String(c.getInputStream().readAllBytes()).trim();
                ok = !o.isEmpty() && c.waitFor() == 0;
            } catch (Exception e) { /* ausente */ }
            if (!ok) missing.add(b);
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(missing.isEmpty(),
                "toolchain cross ausente: " + missing);
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, FP_CROSS_GOLDEN);
        Path out = tmp.resolve(outName);
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), t + " fp cross compile: " + r.diagnostics().getDiagnostics());
        Process p = NativeRiscv64E2ETest.qemu(qemuBin.substring(5), out.resolve("Default/Main"))
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).trim();
        int ec = p.waitFor();
        assertEquals(0, ec, t + " fp cross exit " + ec + ": " + output);
        assertEquals(FP_CROSS_EXPECTED, output, t + " fp cross stdout");
    }

    private static final String FP_CROSS_GOLDEN = """
main() {
    println("0.3".toDouble() == 0.3)
    println("123.456".toDouble() == 123.456)
    println("1.5e2".toDouble() == 150.0)
    println("-1.5e-2".toDouble() == -0.015)
    println("Infinity".toDouble() > 1e300)
    println("-Infinity".toDouble() < -1e300)
    try { var d1 = "infinity".toDouble(); println(d1 == 1.0); println("S1") } catch (String e) { println("T1") }
    try { var d2 = "Inf".toDouble(); println(d2 == 1.0); println("S2") } catch (String e) { println("T2") }
    println("5.e3".toDouble() == 5000.0)
    println("1e3".toDouble() == 1000.0)
    try { var d3 = "1e".toDouble(); println(d3 == 1.0); println("S3") } catch (String e) { println("T3") }
    println(" 1.5 ".toDouble() == 1.5)
    println("+2.25".toDouble() == 2.25)
    println("0.1".toDouble() + "0.2".toDouble() == 0.30000000000000004)
    println(".5".toDouble() == 0.5)
    println("5.".toDouble() == 5.0)
    println("NaN".toDouble() == "NaN".toDouble())
    println("1e400".toDouble() > 1e300)
    println("1e-400".toDouble() == 0.0)
    println("2.5".toFloat() == 2.5)
    try { var f1 = "abc".toFloat(); println(f1 == 1.0); println("S5") } catch (String e) { println("T5") }
    try { var d4 = "abc".toDouble(); println(d4 == 1.0); println("S6") } catch (String e) { println("T6") }
    try { var d5 = "1.2.3".toDouble(); println(d5 == 1.0); println("S7") } catch (String e) { println("T7") }
    println("7".toDouble() == 7.0)
    println("1e3".toFloat() == 1000.0)
    val ls1 = "1e-400"
    val ls2 = "2.5"
    var ln = 0
    var lok = true
    while (ln < 3) {
        val ld = ls1.toDouble()
        val lf = ls2.toFloat()
        lok = lok && (lf == 2.5) && (ld == 0.0)
        ln = ln + 1
    }
    println(lok)
    }
    """;
    private static final String FP_CROSS_EXPECTED = "true\ntrue\ntrue\ntrue\ntrue\ntrue\nT1\nT2\ntrue\ntrue\nT3\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\ntrue\ntrue\ntrue\nT5\nT6\nT7\ntrue\ntrue\ntrue";
}
