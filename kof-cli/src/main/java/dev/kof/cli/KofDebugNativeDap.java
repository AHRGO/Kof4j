package dev.kof.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KofDebugNativeDap — DAP sobre o NATIVE alvo: traduz os pedidos do editor
 * (Kof Editor / qualquer cliente DAP) para GDB/MI contra o ELF Kof construido
 * com DWARF (X7-1/X7-2). O gdb faz o trabalho pesado; aqui so ha o recorte do
 * protocolo (X7-4, fase 7 do §19.5). O editor ve SEMPRE o .kf: o `source.path`
 * de cada frame e a fonte Kof, nunca o asm.
 */
final class KofDebugNativeDap {

    private final Path sourceFile;
    private final Integer attachPid;
    private final OutputStream out = System.out;
    private KofGdbMi mi;
    private Path buildDir;
    private int nextSeq = 1;
    private final Map<Integer, Integer> frameLevel = new LinkedHashMap<>();

    KofDebugNativeDap(Path sourceFile, Integer attachPid) {
        this.sourceFile = sourceFile;
        this.attachPid = attachPid;
    }

    void run() throws Exception {
        buildDir = null;
        if (attachPid != null) {
            // X7-5: o alvo NATIVO ja esta vivo — gdb -p ANTES de qualquer pedido
            // (mesma semantica da sessao JVM); launch/configurationDone viram no-op honesto.
            String gdb = System.getenv("KOF_GDB");
            mi = KofGdbMi.attach(gdb == null || gdb.isEmpty() ? "gdb" : gdb,
                    sourceFile.toAbsolutePath().getParent(), attachPid);
            mi.setEventHandler(this::onMiEvent);
        }
        InputStream in = System.in;
        while (true) {
            int contentLength = -1;
            while (true) {
                String line = KofDebug.readLine(in);
                if (line == null) {
                    cleanup();
                    return;
                }
                if (line.isBlank()) {
                    break;
                }
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                }
            }
            if (contentLength < 0) {
                continue;
            }
            byte[] body = in.readNBytes(contentLength);
            if (body.length < contentLength) {
                return;
            }
            if (!(Json.parse(new String(body, StandardCharsets.UTF_8)) instanceof Map<?, ?> msg)) {
                continue;
            }
            Map<String, Object> m = (Map<String, Object>) msg;
            if (!"request".equals(String.valueOf(m.get("type")))) {
                continue;
            }
            handleRequest(m.get("seq"), String.valueOf(m.get("command")),
                    m.get("arguments") instanceof Map<?, ?> a ? (Map<String, Object>) a : Map.of());
        }
    }

    private void handleRequest(Object seq, String command, Map<String, Object> args) throws IOException {
        switch (command) {
            case "initialize" -> respond(seq, command, Map.of(
                    "supportsConfigurationDoneRequest", true,
                    "supportsTerminateRequest", true));
            case "launch" -> {
                if (attachPid != null) {
                    respond(seq, command, Map.of());
                    return;
                }
                String gdb = System.getenv("KOF_GDB");
                String gdbExe = gdb == null || gdb.isEmpty() ? "gdb" : gdb;
                try {
                    if (attachPid != null) {
                        // X7-5: gdb -p PID no processo NATIVO vivo — sem build, sem launch.
                        mi = KofGdbMi.attach(gdbExe, sourceFile.toAbsolutePath().getParent(), attachPid);
                    } else {
                        KofDebug.NativeBuild built = KofDebug.buildNativeElf(sourceFile);
                        if (built == null) {
                            fail(seq, command, "native build failed (toolchain or source error — see stderr)");
                            return;
                        }
                        buildDir = built.dir();
                        mi = new KofGdbMi(gdbExe, sourceFile.toAbsolutePath().getParent(), built.bin());
                    }
                } catch (IOException spawnFail) {
                    mi = null;
                    KofCliSupport.cleanup(buildDir);
                    buildDir = null;
                    fail(seq, command, "gdb '" + gdbExe + "' not available — install gdb"
                            + " (roadmap §19.5 fase 6/7: the DWARF is already emitted by the compiler)");
                    return;
                }
                mi.setEventHandler(this::onMiEvent);
                respond(seq, command, Map.of());
            }
            case "setBreakpoints" -> {
                List<Object> result = new ArrayList<>();
                if (mi != null) {
                    mi.command("-break-delete *", 5000);
                    for (Object bp : args.get("breakpoints") instanceof List<?> l ? l : List.of()) {
                        if (bp instanceof Map<?, ?> bpm && bpm.get("line") instanceof Number n) {
                            int line = n.intValue();
                            KofGdbMi.Done d = mi.command("-break-insert -f -- "
                                    + sourceFile.getFileName() + ":" + line, 5000);
                            String real = KofGdbMi.field(d.payload(), "line");
                            Map<String, Object> brk = new LinkedHashMap<>();
                            brk.put("verified", real != null && !d.isError());
                            brk.put("line", intOr(real, line));
                            result.add(brk);
                        }
                    }
                }
                respond(seq, command, Map.of("breakpoints", result));
            }
            case "configurationDone" -> {
                if (mi != null && attachPid == null) {
                    mi.send("-exec-run --all");
                }
                respond(seq, command, Map.of());
            }
            case "continue" -> exec(seq, command, "-exec-continue --all",
                    Map.of("allThreadsContinued", true));
            case "next" -> exec(seq, command, "-exec-next --all", Map.of());
            case "stepIn" -> exec(seq, command, "-exec-step --all", Map.of());
            case "stepOut" -> exec(seq, command, "-exec-finish --all", Map.of());
            case "threads" -> respond(seq, command, Map.of("threads",
                    List.of(Map.of("id", 1, "name", "kof-native"))));
            case "stackTrace" -> {
                List<Object> frames = new ArrayList<>();
                frameLevel.clear();
                if (mi != null) {
                    KofGdbMi.Done d = mi.command("-stack-list-frames", 5000);
                    String rest = d.payload();
                    while (true) {
                        int a = rest.indexOf("frame={");
                        if (a < 0) {
                            break;
                        }
                        int end = rest.indexOf('}', a);
                        String f = rest.substring(a, end);
                        rest = rest.substring(end);
                        String func = KofGdbMi.field(f, "func");
                        String line = KofGdbMi.field(f, "line");
                        int id = frames.size();
                        Map<String, Object> frame = new LinkedHashMap<>();
                        frame.put("id", id);
                        frame.put("name", func == null ? "?" : func);
                        frame.put("source", Map.of("path", sourceFile.toAbsolutePath().toString()));
                        frame.put("line", intOr(line, 0));
                        frame.put("column", 1);
                        frames.add(frame);
                        frameLevel.put(id, intOr(KofGdbMi.field(f, "level"), id));
                    }
                }
                respond(seq, command, Map.of("stackFrames", frames, "totalFrames", frames.size()));
            }
            case "scopes" -> {
                int id = args.get("frameId") instanceof Number n ? n.intValue() : -1;
                respond(seq, command, Map.of("scopes", frameLevel.containsKey(id)
                        ? List.of(Map.of("name", "Local", "variablesReference", id + 1, "expensive", false))
                        : List.of()));
            }
            case "variables" -> {
                List<Object> vars = new ArrayList<>();
                if (mi != null && args.get("variablesReference") instanceof Number n
                        && frameLevel.containsKey(n.intValue() - 1)) {
                    KofGdbMi.Done d = mi.command("-stack-list-variables --frame "
                            + frameLevel.get(n.intValue() - 1) + " --simple-values", 5000);
                    String rest = d.payload();
                    while (true) {
                        int a = -1;
                        for (int p = rest.indexOf("name=\""); p >= 0; p = rest.indexOf("name=\"", p + 1)) {
                            if (p == 0 || rest.charAt(p - 1) == '{' || rest.charAt(p - 1) == ',') {
                                a = p;
                                break;
                            }
                        }
                        if (a < 0) {
                            break;
                        }
                        String name = KofGdbMi.field(rest.substring(a), "name");
                        String value = KofGdbMi.field(rest.substring(a), "value");
                        Map<String, Object> v = new LinkedHashMap<>();
                        v.put("name", name);
                        v.put("value", value == null ? "?" : value);
                        v.put("variablesReference", 0);
                        vars.add(v);
                        rest = rest.substring(a + 6);
                    }
                }
                respond(seq, command, Map.of("variables", vars));
            }
            case "evaluate" -> {
                if (mi == null || !(args.get("expression") instanceof String expr)) {
                    fail(seq, command, "not ready");
                    return;
                }
                KofGdbMi.Done d = mi.command("-data-evaluate-expression " + expr, 5000);
                String value = KofGdbMi.field(d.payload(), "value");
                if (d.isError() || value == null) {
                    fail(seq, command, "gdb: " + d.payload());
                } else {
                    respond(seq, command, Map.of("result", value, "variablesReference", 0));
                }
            }
            case "disconnect", "terminate" -> {
                if (mi != null) {
                    mi.close();
                }
                cleanup();
                respond(seq, command, Map.of());
                out.flush();
                Runtime.getRuntime().halt(0);
            }
            default -> respond(seq, command, Map.of());
        }
    }

    private void exec(Object seq, String command, String miCmd, Map<String, Object> body) throws IOException {
        if (mi == null) {
            fail(seq, command, "not launched");
            return;
        }
        mi.send(miCmd);
        respond(seq, command, body);
    }

    /** MI `*stopped` -> DAP `stopped` (ou `exited`+`terminated` no fim). */
    private void onMiEvent(String kind, String payload) {
        try {
            if (!kind.equals("*") || !payload.startsWith("*stopped")) {
                return;
            }
            String reason = KofGdbMi.field(payload, "reason");
            if (reason == null) {
                reason = "breakpoint-hit";
            }
            String exitCode = KofGdbMi.field(payload, "exit-code");
            if (exitCode != null || reason.startsWith("exited")) {
                emit("exited", Map.of("exitCode", exitCode == null ? "0" : exitCode));
                emit("terminated", Map.of());
                return;
            }
            String mapped = switch (reason) {
                case "entry-breakpoint" -> "entry";
                case "end-stepping-range" -> "step";
                case "signal-received" -> "signal";
                default -> "breakpoint";
            };
            emit("stopped", Map.of("reason", mapped, "threadId", 1, "allThreadsStopped", true));
        } catch (IOException ignored) {
        }
    }

    private synchronized void emit(String event, Map<String, Object> body) throws IOException {
        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("seq", nextSeq++);
        evt.put("type", "event");
        evt.put("event", event);
        evt.put("body", body);
        KofDebug.writeMessage(out, Json.stringify(evt));
    }

    private synchronized void respond(Object seq, String command, Map<String, Object> body) throws IOException {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("seq", nextSeq++);
        response.put("type", "response");
        response.put("request_seq", seq);
        response.put("success", true);
        response.put("command", command);
        response.put("body", body);
        KofDebug.writeMessage(out, Json.stringify(response));
    }

    private synchronized void fail(Object seq, String command, String message) throws IOException {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("seq", nextSeq++);
        response.put("type", "response");
        response.put("request_seq", seq);
        response.put("success", false);
        response.put("message", message);
        response.put("command", command);
        KofDebug.writeMessage(out, Json.stringify(response));
    }

    /** Campo numerico do MI: numero valido ou o fallback — lixo do gdb nunca derruba a sessao DAP. */
    private static int intOr(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void cleanup() {
        if (buildDir != null) {
            KofCliSupport.cleanup(buildDir);
        }
    }
}
