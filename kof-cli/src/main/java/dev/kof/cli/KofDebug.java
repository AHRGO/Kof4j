package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KofDebug — Debug Adapter Protocol (DAP) server for Kof.
 *
 * The user debugs Kof source, never bytecode: the program is compiled with
 * debug metadata (SourceFile + LineNumberTable), launched with JDWP, and
 * driven through the raw JDWP protocol by {@link JdwpClient} (no jdk.jdi
 * dependency — self-contained tooling).
 *
 * MVP requests: initialize, launch, setBreakpoints, configurationDone,
 * continue, threads, stackTrace, scopes, variables, disconnect.
 */
final class KofDebug {

    private KofDebug() {
    }

    public static int run(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: kof debug [--target jvm|native] <file.kf>");
            return 1;
        }
        String target = "jvm";
        int i = 1;
        if (args[i].equals("--target")) {
            i++;
            if (i >= args.length) {
                System.err.println("debug: --target requires a value (jvm|native)");
                return 1;
            }
            target = args[i++];
        } else if (args[i].startsWith("-")) {
            // R6 strictness (CliFlagStrictnessTest): a typo must not be silently ignored.
            System.err.println("debug: unknown flag: " + args[i] + " (usage: kof debug [--target jvm|native] <file.kf>)");
            return 1;
        }
        if (i >= args.length) {
            System.err.println("debug: missing file (usage: kof debug [--target jvm|native] <file.kf>)");
            return 1;
        }
        Path file = Path.of(args[i++]);
        if (i < args.length) {
            System.err.println("debug: " + (args[i].startsWith("-") ? "unknown flag: " : "unexpected argument: ")
                    + args[i] + " (usage: kof debug [--target jvm|native] <file.kf>)");
            return 1;
        }
        if (!Files.exists(file)) {
            System.err.println("file not found: " + file);
            return 1;
        }
        if (target.equals("native")) {
            return debugNative(file);
        }
        if (target.equals("js")) {
            System.err.println("debug js: honest gap — the JS target runs on the EMBEDDED engine"
                    + " (there is no node/inspector to attach to). Roadmap §19.5 face 7 stays open.");
            return 1;
        }
        if (!target.equals("jvm")) {
            System.err.println("debug: unknown --target '" + target + "' (jvm|native; js = honest gap;"
                    + " android = packaging, not a debug target)");
            return 1;
        }
        try {
            new DebugSession(file).run();
            return 0;
        } catch (Exception e) {
            System.err.println("kof debug: " + e.getMessage());
            return 1;
        }
    }

    /**
     * X7-3 (face NATIVE do debug, fase 6 do §19.5): o Kof NAO reimplementa um
     * debugger — ele constrói o ELF com DWARF (line table + DIEs do X7-1/X7-2,
     * on por default) e delega ao gdb do alvo, apontando-o para o diretório da
     * FONTE Kof (o `.file` do DWARF é nome relativo; sem o `directory`, o gdb
     * mostra asm). Intenção no comando, mecanismo no platform — o usuario escreve
     * `break Main.kf:2` na fonte, nunca no mangle. O executavel do gdb e
     * resolvido por `KOF_GDB` (override de teste/ambiente; padrao da casa:
     * `KOF_PUBLISH_API`/`KOF_CROSS_SYSROOT`), senao `gdb`.
     */
    private static int debugNative(Path file) {
        Path out = null;
        try {
            out = Files.createTempDirectory("kof-debug-native-");
            CompilerDriver driver = new CompilerDriver();
            driver.setDebugInfoEnabled(true);
            CompilationResult r = driver.compile(file.toAbsolutePath(), out, Target.NATIVE);
            if (!r.success()) {
                r.diagnostics().getDiagnostics().forEach(d -> System.err.println(d.format()));
                return 1;
            }
            Path bin = out.resolve("Default").resolve("Main");
            if (!Files.exists(bin)) {
                System.err.println("debug native: no ELF produced (native toolchain missing on this host)");
                return 1;
            }
            String gdb = firstNonEmpty(System.getenv("KOF_GDB"), "gdb");
            ProcessBuilder pb = new ProcessBuilder(gdb, "-q",
                    "-iex", "set pagination off",
                    "-iex", "directory " + file.toAbsolutePath().getParent(),
                    bin.toString());
            pb.inheritIO();
            try {
                return pb.start().waitFor();
            } catch (java.io.IOException spawnFail) {
                System.err.println("debug native: '" + gdb + "' not available — install gdb"
                        + " (roadmap §19.5 fase 6: gdb front-end over the Kof ELF; the DWARF"
                        + " is already emitted by the compiler)");
                return 1;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return 1;
            }
        } catch (Exception e) {
            System.err.println("kof debug native: " + e.getMessage());
            return 1;
        } finally {
            if (out != null) {
                KofCliSupport.cleanup(out);
            }
        }
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.isEmpty() ? a : b;
    }

    private static final class DebugSession {
        private final Path sourceFile;
        private final CompilerDriver driver = new CompilerDriver();
        private final List<Integer> pendingBreakpoints = new ArrayList<>();
        private final Map<Integer, Map<String, Object>> frameVariables = new LinkedHashMap<>();
        private Process jvmProcess;
        private JdwpClient jdwp;
        private Path classesDir;
        private int nextSeq = 1;
        private OutputStream out;
        private volatile long stoppedThread = -1;
        private volatile int stoppedLine = -1;

        DebugSession(Path sourceFile) {
            this.sourceFile = sourceFile;
        }

        void run() throws Exception {
            out = System.out;
            InputStream in = System.in;
            while (true) {
                int contentLength = -1;
                while (true) {
                    String line = readLine(in);
                    if (line == null) return;
                    if (line.isBlank()) break;
                    if (line.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                    }
                }
                if (contentLength < 0) continue;
                byte[] body = in.readNBytes(contentLength);
                if (body.length < contentLength) return;
                Object parsed = Json.parse(new String(body, StandardCharsets.UTF_8));
                if (!(parsed instanceof Map<?, ?> msg)) continue;
                Map<String, Object> m = (Map<String, Object>) msg;
                if (!"request".equals(String.valueOf(m.get("type")))) continue;
                Object seq = m.get("seq");
                String command = String.valueOf(m.get("command"));
                Map<String, Object> args = m.get("arguments") instanceof Map<?, ?> a
                        ? (Map<String, Object>) a : Map.of();
                handleRequest(seq, command, args);
            }
        }

        private void handleRequest(Object seq, String command, Map<String, Object> args) throws Exception {
            switch (command) {
                case "initialize" -> {
                    Map<String, Object> caps = new LinkedHashMap<>();
                    caps.put("supportsConfigurationDoneRequest", true);
                    caps.put("supportsTerminateRequest", true);
                    respond(seq, command, caps);
                }
                case "launch" -> {
                    String program = args.get("program") == null ? sourceFile.toString()
                            : args.get("program").toString();
                    launch(Path.of(program));
                    respond(seq, command, Map.of());
                }
                case "setBreakpoints" -> {
                    List<Object> bps = args.get("breakpoints") instanceof List<?> l
                            ? new ArrayList<>(l) : List.of();
                    List<Object> result = new ArrayList<>();
                    pendingBreakpoints.clear();
                    for (Object bp : bps) {
                        if (bp instanceof Map<?, ?> bpm && bpm.get("line") instanceof Number n) {
                            int line = n.intValue();
                            pendingBreakpoints.add(line);
                            Map<String, Object> brk = new LinkedHashMap<>();
                            brk.put("verified", false);
                            brk.put("line", line);
                            result.add(brk);
                        }
                    }
                    respond(seq, command, Map.of("breakpoints", result));
                }
                case "configurationDone" -> {
                    if (jdwp != null) jdwp.resume();
                    respond(seq, command, Map.of());
                }
                case "continue" -> {
                    if (jdwp != null) jdwp.resume();
                    stoppedThread = -1;
                    respond(seq, command, Map.of("allThreadsContinued", true));
                }
                case "threads" -> {
                    List<Object> threads = new ArrayList<>();
                    if (jdwp != null) {
                        for (long tid : jdwp.allThreads()) {
                            Map<String, Object> t = new LinkedHashMap<>();
                            t.put("id", tid);
                            t.put("name", "kof-thread-" + tid);
                            threads.add(t);
                        }
                    }
                    respond(seq, command, Map.of("threads", threads));
                }
                case "stackTrace" -> {
                    List<Object> frames = new ArrayList<>();
                    if (jdwp != null) {
                        long threadId = args.get("threadId") instanceof Number n
                                ? n.longValue() : stoppedThread;
                        int idx = 0;
                        frameVariables.clear();
                        for (JdwpClient.FrameInfo f : jdwp.frames(threadId, 50)) {
                            Map<String, Object> frame = new LinkedHashMap<>();
                            frame.put("id", idx);
                            frame.put("name", f.methodName());
                            Map<String, Object> src = new LinkedHashMap<>();
                            src.put("path", sourceFile.toAbsolutePath().toString());
                            src.put("line", f.line());
                            frame.put("source", src);
                            frame.put("line", f.line());
                            frame.put("column", 1);
                            frames.add(frame);
                            Map<String, Object> varInfo = new LinkedHashMap<>();
                            varInfo.put("name", f.methodName());
                            varInfo.put("line", f.line());
                            frameVariables.put(idx, varInfo);
                            idx++;
                        }
                    }
                    respond(seq, command, Map.of("stackFrames", frames, "totalFrames", frames.size()));
                }
                case "scopes" -> {
                    List<Object> scopes = new ArrayList<>();
                    if (args.get("frameId") instanceof Number n) {
                        Map<String, Object> scope = new LinkedHashMap<>();
                        scope.put("name", "Local");
                        scope.put("variablesReference", n.intValue() + 1);
                        scope.put("expensive", false);
                        scopes.add(scope);
                    }
                    respond(seq, command, Map.of("scopes", scopes));
                }
                case "variables" -> {
                    List<Object> vars = new ArrayList<>();
                    if (args.get("variablesReference") instanceof Number n) {
                        int ref = n.intValue();
                        Map<String, Object> info = frameVariables.get(ref - 1);
                        if (info != null) {
                            Map<String, Object> v = new LinkedHashMap<>();
                            v.put("name", info.get("name"));
                            v.put("value", "line " + info.get("line"));
                            v.put("type", "frame");
                            v.put("variablesReference", 0);
                            vars.add(v);
                        }
                    }
                    respond(seq, command, Map.of("variables", vars));
                }
                case "disconnect", "terminate" -> {
                    if (jdwp != null) jdwp.dispose();
                    if (jvmProcess != null) jvmProcess.destroy();
                    cleanup();
                    respond(seq, command, Map.of());
                    out.flush();
                    System.exit(0);
                }
                default -> respond(seq, command, Map.of());
            }
        }

        private void launch(Path file) throws Exception {
            classesDir = Files.createTempDirectory("kof-debug-");
            CompilationResult result = driver.compile(file, classesDir, Target.JVM);
            if (!result.success()) {
                throw new IOException("compilation failed");
            }
            int port;
            try (ServerSocket ss = new ServerSocket(0)) {
                port = ss.getLocalPort();
            }
            List<String> cmd = new ArrayList<>();
            cmd.add(javaExecutable());
            cmd.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + port);
            cmd.add("-cp");
            cmd.add(classesDir.toString());
            cmd.add("Default.Main");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            jvmProcess = pb.start();
            Thread sink = new Thread(() -> {
                try {
                    byte[] buf = new byte[1024];
                    while (jvmProcess.getInputStream().read(buf) != -1) {
                        System.err.print(new String(buf, 0, buf.length).trim());
                    }
                } catch (IOException ignored) {
                }
            }, "debuggee-sink");
            sink.setDaemon(true);
            sink.start();

            jdwp = new JdwpClient("127.0.0.1", port);
            jdwp.connect();
            jdwp.setClassPrepareRequest("Default.Main", (kind, threadId, typeId) -> {
                try {
                    if (kind == 8) {
                        for (Integer line : pendingBreakpoints) {
                            jdwp.setLineBreakpoint(typeId, line);
                        }
                        jdwp.resume();
                    } else if (kind == 2) {
                        stoppedThread = threadId;
                        for (JdwpClient.FrameInfo f : jdwp.frames(threadId, 1)) {
                            stoppedLine = f.line();
                        }
                        notifyStopped();
                    }
                } catch (IOException e) {
                    System.err.println("kof debug: " + e.getMessage());
                }
            });
        }

        private void notifyStopped() throws IOException {
            Map<String, Object> evt = new LinkedHashMap<>();
            evt.put("seq", nextSeq++);
            evt.put("type", "event");
            evt.put("event", "stopped");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("reason", "breakpoint");
            body.put("threadId", stoppedThread);
            body.put("allThreadsStopped", true);
            evt.put("body", body);
            writeMessage(out, Json.stringify(evt));
        }

        private void respond(Object seq, String command, Object body) throws IOException {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("seq", nextSeq++);
            response.put("type", "response");
            response.put("request_seq", seq);
            response.put("success", true);
            response.put("command", command);
            response.put("body", body);
            writeMessage(out, Json.stringify(response));
        }

        private void cleanup() {
            if (classesDir != null) {
                try (var s = Files.walk(classesDir)) {
                    s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
                } catch (IOException ignored) {
                }
            }
        }
    }

    static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
        }
        return sb.length() == 0 && c == -1 ? null : sb.toString();
    }

    static void writeMessage(OutputStream out, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private static String javaExecutable() {
        String javaHome = System.getProperty("java.home");
        return javaHome + "/bin/java";
    }
}