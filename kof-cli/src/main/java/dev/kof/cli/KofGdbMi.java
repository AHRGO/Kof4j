package dev.kof.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * KofGdbMi — cliente MINIMO do GDB/MI (protocolo de maquina do gdb) por stdio.
 *
 * Kof NAO reimplementa um debugger (regra: complexidade pertence a plataforma):
 * esta classe fala MI2 com o gdb REAL e devolve tres coisas — a payload `^done`
 * de um comando tokenizado, o registro de evento (`*stopped`, `=running`) e o
 * stream de console (`&"..."`). A gramatica MI aqui e recortada de proposito:
 * so os campos que o DAP precisa (reason, line, file, func, listas de frame),
 * extraindo `"chave":"valor"` do texto — nada de parser completo de MI.
 */
final class KofGdbMi {

    /** Payload de um comando concluido: token -> resto da linha (`^done,bkpt={...}`). */
    record Done(String payload) {
        boolean isError() {
            return payload.startsWith("^error");
        }
    }

    private final Process gdb;
    private final OutputStream toGdb;
    private final AtomicInteger token = new AtomicInteger(1);
    private final Map<Integer, LinkedBlockingQueue<String>> waiting = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();
    private volatile BiConsumer<String, String> onEvent;
    private volatile Runnable onExit;

    KofGdbMi(String gdbExe, Path sourceDir, Path bin) throws IOException {
        this(spawn(gdbExe, sourceDir, bin));
    }

    /** X7-5: anexa ao PID vivo (`gdb -p`) — sem ELF próprio; o DWARF vem do exe em execucao. */
    static KofGdbMi attach(String gdbExe, Path sourceDir, int pid) throws IOException {
        return new KofGdbMi(spawn(gdbExe, sourceDir, null, pid));
    }

    private static Process spawn(String gdbExe, Path sourceDir, Path bin) throws IOException {
        return spawn(gdbExe, sourceDir, bin, 0);
    }

    private static Process spawn(String gdbExe, Path sourceDir, Path bin, int pid) throws IOException {
        List<String> argv = new ArrayList<>(List.of(gdbExe, "-q", "-nx",
                "-iex", "set pagination off",
                "-iex", "set mi async on",
                "-iex", "directory " + sourceDir,
                "--interp", "mi2"));
        if (pid > 0) {
            argv.addAll(List.of("-p", String.valueOf(pid)));
        } else if (bin != null) {
            argv.add(bin.toString());
        } else {
            throw new IllegalArgumentException("KofGdbMi.spawn: either bin or pid is required");
        }
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private KofGdbMi(Process started) {
        gdb = started;
        toGdb = gdb.getOutputStream();
        Thread reader = new Thread(this::pump, "gdb-mi-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** Callback (tipo, payload) p/ `*`/`=` assincronos; chamado na thread do reader. */
    void setEventHandler(BiConsumer<String, String> handler) {
        this.onEvent = handler;
    }

    void setExitHandler(Runnable handler) {
        this.onExit = handler;
    }

    private void pump() {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(gdb.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                int k = 0;
                while (k < line.length() && Character.isDigit(line.charAt(k))) {
                    k++;
                }
                char kind = k < line.length() ? line.charAt(k) : ' ';
                if (kind == '^' || kind == '*' || kind == '=') {
                    dispatch(line);
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (onExit != null) {
                onExit.run();
            }
        }
    }

    private void dispatch(String line) {
        int tok = 0;
        int i = 0;
        while (i < line.length() && Character.isDigit(line.charAt(i))) {
            tok = tok * 10 + (line.charAt(i++) - '0');
        }
        String rest = line.substring(i);
        char kind = rest.charAt(0);
        if (kind == '^') {
            LinkedBlockingQueue<String> q = waiting.remove(tok);
            if (q != null) {
                q.add(rest);
            }
        } else {
            events.add(rest);
            BiConsumer<String, String> h = onEvent;
            if (h != null) {
                h.accept(String.valueOf(kind), rest);
            }
        }
    }

    /** Envia um comando e espera a resposta `^` do MESMO token (timeout = erro, nunca hang). */
    Done command(String text, long timeoutMs) throws IOException {
        int t = token.getAndIncrement();
        LinkedBlockingQueue<String> q = new LinkedBlockingQueue<>();
        waiting.put(t, q);
        toGdb.write((t + text + "\n").getBytes(StandardCharsets.UTF_8));
        toGdb.flush();
        try {
            String payload = q.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (payload == null) {
                waiting.remove(t);
                return new Done("^error,msg=\"timeout waiting for gdb\"");
            }
            return new Done(payload);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Done("^error,msg=\"interrupted\"");
        }
    }

    /** Comando fire-and-forget (execucao assincrona: os eventos virao por handler). */
    void send(String text) throws IOException {
        toGdb.write(text.getBytes(StandardCharsets.UTF_8));
        toGdb.write('\n');
        toGdb.flush();
    }

    /** Espera um `*stopped` com timeout honesto; devolve o payload cru ou null. */
    String pollStopped(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String ev = null;
            try {
                ev = events.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            if (ev != null && ev.startsWith("*stopped")) {
                return ev;
            }
        }
        return null;
    }

    boolean alive() {
        return gdb.isAlive();
    }

    void close() {
        try {
            send("-gdb-exit");
        } catch (IOException ignored) {
        }
        gdb.destroy();
    }

    /** Extrai `chave="valor"` de um payload MI (chaves MI nao tem aspas; fronteira: inicio ou {,). */
    static String field(String payload, String key) {
        String needle = key + "=\"";
        for (int a = payload.indexOf(needle); a >= 0; a = payload.indexOf(needle, a + 1)) {
            if (a == 0 || payload.charAt(a - 1) == '{' || payload.charAt(a - 1) == ',') {
                a += needle.length();
                int b = payload.indexOf('"', a);
                return b < 0 ? null : payload.substring(a, b);
            }
        }
        return null;
    }
}
