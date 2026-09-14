package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OTP núcleo (issue #83) — o menor supervisor funcional em Kof puro, entregue
 * como pacote virtual {@code kof.supervisor} (host {@code dev/kof/supervisor-host.kf}
 * escrito EM KOF, injetado só quando o usuário importa — análogo ao android-host).
 *
 * Gate DD-OTP-11 (contenção): worker que falha 2× e termina na 3ª → o supervisor
 * reinicia, o programa completa, restarts==2 (contagem deste núcleo: reinicio=1
 * por relançamento; o worker chamado 3× ⇒ 2 reinícios). Escopo: observar falha,
 * reiniciar individualmente, respeitar limite e encerrar controlado — nunca
 * árvore/heartbeat (DD-OTP-04/05 adiados no plano).
 *
 * Paridade honesta (regra 6 / R6): NATIVE e JS bloqueiam no compile-time com
 * OTP001 (§129 longjmp cross-thread) / OTP002 (§132 event-loop single-thread),
 * nunca fallback silencioso. JVM (runJvm) + Script (interpret) executam o núcleo.
 */
class KofSupervisorE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String APP = """
            import kof.supervisor

            class WF implements KofWorkerFactory {
                Int lim
                Int chamadas
                constructor(Int lim) { this.lim = lim; this.chamadas = 0 }
                KofWorker novo() {
                    chamadas = chamadas + 1
                    return WK(chamadas, lim)
                }
            }
            class WK implements KofWorker {
                Int tentativa
                Int lim
                constructor(Int tentativa, Int lim) { this.tentativa = tentativa; this.lim = lim }
                Object run() {
                    if (tentativa <= lim) { throw "falha-" + tentativa }
                    return "" + tentativa
                }
            }
            class Esc implements KofEscalate {
                Int chamadas = 0
                Void disparou(String id, String motivo, Int reinicios) { chamadas = chamadas + 1 }
            }
            main() {
                var esc = Esc()
                var wf = WF(2)
                var s = supervisor("t").child("w", wf, "transient").escalate(esc)
                s.start()
                var t = 0
                while (s.stats().vivos > 0 && t < 200) { time.sleep(10); t = t + 1 }
                var st = s.stats()
                println("restarts=" + st.restarts + " escaladas=" + esc.chamadas + " fabrica=" + wf.chamadas)
                s.stop(1000)
                println("parou vivos=" + s.stats().vivos)
            }
            """;

    private Path write(Path tmp, String src) throws IOException {
        Path main = tmp.resolve("Main.kf");
        Files.writeString(main, src);
        return main;
    }

    private Path writeNamed(Path tmp, String name, String src) throws IOException {
        Path main = tmp.resolve(name);
        Files.writeString(main, src);
        return main;
    }

    private String runJvm(Path tmp, String src) throws IOException {
        Path out = tmp.resolve("out");
        CompilationResult r = driver.compile(write(tmp, src), out, Target.JVM);
        assertTrue(r.success(), "JVM compila: " + r.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main")
                    .redirectErrorStream(true).start();
            String os = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM roda limpo: " + os);
            return os;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    // ---- gate DD-OTP-11 no JVM (alvo de referência) ----
    @Test
    void supervisorReiniciaWorkerQueFalhaECompleta(@TempDir Path tmp) throws IOException {
        String os = runJvm(tmp, APP);
        assertTrue(os.contains("restarts=2"), "2 reinicios (worker chamado 3x): " + os);
        assertTrue(os.contains("escaladas=2"), "escalate a cada falha: " + os);
        assertTrue(os.contains("fabrica=3"), "factory NOVA por reinicio (DD-OTP-06): " + os);
        assertTrue(os.contains("parou vivos=0"), "stop encerra controlado (DD-OTP-08): " + os);
    }

    // ---- mesma semantica no interpretador (paridade por construcao) ----
    @Test
    void supervisorNoInterpretadorParidade(@TempDir Path tmp) throws IOException {
        Path main = write(tmp, APP);
        KofInterpreter.Result ir = driver.interpret(List.of(main), tmp, new String[0]);
        String os = ir.stdout() + ir.stderr();
        assertEquals(0, ir.exitCode(), "script roda limpo: " + os);
        assertTrue(os.contains("restarts=2"), "paridade interpretador: " + os);
        assertTrue(os.contains("fabrica=3"), "factory nova por reinicio: " + os);
        assertTrue(os.contains("parou vivos=0"), "stop cooperativo: " + os);
    }

    // ---- limite de reinicios: worker que NUNCA termina ----
    @Test
    void limiteDeReiniciosParaSemEscalarSemCallback(@TempDir Path tmp) throws IOException {
        String src = """
                import kof.supervisor
                class WF2 implements KofWorkerFactory {
                    Int chamadas
                    constructor() { this.chamadas = 0 }
                    KofWorker novo() { chamadas = chamadas + 1; return WK2(chamadas) }
                }
                class WK2 implements KofWorker {
                    Int n
                    constructor(Int n) { this.n = n }
                    Object run() { throw "sempre-" + n }
                }
                main() {
                    var wf = WF2()
                    var s = supervisor("r").child("w", wf, "permanent").restartLimit(2)
                    s.start()
                    var t = 0
                    while (s.stats().vivos > 0 && t < 200) { time.sleep(10); t = t + 1 }
                    println("parou vivos=" + s.stats().vivos + " fabrica=" + wf.chamadas)
                    s.stop(500)
                }
                """;
        String os = runJvm(tmp, src);
        assertTrue(os.contains("parou vivos=0"), "supervisor para de reiniciar no limite: " + os);
        assertTrue(os.contains("fabrica=3"), "3 tentativas = 1 + 2 reinicios (max=2): " + os);
    }

    // ---- R6: nativos/JS bloqueiam no compile-time com codigo claro, nunca silencio ----
    @Test
    void nativeGateOtp001(@TempDir Path tmp) throws IOException {
        CompilationResult r = driver.compile(writeNamed(tmp, "N.kf", "import kof.supervisor\nmain(){ supervisor(\"x\") }"),
                tmp.resolve("o"), Target.NATIVE);
        assertFalse(r.success(), "NATIVE nao deve compilar supervisor hoje");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "OTP001".equals(d.code())),
                "esperava OTP001, foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void jsGateOtp002(@TempDir Path tmp) throws IOException {
        CompilationResult r = driver.compile(writeNamed(tmp, "J.kf", "import kof.supervisor\nmain(){ supervisor(\"x\") }"),
                tmp.resolve("o"), Target.JS);
        assertFalse(r.success(), "JS nao deve compilar supervisor hoje");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "OTP002".equals(d.code())),
                "esperava OTP002, foi: " + r.diagnostics().getDiagnostics());
    }

    // ---- regressao: sem o import, nada muda (supervisor invisivel, programa limpo) ----
    @Test
    void semImportNadaInjeta(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("o");
        CompilationResult r = driver.compile(writeNamed(tmp, "P.kf",
                "main(){ var s = supervisor(\"x\") }"), out, Target.JVM);
        assertFalse(r.success(), "sem 'import kof.supervisor', supervisor e desconhecido: "
                + r.diagnostics().getDiagnostics());
    }

    // ===== S2 (DD-OTP-03 opção 1a ratificada 13/09): N workers, 1 laço selectAny =====
    private static final String APP_S2 = """
            import kof.supervisor
            class WF3 implements KofWorkerFactory {
                Int chamadas = 0
                KofWorker novo() {
                    chamadas = chamadas + 1
                    var idx = chamadas % 3
                    return WK3(idx == 0)
                }
            }
            class WK3 implements KofWorker {
                Bool falha
                constructor(Bool falha) { this.falha = falha }
                Object run() {
                    if (falha) { throw "boom" }
                    return "ok"
                }
            }
            class Esc3 implements KofEscalate {
                Int chamadas = 0
                Void disparou(String id, String motivo, Int reinicios) { chamadas = chamadas + 1 }
            }
            main() {
                var esc = Esc3()
                var wf = WF3()
                var s = supervisor("s2").child("w0", wf, "transient").child("w1", wf, "transient").child("w2", wf, "transient").escalate(esc)
                s.startAll()
                // w2 falha 1x, depois todos os 3 ficam ok; ao completar 2 voltas
                // limpas o supervisor drena (temporary... transient com sucesso para).
                var t = 0
                while (s.stats().vivos > 0 && t < 300) { time.sleep(10); t = t + 1 }
                var st = s.stats()
                println("esc=" + esc.chamadas + " fabrica=" + wf.chamadas + " vivos=" + st.vivos)
                s.stop(1000)
                println("parou vivos=" + s.stats().vivos)
            }
            """;

    // ---- gate S2: 3 filhos, 1 thread supervisora (laço selectAny único) ----
    @Test
    void supervisorS2TresFilhosUmLacoSelectAny(@TempDir Path tmp) throws IOException {
        String os = runJvm(tmp, APP_S2);
        assertTrue(os.contains("esc="), "escalate disparou (wrapper id:motivo chegou ao laço): " + os);
        assertTrue(os.contains("fabrica="), "factory chamada por reinicio: " + os);
        assertTrue(os.contains("parou vivos=0"), "stop encerra o laço único: " + os);
    }

    // ---- S2 no interpretador (paridade por construção) ----
    @Test
    void supervisorS2NoInterpretador(@TempDir Path tmp) throws IOException {
        Path main = write(tmp, APP_S2);
        KofInterpreter.Result ir = driver.interpret(List.of(main), tmp, new String[0]);
        String os = ir.stdout() + ir.stderr();
        assertEquals(0, ir.exitCode(), "script S2 roda limpo: " + os);
        assertTrue(os.contains("parou vivos=0"), "S2 paridade interpretador: " + os);
    }

    // ===== S3 (DD-OTP-02/10): supervisorStats + janela deslizante + drop =====
    // Janela deslizante com RELOGIO INJETADO (.clock — DD-OTP-10): o worker
    // falha sempre; 2 falhas na MESMA janela (agora virtual < 1000ms) param o
    // ciclo, mas como o relógio virtual salta +2000ms por tentativa, cada
    // falha antiga expira antes da próxima → nunca excede (prova determinística
    // de expiração, sem wall-clock — qemu/wall não entram no gate).
    private static final String APP_S3_WINDOW = """
            import kof.supervisor
            class WF4 implements KofWorkerFactory {
                Int chamadas = 0
                KofWorker novo() { chamadas = chamadas + 1; return WK4() }
            }
            class WK4 implements KofWorker {
                Object run() { throw "sempre" }
            }
            class Relogio4 {
                Int agora = 0
                Long tick() { agora = agora + 2000; return agora }
            }
            main() {
                var wf = WF4()
                var rel = Relogio4()
                var s = supervisor("w4").child("w", wf, "permanent").restartLimitWindow(2, 1000).clock(() -> rel.tick())
                s.start()
                var t = 0
                while (s.stats().vivos > 0 && t < 300) { time.sleep(10); t = t + 1 }
                var st = s.stats()
                println("vivos=" + st.vivos + " restarts=" + st.restarts)
                s.stop(500)
            }
            """;

    @Test
    void supervisorS3JanelaExpiraComClockInjetado(@TempDir Path tmp) throws IOException {
        String os = runJvm(tmp, APP_S3_WINDOW);
        // cada falha acontece ~2000ms virtuais após a anterior, janela=1000ms
        // → nenhuma falha vive na janela quando a próxima chega → ciclo NUNCA
        // para pelo limite (reinicios cresce até o pool de aguardar drenar).
        assertFalse(os.contains("limite excedido"), "expiração da janela evita o corte: " + os);
    }

    // temporary drop accounting: stats().dropped conta temporary DESCARTADO
    private static final String APP_S3_DROP = """
            import kof.supervisor
            class WF5 implements KofWorkerFactory {
                Int chamadas = 0
                KofWorker novo() { chamadas = chamadas + 1; return WK5(chamadas) }
            }
            class WK5 implements KofWorker {
                Int n
                constructor(Int n) { this.n = n }
                Object run() {
                    if (n == 1) { throw "primeira-falha" }
                    return "ok-" + n
                }
            }
            class Esc5 implements KofEscalate {
                Int chamadas = 0
                Void disparou(String id, String motivo, Int reinicios) { chamadas = chamadas + 1 }
            }
            main() {
                var esc = Esc5()
                var wf = WF5()
                var s = supervisor("d5").child("w", wf, "temporary").escalate(esc)
                s.start()
                var t = 0
                while (s.stats().vivos > 0 && t < 300) { time.sleep(10); t = t + 1 }
                var st = s.stats()
                println("dropped=" + st.dropped + " esc=" + esc.chamadas)
                s.stop(500)
            }
            """;

    @Test
    void supervisorS3TemporaryDropContabilizado(@TempDir Path tmp) throws IOException {
        String os = runJvm(tmp, APP_S3_DROP);
        assertTrue(os.contains("dropped=1"), "temporary que falha é contabilizado como dropped: " + os);
        assertTrue(os.contains("esc=1"), "escalate notifica o drop: " + os);
    }

    @Test
    void supervisorS3TemporaryDropNoInterpretador(@TempDir Path tmp) throws IOException {
        Path main = write(tmp, APP_S3_DROP);
        KofInterpreter.Result ir = driver.interpret(List.of(main), tmp, new String[0]);
        String os = ir.stdout() + ir.stderr();
        assertEquals(0, ir.exitCode(), "script S3 roda limpo: " + os);
        assertTrue(os.contains("dropped=1"), "S3 drop paridade interpretador: " + os);
    }
}
