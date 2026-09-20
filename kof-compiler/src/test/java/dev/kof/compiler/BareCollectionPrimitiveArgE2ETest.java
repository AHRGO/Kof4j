package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §374 (#553) — argumento PRIMITIVO em `add`/`set` de coleção builtin BARE
 * (sem type-args: `List xs = listOf(1)`, campo `List xs`) nunca era boxeado
 * no JVM: `emitBoxIfPrimitive` seguia o elemType DECLARADO, que e `Unknown`
 * num receiver sem type-args — o `int` cru chegava a `ArrayList.add(Object)`
 * e o Load morria em `VerifyError: Type integer ... not assignable to
 * 'java/lang/Object'` (o lancador da CLI mascara como mensagem JavaFX, §149).
 *
 * <p>Fix = o MESMO fallback do bug 35 (`kof_list_contains`/`indexOf`): boxear
 * pelo tipo do ARGUMENTO no call-site quando o declarado nao ajuda; com
 * elemType tipado o resultado e identico (controles abaixo). Set/Map ja
 * caiam no fallback via parameterTypes; a face viva era `kof_list_add`/
 * `kof_list_set`.
 *
 * <p>Paridade (regra 5): mesmo programa, mesma saida byte-a-byte em JVM,
 * Script e JS (host); Native x86-64 compila e roda quando o toolchain existe
 * no host (guarda ambiental honesta).
 */
class BareCollectionPrimitiveArgE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(boolean ok, String output) {}

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Run(true, buf.toString());
        } catch (ReflectiveOperationException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            return new Run(false, "THROW: " + c);
        } finally {
            System.setOut(oldOut);
        }
    }

    private Run runJs(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JS);
        if (!r.success()) return new Run(false, diags(r));
        var buf = new ByteArrayOutputStream();
        try {
            int rc = dev.kof.runtime.KofJsRunner.run(
                    out.resolve("Default.mjs"), buf,
                    new ByteArrayInputStream(new byte[0]), buf);
            return new Run(rc == 0, buf.toString());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage() + "\n" + buf);
        }
    }

    private Run runScript(Path src, Path root) {
        try {
            var r = driver.interpret(java.util.List.of(src), root, new String[0]);
            return new Run(r.exitCode() == 0, r.stdout());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage());
        }
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    /** Byte-for-byte JVM == Script == JS-host for a source (rule 5). */
    private void assertAllTargets(@TempDir Path tmp, String base, String source, String expected)
            throws Exception {
        Path src = tmp.resolve(base + ".kf");
        Files.writeString(src, source);
        Run jvm = runJvm(src, tmp.resolve("o-" + base + "-jvm"));
        assertTrue(jvm.ok(), () -> "JVM: " + jvm.output());
        assertEquals(expected, jvm.output().replace("\r\n", "\n").trim(), "JVM saida");
        Run scr = runScript(src, tmp);
        assertTrue(scr.ok(), () -> "Script: " + scr.output());
        assertEquals(jvm.output().replace("\r\n", "\n").trim(),
                scr.output().replace("\r\n", "\n").trim(), "JVM x Script byte-a-byte");
        Run js = runJs(src, tmp.resolve("o-" + base + "-js"));
        assertTrue(js.ok(), () -> "JS: " + js.output());
        assertEquals(jvm.output().replace("\r\n", "\n").trim(),
                js.output().replace("\r\n", "\n").trim(), "JVM x JS byte-a-byte");
    }

    /** O reprodutor verbatim do #553: add de primitivo numa List BARE LOCAL. */
    @Test
    void bareListAddPrimitiveRunsEverywhere(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B374Add", """
            main() {
              List xs = listOf(1)
              xs.add(2)
              println(xs.size)
              println(xs.get(1))
            }
            """, "2\n2");
    }

    /** Set BARE + valor primitivo (face do field, viva apos o pin do §373). */
    @Test
    void bareListFieldAddPrimitiveRuns(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B374Field", """
            class Box {
              List xs = listOf()
            }

            main() {
              var b = Box()
              b.xs.add(7)
              b.xs.add(8)
              println(b.xs.size)
              println(b.xs.get(0))
            }
            """, "2\n7");
    }

    /** `set(i, primitivo)` em BARE local: o VALOR e o arg posicao-1. */
    @Test
    void bareListSetPrimitiveValueRuns(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B374Set", """
            main() {
              List xs = listOf(1)
              xs.set(0, 5)
              println(xs.get(0))
            }
            """, "5");
    }

    /** Long/Double (categorias de pilha largas) — nao so `int`. Uma lista por
     *  tipo: o runtime e homogeneo e recusa Double em lista Long com
     *  diagnostico R6 correto (comportamento esperado, nao o bug §374). */
    @Test
    void bareListWidePrimitivesRun(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B374Wide", """
            main() {
              List longs = listOf()
              longs.add(9L)
              List doubles = listOf()
              doubles.add(2.5)
              println(longs.get(0))
              println(doubles.get(0))
            }
            """, "9\n2.5");
    }

    /** Controles: Set/Map bare ja caiam no fallback de parameterTypes. */
    @Test
    void bareSetAndMapPrimitiveArgsStayGreen(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B374SetMap", """
            main() {
              Set s = setOf()
              s.add(3)
              println(s.contains(3))
              Map m = mapOf()
              m.put(1, "v")
              println(m.get(1))
            }
            """, "true\nv");
    }

    /** Controle de nao-regressao: coleção TIPIADA nao muda de emissao. */
    @Test
    void typedListPrimitiveOpsUnchanged(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B374Typed", """
            main() {
              var xs = listOf(1)
              xs.add(2)
              xs.set(0, 3)
              println(xs.get(0))
              println(xs.size)
            }
            """, "3\n2");
    }

    /** Native x86-64: compila e roda quando o toolchain existe (guarda honesta). */
    @Test
    void bareListAddPrimitiveNativeRuns(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("B374Nat.kf");
        Files.writeString(src, """
            main() {
              List xs = listOf(1)
              xs.add(2)
              println(xs.size)
              println(xs.get(1))
            }
            """);
        Path out = tmp.resolve("o-nat");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        org.junit.jupiter.api.Assumptions.assumeTrue(r.success(),
                "Native toolchain ausente no host (COMP001): " + diags(r));
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String s = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "Native exit, saida: " + s);
        assertEquals("2\n2", s, "Native == JVM (regra 5)");
    }

    // ── faces medidas e travadas pela lane #553 (verificacao 20/09): os
    //     slots Object que a lei do §374 alcanca e que ainda nao tinham
    //     golden ──────────────────────────────────────────────────────

    /** `put(k, v)` com AMBOS os args primitivos na Map BARE (chave passa
     *  pelo SWAP-box do put; valor pelo fallback do bug 35/§374). */
    @Test
    void bareMapPutBothPrimitivesRunsEverywhere(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B553Put", """
            main() {
              Map m = mapOf()
              m.put(1, 2)
              m.put(5, 7)
              println(m.get(1))
              println(m.size)
            }
            """, "2\n2");
    }

    /** `remove(primitivo)` + `contains` numa Set BARE (mesma lei do add). */
    @Test
    void bareSetRemoveAndContainsPrimitivesRun(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B553Set", """
            main() {
              Set s = setOf()
              s.add(3)
              s.add(4)
              s.remove(3)
              println(s.size)
              println(s.contains(4))
            }
            """, "1\ntrue");
    }

    /** Bool no primeiro add de lista BARE — wrapper por categoria
     *  (Boolean.valueOf), espelhando o dispatch do helper de box. */
    @Test
    void bareListBoolFirstAddRuns(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B553Bool", """
            main() {
              List b = listOf()
              b.add(true)
              b.add(false)
              println(b.get(0))
              println(b.get(1))
            }
            """, "true\nfalse");
    }

    /** Canal BARE — a face que o pouso do §374 reclamou mas NAO alcancava:
     *  o lowerer punha o elemT do canal (Unknown) no parameterTypes, nunca
     *  o tipo do ARG — o fallback lia Unknown de novo e o int cru morria no
     *  LOAD do put(Object) do LinkedBlockingQueue (VerifyError atras da
     *  mascara JavaFX, §149). */
    @Test
    void bareChannelSendPrimitiveReceivesOnJvmScriptJs(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B553Chan", """
            main() {
              val c = channel()
              c.send(1)
              println(c.receive())
            }
            """, "1");
    }

    /** NAT003 (padrao honesto §352/NAT001-NAT002): a fila nativa e de
     *  OBJETOS — primitivo cru no receive virava ponteiro (SIGSEGV 139,
     *  medido antes do fix). A recusa em compile-time nomeia o idiom
     *  tipado; o canal TIPADO no native nao e tocado (controle abaixo —
     *  emissao byte-identica, freeze regra 1). */
    @Test
    void bareChannelPrimitiveNativeRefusesAndTypedStaysGreen(@TempDir Path tmp) throws Exception {
        Path bare = tmp.resolve("B553ChanBare.kf");
        Files.writeString(bare, """
            main() {
              val c = channel()
              c.send(1)
              println(c.receive())
            }
            """);
        CompilationResult rn = driver.compile(bare, tmp.resolve("o-chnat"), Target.NATIVE);
        assertFalse(rn.success(), "primitive send on bare Channel must be refused (NAT003)");
        assertTrue(rn.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("NAT003")),
                "NAT003 expected: " + rn.diagnostics().getDiagnostics());
        Path typed = tmp.resolve("B553ChanTyped.kf");
        Files.writeString(typed, """
            main() {
              val c = channel<Int>()
              c.send(5)
              c.send(6)
              println(c.receive() + c.receive())
            }
            """);
        CompilationResult rt = driver.compile(typed, tmp.resolve("o-chnat2"), Target.NATIVE);
        org.junit.jupiter.api.Assumptions.assumeTrue(rt.success(),
                "Native toolchain ausente no host (COMP001): " + diags(rt));
        Process p = new ProcessBuilder(tmp.resolve("o-chnat2/Default/Main").toString())
                .redirectErrorStream(true).start();
        String s = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "typed native exit, saida: " + s);
        assertEquals("11", s, "typed Channel<Int> native unchanged (regra 1)");
    }
}
