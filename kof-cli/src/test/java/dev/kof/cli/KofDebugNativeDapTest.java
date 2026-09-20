package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * X7-4 (fase 7 do §19.5): `kof debug --dap --target native` — a ponte DAP<->gdb/MI
 * que o Kof Editor usa para debugar Native. O gdb real NAO existe neste host (medido),
 * entao a prova fala o MI com um STUB que implementa a gramatica minima (`token^done`,
 * `*stopped`, dict `chave="valor"`) — mesma formula de stub-gdb do X7-3; o gdb real e
 * exercitado onde ele existe (CI ubuntu).
 */
class KofDebugNativeDapTest {

    private static final String STUB = """
            #!/bin/sh
            while IFS= read -r line; do
              tok="${line%%[!0-9]*}"
              rest="${line#"$tok"}"
              case "$rest" in
                -break-insert*)
                  echo "${tok}^done,bkpt={number=\\"1\\",type=\\"breakpoint\\",line=\\"7\\"}" ;;
                -stack-list-frames*)
                  echo "${tok}^done,stack=[frame={level=\\"0\\",addr=\\"0x1000\\",func=\\"main_kf\\",file=\\"Main.kf\\",line=\\"7\\"}]" ;;
                -stack-list-variables*)
                  echo "${tok}^done,variables=[{name=\\"count\\",args=[],value=\\"3\\",type=\\"int\\"}]" ;;
                -data-evaluate-expression*count)
                  echo "${tok}^done,value=\\"3\\"" ;;
                -data-evaluate-expression*)
                  echo "${tok}^error,msg=\\"No symbol in current context.\\"" ;;
                -exec-run*)
                  echo "${tok}^running"
                  echo '*stopped,reason="breakpoint-hit",bkptnum="1",frame={level="0",func="main_kf",file="Main.kf",line="7"}' ;;
                -gdb-exit*)
                  exit 0 ;;
                *)
                  [ -n "$tok" ] && echo "${tok}^done" ;;
              esac
            done
            """;

    private record Session(Process p, OutputStream toCli, InputStream fromCli) {
    }

    private Session start(Path dir) throws Exception {
        return start(dir, makeStub(dir));
    }

    private static String makeStub(Path dir) throws Exception {
        Path stub = dir.resolve("stub-gdb-mi.sh");
        Files.writeString(stub, STUB);
        stub.toFile().setExecutable(true);
        return stub.toString();
    }

    private Session start(Path dir, String gdbExe) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, "main() {\n    var count = 3\n    println(count)\n}\n");
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of("debug", "--dap", "--target", "native", "Main.kf"));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile())
                .redirectError(ProcessBuilder.Redirect.to(dir.resolve("cli-err.txt").toFile()));
        pb.environment().put("KOF_GDB", gdbExe);
        Process p = pb.start();
        return new Session(p, p.getOutputStream(), p.getInputStream());
    }

    private static void send(Session s, int seq, String command, String argsJson) throws Exception {
        String body = "{\"seq\":" + seq + ",\"type\":\"request\",\"command\":\"" + command
                + "\",\"arguments\":{" + argsJson + "}}";
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        s.toCli().write(("Content-Length: " + b.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        s.toCli().write(b);
        s.toCli().flush();
    }

    private static String next(Session s) throws Exception {
        String line;
        int len = -1;
        while ((line = KofDebug.readLine(s.fromCli())) != null) {
            if (line.isBlank()) {
                break;
            }
            if (line.toLowerCase().startsWith("content-length:")) {
                len = Integer.parseInt(line.substring(15).trim());
            }
        }
        assertNotNull(line, "fluxo DAP fechou sem header");
        if (len < 0) {
            return null;
        }
        byte[] body = s.fromCli().readNBytes(len);
        return new String(body, StandardCharsets.UTF_8);
    }

    private static String await(Session s, String needle, String label) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        List<String> seen = new ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
            String m = next(s);
            if (m == null) {
                continue;
            }
            seen.add(m);
            if (m.contains(needle)) {
                return m;
            }
        }
        fail(label + " nunca chegou; vistos: " + seen);
        return null;
    }

    @Test
    void editorConversationsBreakContinueAndStackTraceComeBackAsKofSource() throws Exception {
        Path dir = Files.createTempDirectory("dap-native-");
        Session s = start(dir);
        try {
            send(s, 1, "initialize", "\"clientID\":\"kof-editor\"");
            assertTrue(await(s, "\"command\":\"initialize\"", "initialize").contains("success"));

            send(s, 2, "launch", "\"program\":\"" + dir.resolve("Main.kf").toString().replace("\\", "\\\\") + "\"");
            assertTrue(await(s, "\"command\":\"launch\"", "launch").contains("success"));

            send(s, 3, "setBreakpoints",
                    "\"source\":{\"path\":\"Main.kf\"},\"breakpoints\":[{\"line\":7}]");
            String bps = await(s, "\"command\":\"setBreakpoints\"", "setBreakpoints");
            assertTrue(bps.contains("\"verified\":true"), "o bkpt do stub vincula na linha 7: " + bps);

            send(s, 4, "configurationDone", "");
            assertTrue(await(s, "\"command\":\"configurationDone\"", "configurationDone").contains("success"));
            assertTrue(await(s, "\"event\":\"stopped\"", "stopped").contains("breakpoint"),
                    "o *-stopped do MI vira DAP stopped/breakpoint");

            send(s, 5, "stackTrace", "\"threadId\":1");
            String st = await(s, "\"command\":\"stackTrace\"", "stackTrace");
            assertTrue(st.contains("\"name\":\"main_kf\""), "frame do MI: " + st);
            assertTrue(st.contains("Main.kf"),
                    "O PONTO DA FRENTE: o editor ve o .kf (source.path = fonte Kof, nunca asm): " + st);

            send(s, 6, "scopes", "\"frameId\":0");
            assertTrue(await(s, "\"command\":\"scopes\"", "scopes").contains("Local"));
            send(s, 7, "variables", "\"variablesReference\":1");
            String vs = await(s, "\"command\":\"variables\"", "variables");
            assertTrue(vs.contains("\"name\":\"count\"") && vs.contains("\"value\":\"3\""),
                    "locals via -stack-list-variables (--simple-values): " + vs);

            send(s, 8, "disconnect", "");
            assertTrue(s.p().waitFor(30, TimeUnit.SECONDS), "disconnect deve encerrar a sessao");
        } finally {
            s.p().destroy();
        }
    }

    @Test
    void evaluateOfUnknownSymbolFailsHonestlyNeverInventsAValue() throws Exception {
        Path dir = Files.createTempDirectory("dap-native-eval-");
        Session s = start(dir);
        try {
            send(s, 1, "initialize", "");
            await(s, "\"command\":\"initialize\"", "initialize");
            send(s, 2, "launch", "");
            await(s, "\"command\":\"launch\"", "launch");
            send(s, 3, "evaluate", "\"expression\":\"count\",\"context\":\"hover\"");
            String ok = await(s, "\"command\":\"evaluate\"", "evaluate");
            assertTrue(ok.contains("\"result\":\"3\""), ok);
            send(s, 4, "evaluate", "\"expression\":\"banana\",\"context\":\"hover\"");
            String bad = await(s, "\"command\":\"evaluate\"", "evaluate 2");
            assertTrue(bad.contains("\"success\":false"),
                    "sem simbolo no contexto = erro honesto do gdb repassado, nunca valor falso: " + bad);
        } finally {
            s.p().destroy();
        }
    }

    @Test
    void launchWithoutGdbAnswersAnHonestDapError() throws Exception {
        Path dir = Files.createTempDirectory("dap-native-nogdb-");
        Session s = start(dir, "/nonexistent/kof-gdb-mi-probe");
        try {
            send(s, 1, "launch", "");
            String r = await(s, "\"command\":\"launch\"", "launch sem gdb");
            assertTrue(r.contains("\"success\":false"),
                    "erro DAP honesto nomeando o gdb, nunca stack no stream: " + r);
            assertTrue(r.contains("gdb"), r);
        } finally {
            s.p().destroy();
        }
    }
}
