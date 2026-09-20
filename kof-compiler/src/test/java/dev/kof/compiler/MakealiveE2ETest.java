package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tracker 3.1 (D-MAKEALIVE 20/09; plano §5): golden do pacote virtual
 * {@code kof.makealive} — plan/apply/destroy com provider de function-values
 * e estado como dado. Idempotência é o golden de aceitação (contrato §3 do
 * plano: apply de design já convergido = no-op), não slogan; as guardas
 * (§3: ciclo nomeando membros, duplicado, estado intacto na falha — nunca
 * partial silencioso) também correm. JVM==JS byte-parity; Native compila
 * (host é composição pura — crosses de runtime só no corpo do provider).
 */
class MakealiveE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

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
        } catch (java.lang.reflect.InvocationTargetException e) {
            return new Run(false, "THROW: " + e.getCause());
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

    private CompilationResult compileNative(Path src, Path out) throws Exception {
        return driver.compile(src, out, Target.NATIVE);
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    private void assertParityJvmJsNative(String base, String source, String... expected)
            throws Exception {
        Path src = tmp.resolve(base + ".kf");
        Files.writeString(src, source);
        Run jvm = runJvm(src, tmp.resolve("o-" + base + "-jvm"));
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        Run js = runJs(src, tmp.resolve("o-" + base + "-js"));
        assertTrue(js.ok(), () -> "JS failed: " + js.output());
        for (String e : expected) {
            assertTrue(jvm.output().contains(e), () -> "JVM expected '" + e + "' in: " + jvm.output());
        }
        assertEquals(jvm.output(), js.output(), "3.1 golden: JVM/JS diverge");
        var native_ = compileNative(src, tmp.resolve("o-" + base + "-native"));
        assertTrue(native_.success(), () -> "Native must compile: " + diags(native_));
    }

    /** Ciclo de vida completo do golden: plan ordenado (topo→declaração),
     *  apply cria, SEGUNDO apply é no-op (idempotência por construção — o
     *  plano §3 exige), prop nova vira update, resource fora do desenho vira
     *  delete, destroy derruba em topológica inversa e deixa estado vazio. */
    @Test
    void lifecycleIsByteIdenticalAndIdempotent() throws Exception {
        assertParityJvmJsNative("MkLifecycle", """
            import kof.makealive

            class World {
                List<String> events = listOf()
            }

            Bool mkLog(World w, String e) {
                w.events.add(e)
                return true
            }

            main() {
                var w = World()
                var p = Provider(
                    (r: Resource) -> mapOf(),
                    (r: Resource, props: Map<String, String>) -> mkLog(w, "set:" + r.name()),
                    (r: Resource) -> mkLog(w, "del:" + r.name())
                )
                var d = Infrastructure("cdn")
                d.resource("service", "build")
                d.resource("service", "deploy")
                d.requires("deploy", "build")
                d.prop("deploy", "size", "3")
                var st = State()
                var pl = plan(d, st)
                println("plan:" + pl.creates.size() + "/" + pl.updates.size() + "/" + pl.deletes.size())
                var rep = apply(d, st, p)
                println("applied:" + rep.created.size() + "/" + rep.updated.size() + "/" + rep.deleted.size())
                var i = 0
                while (i < w.events.size()) {
                    println(w.events.get(i))
                    i = i + 1
                }
                var pl2 = plan(d, rep.state)
                println("again:" + pl2.creates.size() + "/" + pl2.updates.size() + "/" + pl2.deletes.size())
                var rep2 = apply(d, rep.state, p)
                println("events-after:" + w.events.size())
                println("state2:" + rep2.state.entries.size())
                d.prop("build", "cache", "on")
                var pl3 = plan(d, rep2.state)
                println("upd:" + pl3.updates.size())
                var rep3 = apply(d, rep2.state, p)
                println("last:" + w.events.get(w.events.size() - 1))
                var d2 = Infrastructure("cdn2")
                d2.resource("service", "build")
                var pl4 = plan(d2, rep3.state)
                println("del-plan:" + pl4.deletes.size())
                var rep4 = destroy(d, rep3.state, p)
                println("destroy:" + rep4.deleted.size())
                var j = w.events.size() - 2
                while (j < w.events.size()) {
                    println(w.events.get(j))
                    j = j + 1
                }
                println("end-state:" + rep4.state.entries.size())
            }
            """,
            "plan:2/0/0", "applied:2/0/0", "set:build", "set:deploy",
            "again:0/0/0", "events-after:2", "state2:2",
            "upd:1", "last:set:build",
            "del-plan:1", "destroy:2", "del:deploy", "del:build", "end-state:0");
    }

    /** Guardas do contrato §3, acionáveis e paridas: ciclo nomeia os
     *  membros; duplicado recusado no builder; dependência inexistente
     *  nomeia as duas pontas. */
    @Test
    void guardsThrowActionableNamedStrings() throws Exception {
        assertParityJvmJsNative("MkGuards", """
            import kof.makealive

            main() {
                var m1 = "ok"
                try {
                    var c = Infrastructure("c")
                    c.resource("s", "a")
                    c.resource("s", "b")
                    c.requires("a", "b")
                    c.requires("b", "a")
                    plan(c, State())
                    m1 = "no-throw"
                } catch (String e) {
                    m1 = e
                }
                println(m1)
                var m2 = "ok"
                try {
                    var d = Infrastructure("d")
                    d.resource("s", "x")
                    d.resource("s", "x")
                    m2 = "no-throw"
                } catch (String e) {
                    m2 = e
                }
                println(m2)
                var m3 = "ok"
                try {
                    var d = Infrastructure("d3")
                    d.resource("s", "a")
                    d.requires("a", "ghost")
                    plan(d, State())
                    m3 = "no-throw"
                } catch (String e) {
                    m3 = e
                }
                println(m3)
            }
            """,
            "makealive: ciclo de dependencia em 'c'",
            "resource duplicada no design 'd'",
            "depende de nome inexistente 'ghost'");
    }

    /** R6/§3: provider que FALHA (throw ou false) deixa o estado anterior
     *  intacto (nunca partial silencioso) e o erro NOMEIA o resource. */
    @Test
    void failedApplyKeepsStateAndNamesTheResource() throws Exception {
        assertParityJvmJsNative("MkFailure", """
            import kof.makealive

            class World {
                List<String> events = listOf()
            }

            Bool mkLog(World w, String e) {
                w.events.add(e)
                return true
            }

            Bool mkSet(World w, Resource r, Bool refuse) {
                // dois guards de nivel unico: a forma "if aninhado cujo then
                // termina em throw com epilogue depois" e o bug §377 do JS
                if (r.name() == "bad" && refuse) { return false }
                if (r.name() == "bad") { throw "boom do provider" }
                w.events.add("set:" + r.name())
                return true
            }

            main() {
                var w = World()
                var pThrow = Provider(
                    (r: Resource) -> mapOf(),
                    (r: Resource, props: Map<String, String>) -> mkSet(w, r, false),
                    (r: Resource) -> mkLog(w, "del:" + r.name())
                )
                var pFalse = Provider(
                    (r: Resource) -> mapOf(),
                    (r: Resource, props: Map<String, String>) -> mkSet(w, r, true),
                    (r: Resource) -> mkLog(w, "del:" + r.name())
                )
                var d = Infrastructure("f")
                d.resource("s", "ok1")
                d.resource("s", "bad")
                var st = State()
                var m1 = "ok"
                try {
                    apply(d, st, pThrow)
                    m1 = "no-throw"
                } catch (String e) {
                    m1 = e
                }
                println(m1)
                println("state-untouched:" + st.entries.size())
                var m2 = "ok"
                try {
                    apply(d, st, pFalse)
                    m2 = "no-throw"
                } catch (String e) {
                    m2 = e
                }
                println(m2)
                println("world-saw:" + w.events.size())
            }
            """,
            "makealive: apply falhou em 'bad': boom do provider",
            "state-untouched:0",
            "makealive: provider recusou set em 'bad'",
            "world-saw:2");
    }

    /** Regra 8 medida: usuário que declara o PRÓPRIO `Resource` com o
     *  import NÃO recebe o host por cima (collision-skip do injetor) e o
     *  programa dele compila e roda — o import `kof.*` tolerado pela
     *  whitelist de imports legítimos (CompilerImports) não vira ruído.
     *  Usar uma face do host sem o host injetado é que é o sinal
     *  (undefined), nunca silêncio. */
    @Test
    void userCollisionKeepsOwnNames() throws Exception {
        Path src = tmp.resolve("MkCollision.kf");
        Files.writeString(src, """
            import kof.makealive

            record Resource(String kind, String name)

            main() {
                var r = Resource("mine", "own")
                println(r.kind())
            }
            """);
        Run jvm = runJvm(src, tmp.resolve("o-collide-jvm"));
        assertTrue(jvm.ok(), "programa do usuario com Resource proprio nao pode quebrar: " + jvm.output());
        assertTrue(jvm.output().contains("mine"));
        Path src2 = tmp.resolve("MkCollisionFace.kf");
        Files.writeString(src2, """
            import kof.makealive

            record Resource(String kind, String name)

            main() {
                var d = Infrastructure("nope")
                println("unreachable")
            }
            """);
        Run jvm2 = runJvm(src2, tmp.resolve("o-collide-face"));
        assertFalse(jvm2.ok(), "face do host sem host injetado = undefined (sinal), nunca silencio");
        assertTrue(jvm2.output().contains("Infrastructure"), "undefined deve nomear Infrastructure: " + jvm2.output());
    }
}
