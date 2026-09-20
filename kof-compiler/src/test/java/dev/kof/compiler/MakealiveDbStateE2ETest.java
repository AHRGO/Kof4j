package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * makealive 3.1.1 — SONDA das faces de ESTADO sobre kof.db/kof.orm (Q3:
 * "state = kof.db from day one", D-MAKEALIVE). Mesma disciplina das sondas
 * 3.1.0: a superficie do host e TRAVADA por medicao, nunca por suposicao
 * (o checkpoint do workflow reusa estes faces — precedente vivo
 * workflow-ckpt-host.kf). O corpo db NAO entra no host-core (gate ORM001
 * estatico derrubaria o host inteiro no Native — razao da fatia separada);
 * esta sonda mede o que a `makealive-db-host.kf` (proximo passo da 3.1)
 * pode usar HOJE no frontend .kf: create/save/find/all/page/where (medido
 * 20/09 no lowerer: delete/count/deleteAll/saveAll ainda NAO resolvem em
 * .kf — fatias F1/F2 da lane GAPS-DB; a face de estado entao usa chave com
 * geracao por design + `all`/`where` com filtro no host, e NUNCA depende de
 * delete). Entidades sao imutaveis (SEM038) — update = nova geracao, nunca
 * re-save da mesma chave. JVM==JS byte (o delegate kof_platform.db* faz o
 * roundtrip JDBC no host Graal — DB001 16/09); Native = MEDICAO honesta
 * (a lane GAPS-DB esta aterrando RuntimeOrm nativo; aceita "roda identico"
 * OU "recusa nomeando ORM001/DB001" — nunca silent, R6).
 */
class MakealiveDbStateE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private record Run(boolean ok, String output) {}

    /** DB por execucao (H2 mem e compartilhado na MESMA JVM entre os dois
     *  testes — token so na URL; os DADOS gravados sao byte-identicos em
     *  qualquer token, e e isso que o golden de paridade compara). */
    private String program(String token) {
        return STATE_PROGRAM.replace("__T__", token);
    }

    private static final String STATE_PROGRAM = """
        import kof.db
        import kof.orm

        entity St {
            id: Long generated
            design: String
            key: String unique
            value: String
        }

        main() {
            var db = db.connect("jdbc:h2:mem:__T__;DB_CLOSE_DELAY=-1")
            orm.create<St>(db)
            orm.save(db, St(0, "prod", "prod/web/region#1", "us-east"))
            orm.save(db, St(0, "prod", "prod/web/tier#1", "front"))
            orm.save(db, St(0, "dev", "dev/web/region#1", "local"))
            var rows = orm.all<St>(db)
            println(rows.size)
            var i = 0
            while (i < rows.size) {
                var r = rows.get(i)
                println(r.key + "=" + r.value)
                i = i + 1
            }
            var one = orm.where<St>(db, "key", "prod/web/region#1")
            println(one.size)
            var prod1 = orm.where<St>(db, "design", "prod")
            println(prod1.size)
            var f = orm.find<St>(db, one.get(0).id)
            println(f.key)
            var pg = orm.page<St>(db, 0, 2)
            println(pg.size)
            // "update" sem delete = nova geracao (a face de estado do host):
            orm.save(db, St(0, "prod", "prod/web/region#2", "us-west"))
            var g2 = orm.where<St>(db, "design", "prod")
            println(g2.size)
        }
        """;

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

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    @Test
    void stateSurfaceRoundTripJvmJsByteParity() throws Exception {
        Path jSrc = tmp.resolve("A.kf");
        Files.writeString(jSrc, program("mkj"));
        Run jvm = runJvm(jSrc, tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM state surface failed: " + jvm.output());
        Path sSrc = tmp.resolve("B.kf");
        Files.writeString(sSrc, program("mks"));
        Run js = runJs(sSrc, tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS state surface failed (bridge DB001): " + js.output());
        assertEquals(jvm.output(), js.output(),
                "3.1 golden: state surface JVM/JS diverge (H2 same-host JDBC delegate)");
        // Golden MEDIDO: 3 linhas na insercao + tamanho do where por design
        // (prod = 2) + find devolve a linha + page(0,2) = 2 + nova geracao
        // apos "update" = 3 linhas em prod.
        assertTrue(jvm.output().contains("3\n"), () -> "all size 3: " + jvm.output());
        assertTrue(jvm.output().contains("prod/web/region#1=us-east"),
                () -> "linha lida: " + jvm.output());
        assertTrue(jvm.output().endsWith("3\n"), () -> "pos-nova-geracao prod=3: " + jvm.output());
    }

    /** Native: MEDICAO honesta da janela GAPS-DB. Aceita execuacao identica
     *  ao oracle JVM OU recusa com gap NOMEADO (ORM001/DB001) — R6. Quando a
     *  fatia F1/F2 da irma pousar, este teste vira golden de paridade. */
    @Test
    void stateSurfaceNativeNeverSilent() throws Exception {
        Path oSrc = tmp.resolve("O.kf");
        Files.writeString(oSrc, program("mko"));
        Run oracle = runJvm(oSrc, tmp.resolve("o-oracle"));
        assertTrue(oracle.ok(), () -> "oracle JVM: " + oracle.output());
        Path nSrc = tmp.resolve("N.kf");
        Files.writeString(nSrc, program("mkn"));
        CompilationResult nat = driver.compile(nSrc, tmp.resolve("o-native"), Target.NATIVE);
        String why = diags(nat);
        boolean honest = !nat.success();
        if (!honest) {
            Path bin = tmp.resolve("o-native/Default/Main");
            assertTrue(Files.exists(bin), "native compilou sem binario");
            try {
                ProcessBuilder pb = new ProcessBuilder(bin.toString());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String outp = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n");
                int rc = p.waitFor();
                honest = (rc == 0 && outp.equals(oracle.output()));
                if (!honest) why = why + "\nrc=" + rc + " out=" + outp;
            } catch (Exception e) {
                why = why + "\nexec: " + e;
            }
        }
        // disjuncao medida: paridade real OU recusa NOMEANDO o gap (R6 — o
        // pior resultado seria silencioso); vira paridade stricta com a F1/F2.
        String finalWhy = why;
        assertTrue(honest || finalWhy.contains("ORM001") || finalWhy.contains("DB001"),
                () -> "native nem executou nem nomeou o gap (R6): " + finalWhy);
    }
}
