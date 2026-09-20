package dev.kof.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

/**
 * Ponte de processo do host JS: expoe {@code kof_platform.processRun} e
 * {@code kof_platform.processRunWith} para o JavaScript gerado (face F4 do
 * {@code kof.process} e 2.2.3 do {@code kof.shell}). A convencao e honesta
 * nos dois alvos: erro de spawn ou argv vazio devolve Map com {@code stderr}
 * preenchido e {@code exitCode == -1} — nunca hang, nunca sucesso silencioso
 * (R6). Ambiente do {@code runWith} e ADITIVO (chaves sobrescrevem herdadas,
 * nunca limpeza silenciosa); cwd {@code ""} = diretorio do processo hospedeiro.
 * Extraido de {@link KofJsRunner} (gate <=500 — 19/09, shell 2.2.3).
 */
public final class KofJsProcessBridge {

    private KofJsProcessBridge() {
    }

    /** Registra as faces de processo no mapa {@code kof_platform}. */
    public static void install(Map<String, Object> platform) {
        platform.put("processRun", (ProxyExecutable) args -> run(args));
        platform.put("processRunWith", (ProxyExecutable) args -> runWith(args));
        platform.put("processSpawn", (ProxyExecutable) args -> spawn(args));
        platform.put("spawnWrite", (ProxyExecutable) args -> {
            write(args[0].asLong(), args[1].asString());
            return 0;
        });
        platform.put("spawnReadLine", (ProxyExecutable) args -> readLine(args[0].asLong()));
        platform.put("spawnExitCode", (ProxyExecutable) args -> exitCode(args[0].asLong()));
        platform.put("spawnKill", (ProxyExecutable) args -> {
            kill(args[0].asLong());
            return 0;
        });
        platform.put("spawnAlive", (ProxyExecutable) args -> alive(args[0].asLong()) ? 1 : 0);
    }

    static Map<String, Object> run(Value[] args) {
        try {
            String program = args[0].asString();
            List<String> cmd = new ArrayList<>();
            cmd.add(program);
            if (args.length > 1 && !args[1].isNull() && args[1].hasArrayElements()) {
                cmd.addAll(argvOf(args[1]));
            }
            return execute(cmd, "", Map.of());
        } catch (Exception e) {
            return honestFailure(e);
        }
    }

    static Map<String, Object> runWith(Value[] args) {
        try {
            List<String> cmd = args.length > 0 && args[0].hasArrayElements()
                    ? argvOf(args[0]) : List.of();
            if (cmd.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("stdout", "");
                result.put("stderr", "kof_shell_runwith: empty argv");
                result.put("exitCode", -1);
                return result;
            }
            String cwd = args.length > 1 && args[1].isString() ? args[1].asString() : "";
            Map<String, String> env = new LinkedHashMap<>();
            if (args.length > 2 && !args[2].isNull() && args[2].hasMembers()) {
                for (String k : args[2].getMemberKeys()) {
                    Value v = args[2].getMember(k);
                    env.put(k, v.isString() ? v.asString() : String.valueOf(v));
                }
            }
            return execute(cmd, cwd, env);
        } catch (Exception e) {
            return honestFailure(e);
        }
    }

    private static List<String> argvOf(Value arrayArg) {
        List<String> cmd = new ArrayList<>();
        long n = arrayArg.getArraySize();
        for (long i = 0; i < n; i++) {
            Value v = arrayArg.getArrayElement(i);
            cmd.add(v.isString() ? v.asString() : String.valueOf(v));
        }
        return cmd;
    }

    private static Map<String, Object> execute(List<String> cmd, String cwd,
                                               Map<String, String> extraEnv) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(false);
            if (cwd != null && !cwd.isEmpty()) {
                pb.directory(new java.io.File(cwd));
            }
            if (!extraEnv.isEmpty()) {
                pb.environment().putAll(extraEnv);
            }
            Process p = pb.start();
            String outText = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            String errText = new String(p.getErrorStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            int code = p.waitFor();
            result.put("stdout", outText);
            result.put("stderr", errText);
            result.put("exitCode", code);
        } catch (Exception e) {
            return honestFailure(e);
        }
        return result;
    }

    private static Map<String, Object> honestFailure(Exception e) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stdout", "");
        result.put("stderr", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        result.put("exitCode", -1);
        return result;
    }

    // ── process.spawn (F10) — stdin/stdout vivos no host JS. Espelho EXATO
    // do binding JVM medido em JvmRuntimeCore (279-350): handle = seq Long,
    // spawn falho = -1, readLine EOF/morto = "", exitCode ainda vivo =
    // Integer.MIN_VALUE, kill = destroyForcibly + remove. Mesma JVM do
    // processo hospedeiro (graal), mesmo ProcessBuilder — paridade por
    // construção, não por imitação.

    private static final java.util.concurrent.ConcurrentHashMap<Long, Process> SPAWNED =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Long, java.io.BufferedReader> SPAWN_READERS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Long, java.io.PrintWriter> SPAWN_WRITERS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static long spawnSeq = 0;

    static long spawn(Value[] args) {
        try {
            String program = args[0].asString();
            List<String> cmd = new ArrayList<>();
            cmd.add(program);
            if (args.length > 1 && !args[1].isNull() && args[1].hasArrayElements()) {
                cmd.addAll(argvOf(args[1]));
            }
            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(false)
                    .redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")))
                    .start();
            long id;
            synchronized (KofJsProcessBridge.class) {
                id = ++spawnSeq;
            }
            SPAWNED.put(id, p);
            SPAWN_READERS.put(id, new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(),
                            java.nio.charset.StandardCharsets.UTF_8)));
            SPAWN_WRITERS.put(id, new java.io.PrintWriter(
                    new java.io.OutputStreamWriter(p.getOutputStream(),
                            java.nio.charset.StandardCharsets.UTF_8), true));
            return id;
        } catch (Exception e) {
            return -1;
        }
    }

    static String readLine(long handle) {
        var r = SPAWN_READERS.get(handle);
        if (r == null) {
            return "";
        }
        try {
            String line = r.readLine();
            return line == null ? "" : line;
        } catch (Exception e) {
            return "";
        }
    }

    static void write(long handle, String data) {
        var w = SPAWN_WRITERS.get(handle);
        if (w == null) {
            return;
        }
        w.println(data);
        w.flush();
    }

    static int exitCode(long handle) {
        var p = SPAWNED.get(handle);
        if (p == null) {
            return -1;
        }
        try {
            if (p.isAlive()) {
                return Integer.MIN_VALUE;
            }
            return p.exitValue();
        } catch (Exception e) {
            return -1;
        }
    }

    static void kill(long handle) {
        var p = SPAWNED.get(handle);
        if (p != null) {
            p.destroyForcibly();
            SPAWNED.remove(handle);
            SPAWN_WRITERS.remove(handle);
            SPAWN_READERS.remove(handle);
        }
    }

    static boolean alive(long handle) {
        var p = SPAWNED.get(handle);
        return p != null && p.isAlive();
    }
}
