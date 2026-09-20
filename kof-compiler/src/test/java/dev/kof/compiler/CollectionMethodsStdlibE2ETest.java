package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #386/#382 — Map.containsValue/putIfAbsent + List.indexOf/lastIndexOf/
 * addAll/subList/sort na stdlib. Goldens são MEDIÇÃO no oráculo JVM
 * (java.util é a fonte de verdade do contrato), nunca memória: hit/miss,
 * mapa vazio, putIfAbsent duas vezes (segunda devolve o existente e NÃO
 * sobrescreve), indexOf ausente -1, lastIndexOf com duplicatas, subList
 * begin==end (vazia) e out-of-range (a mensagem que o java.util lança,
 * medida com o processo morrendo exit=1), addAll true/false, sort crescente
 * e reverso, String e Double. Os gates SEM097 (ordem natural), NAT001
 * (Float no native — diagnóstico honesto, nunca ordem errada) e SEM025 de
 * aridade são compile-time compartilhados: um gate, os 4 alvos.
 */
class CollectionMethodsStdlibE2ETest {

    static final String PROGRAM = """
            main() {
                val m: Map<String, Int> = mapOf()
                m.put("a", 1)
                m.put("b", 2)
                println(m.containsValue(2))
                println(m.containsValue(3))
                val e: Map<String, Int> = mapOf()
                println(e.containsValue(1))
                println(m.putIfAbsent("a", 9))
                println(m.getOrDefault("a", 0))
                println(m.putIfAbsent("c", 3) == null)
                println(m.containsValue(3))
                val s: Map<String, String> = mapOf()
                println(s.putIfAbsent("k", "v1") == null)
                println(s.putIfAbsent("k", "v2"))
                val l: List<Int> = listOf(3, 1, 2, 1)
                println(l.indexOf(1))
                println(l.indexOf(99))
                println(l.lastIndexOf(1))
                println(l.lastIndexOf(99))
                val sub = l.subList(1, 3)
                println(sub.size)
                val empty = l.subList(2, 2)
                println(empty.size)
                println(l.contains(3))
                val b: List<Int> = listOf()
                println(b.addAll(l))
                println(b.size)
                println(b.addAll(listOf<Int>()))
                val rev: List<Int> = listOf(5, 4, 3, 2, 1)
                rev.sort()
                println(rev.get(0))
                println(rev.get(4))
                val ok: List<Int> = listOf(1, 2, 3)
                ok.sort()
                println(ok.get(2))
                val str: List<String> = listOf("pear", "apple", "fig")
                str.sort()
                println(str.get(0))
                val dbl: List<Double> = listOf(2.5, -1.5, 0.0)
                dbl.sort()
                println(dbl.get(0) == -1.5)
                println(l.subList(0, 1).get(0))
            }
            """;

    // Golden MEDIDO no oráculo JVM (java.util), 19/09 — ver body do programa.
    static final String GOLDEN =
            "true\nfalse\nfalse\n1\n1\ntrue\ntrue\ntrue\nv1\n1\n-1\n3\n-1\n"
                    + "2\n0\ntrue\ntrue\n4\nfalse\n1\n5\n3\napple\ntrue\n3";

    private final CompilerDriver driver = new CompilerDriver();

    private Path outDirFor(Path tempDir, String name, Target t) {
        return tempDir.resolve("out-" + name + "-" + t);
    }

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return driver.compile(source, outDirFor(tempDir, name, t), t);
    }

    @Test
    void sevenMethodsRunOnJvm(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "V", PROGRAM, Target.JVM);
        assertTrue(r.success(), "#386/#382 verbatim must compile: " + r.diagnostics().getDiagnostics());
        String javaCmd = TestJdk.javaBin();
        Process p = new ProcessBuilder(javaCmd, "-cp",
                outDirFor(tempDir, "V", Target.JVM).toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM run must exit 0, got:\n" + out);
        assertEquals(GOLDEN, out, "JVM oracle golden (medido)");
    }

    @Test
    void sevenMethodsRunOnJs(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("J.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outJ");
        CompilationResult r = driver.compile(src, outDir, Target.JS);
        assertTrue(r.success(), "js compile: " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String txt = out.toString().replace("\r\n", "\n").trim();
        assertEquals(0, ec, "js run exit, output:\n" + txt);
        assertEquals(GOLDEN, txt, "js output must match the JVM oracle (parity rule 5)");
    }

    // Fatia 2 (#386/#382): o mesmo golden roda no NATIVO x86_64 (asm real —
    // RuntimeMapLookups/RuntimeListLookups). Riscv64/aarch64: NativeRiscv64/
    // Aarch64E2ETest (guardados por toolchain — sem qemu no host de medição,
    // skip honesto, nunca falso-verde).
    @Test
    void sevenMethodsRunOnNativeX86(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("N.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outN");
        CompilationResult r = driver.compile(src, outDir, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native run must exit 0, got:\n" + out);
        assertEquals(GOLDEN, out, "native x86_64 output must match the JVM oracle (rule 5)");
    }

    @Test
    void sortEmptyListOfCompilesAndRunsEverywhere(@TempDir Path tempDir) throws Exception {
        // listOf() sem pin → List<Unknown>; sort de lista VAZIA é no-op em
        // todo alvo (o gate SEM097 aceita Unknown exatamente por isso).
        CompilationResult r = compile(tempDir, "S", """
                main() {
                    val l = listOf()
                    l.sort()
                    println(l.size)
                }
                """, Target.JVM);
        assertTrue(r.success(), "empty sort must compile: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void subListOutOfBoundsDiesWithTheMeasuredJavaUtilMessage(@TempDir Path tempDir) throws Exception {
        // Q3 "measured, never guessed": ArrayList.subList(1,9) numa lista de
        // 4 lança IndexOutOfBoundsException("toIndex = 9") — o processo morre
        // exit=1 (Kof não captura exceção Java com catch(String)).
        Path src = tempDir.resolve("O.kf");
        Files.writeString(src, """
                main() {
                    val l: List<Int> = listOf(3, 1, 2, 1)
                    val big = l.subList(1, 9)
                    println(big.size)
                }
                """);
        Path outDir = tempDir.resolve("outO");
        CompilationResult r = driver.compile(src, outDir, Target.JVM);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        String javaCmd = TestJdk.javaBin();
        Process p = new ProcessBuilder(javaCmd, "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int ec = p.waitFor();
        assertNotEquals(0, ec, "out-of-range subList must die, got:\n" + out);
        assertTrue(out.contains("IndexOutOfBoundsException"), "measured java.util exception, got:\n" + out);
        assertTrue(out.contains("toIndex = 9"), "measured message, got:\n" + out);
    }

    @Test
    void sortOnNonNaturalElementIsRejectedEveryTarget(@TempDir Path tempDir) throws Exception {
        // SEM097 — gate compartilhado (record não tem ordem natural; Kof não
        // tem Comparator). SCRIPT não passa por driver.compile (COMP003 — o
        // interpretador vive em kof-script, coberto em KofScriptStdlibParity
        // Test com o mesmo frontend); JVM/JS/Native: um gate, três faces.
        for (Target t : new Target[]{Target.JVM, Target.JS, Target.NATIVE}) {
            CompilationResult r = compile(tempDir, "R" + t, """
                    record Point(Int x, Int y)
                    main() {
                        val l = listOf(Point(1, 2), Point(3, 4))
                        l.sort()
                        println(l.get(0).x())
                    }
                    """, t);
            assertFalse(r.success(), "record sort must be rejected on " + t);
            assertTrue(r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> d.code().equals("SEM097")),
                    "SEM097 expected on " + t + ": " + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void floatSortOnNativeIsHonestDiagnostic(@TempDir Path tempDir) throws Exception {
        // NAT001 — o par (sort, Float, nativo) não tem compare de precisão
        // simples tradutível no runtime cross; diagnóstico honesto no
        // compile (R6), NUNCA ordem silenciosa errada. Funciona nos outros
        // alvos (medido no golden JVM acima com Double).
        CompilationResult rn = compile(tempDir, "F", """
                main() {
                    val l: List<Float> = listOf(2.5f, -1.5f)
                    l.sort()
                    println(l.get(0))
                }
                """, Target.NATIVE);
        assertFalse(rn.success(), "Float sort on native must be rejected");
        assertTrue(rn.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("NAT001")),
                "NAT001 expected: " + rn.diagnostics().getDiagnostics());
        CompilationResult rj = compile(tempDir, "FJ", """
                main() {
                    val l: List<Float> = listOf(2.5f, -1.5f)
                    l.sort()
                    println(l.get(0))
                }
                """, Target.JVM);
        assertTrue(rj.success(), "Float sort stays valid on JVM: " + rj.diagnostics().getDiagnostics());
    }

    @Test
    void wrongAritiesAreRejectedWithSem025(@TempDir Path tempDir) throws Exception {
        String[] bad = {
                "l.indexOf()",
                "l.subList(1)",
                "l.addAll()",
                "m.containsValue()",
                "m.putIfAbsent(\"a\")",
        };
        for (String expr : bad) {
            CompilationResult r = compile(tempDir, "A" + expr.hashCode(), """
                    main() {
                        val l: List<Int> = listOf(1, 2)
                        val m: Map<String, Int> = mapOf()
                        m.put("a", 1)
                        val x = %s
                        println(x)
                    }
                    """.formatted(expr), Target.JVM);
            assertFalse(r.success(), expr + " must not compile");
            assertTrue(r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> d.code().equals("SEM025")),
                    expr + " expected SEM025: " + r.diagnostics().getDiagnostics());
        }
        // sort(1) em STATEMENT (em `val x = l.sort(1)` o SEM033 de atribuição
        // void dispara antes — mesma rejeição, face diferente do gate).
        CompilationResult rs = compile(tempDir, "AS", """
                main() {
                    val l: List<Int> = listOf(1, 2)
                    l.sort(1)
                }
                """, Target.JVM);
        assertFalse(rs.success(), "l.sort(1) must not compile");
        assertTrue(rs.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM025")),
                "l.sort(1) expected SEM025: " + rs.diagnostics().getDiagnostics());
    }

    @Test
    void putIfAbsentPollutingValueIsRejected(@TempDir Path tempDir) throws Exception {
        // §126 wall do put agora cobre putIfAbsent (escreve o slot de valor).
        CompilationResult r = compile(tempDir, "P", """
                main() {
                    val m: Map<String, Int> = mapOf()
                    m.put("a", 1)
                    println(m.putIfAbsent("b", 5L) == null)
                }
                """, Target.JVM);
        assertFalse(r.success(), "polluting value must not compile");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM056")),
                "SEM056 expected: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void containsValueOnObjectMapWorksOnJvmAndIsHonestOnNative(@TempDir Path tempDir) throws Exception {
        // NAT002 (§349): no Map<_,Object> nativo o slot de Double cru é
        // indistinguível de caixa MAGIC sem dereferência (medição JVM: put
        // Int E Double no mesmo mapa é LEGÍTIMO — SEM056 não pinna tipo
        // declarado) — rejeição honesta no native, nunca SIGSEGV. No JVM os
        // dois lados do probe são objetos reais: hit e miss medidos.
        String src = """
                main() {
                    val o: Map<String, Object> = mapOf()
                    o.put("k1", 7)
                    println(o.containsValue(7))
                    println(o.containsValue("s"))
                }
                """;
        CompilationResult rj = compile(tempDir, "ONJ", src, Target.JVM);
        assertTrue(rj.success(), "Object containsValue works on JVM: " + rj.diagnostics().getDiagnostics());
        String javaCmd = TestJdk.javaBin();
        Process p = new ProcessBuilder(javaCmd, "-cp",
                outDirFor(tempDir, "ONJ", Target.JVM).toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run exit 0, got:\n" + out);
        assertEquals("true\nfalse", out, "MEDIÇÃO JVM 19/09 (Probe2): hit 7, miss \"s\"");
        CompilationResult rn = compile(tempDir, "ONN", src, Target.NATIVE);
        assertFalse(rn.success(), "Object containsValue must be rejected on native");
        assertTrue(rn.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("NAT002")),
                "NAT002 expected: " + rn.diagnostics().getDiagnostics());
    }

    @Test
    void existingCollectionSurfaceStillCompiles(@TempDir Path tempDir) throws Exception {
        // NEGATIVO (regra 1): o que já era válido continua válido.
        CompilationResult r = compile(tempDir, "K", """
                main() {
                    val m: Map<String, Int> = mapOf()
                    m.put("a", 1)
                    println(m.getOrDefault("a", 0))
                    println(m.containsKey("a"))
                    println(m.keys().size)
                    val l: List<Int> = listOf(1, 2, 3)
                    println(l.size)
                    println(l.remove(0))
                    l.clear()
                    println(l.isEmpty())
                    val d: Map<String, String> = mapOf("x", "y")
                    println(d.get("x"))
                }
                """, Target.JVM);
        assertTrue(r.success(), "regression: " + r.diagnostics().getDiagnostics());
    }
}
