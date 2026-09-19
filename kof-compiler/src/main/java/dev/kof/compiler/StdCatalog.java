package dev.kof.compiler;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * X10 fatia 1 (fila X, IMPLEMENTATION-UNIVERSAL-PLATFORM) — catálogo de
 * membros dos namespaces stdlib roteado por {@link KofStd#staticMethod}
 * (math, strings, encoding, net, uuid, random, rng — fatia 1; time, http, db,
 * cache, process e os 6 namespaces de segurança — fatia 2; json, log, orm, config,
 * gpu, mq, validation, observability, tetris e Image/Audio/Video/Mic — fatia 3).
 * Alimenta o completion
 * domain-aware do LSP SEM parser paralelo: os nomes são uma transcrição
 * dos `case` dos próprios typers, e o `StdCatalogTest` trava as duas pontas
 * contra a fonte real — lista ≠ case-literals do `switch (name)` ou
 * dispatch do KofStd ≠ chaves do catálogo ⇒ vermelho (padrão
 * RuntimeSlices: transcription protegida por teste, nunca confiança).
 *
 * <p>Escopo honesto: o DSL de {@code web}/{@code app} (recebedores com
 * semântica própria de rota) e {@code ui}/{@code ffi} ficam de fora — faces
 * seguintes do X10 (R6: não fingir cobertura total).
 */
public final class StdCatalog {

    private StdCatalog() {}

    private static final Map<String, List<String>> MEMBERS;

    static {
        // fatia 1: os 7 do dispatch KofStd; fatia 2: dispatch próprio
        // (time/http/db/cache/process) + os 6 namespaces de segurança.
        var m = new java.util.LinkedHashMap<String, List<String>>();
        m.put("math", KofMath.functions());
        m.put("strings", KofStrings.functions());
        m.put("encoding", KofEncoding.functions());
        m.put("net", KofNet.functions());
        m.put("uuid", KofUuid.functions());
        m.put("random", KofRandom.functions());
        m.put("rng", KofRng.functions());
        m.put("time", KofTime.functions());
        m.put("http", KofHttp.functions());
        m.put("db", KofDb.functions());
        m.put("cache", KofCache.functions());
        m.put("process", KofProcess.functions());
        m.put("shell", KofShell.functions());
        m.putAll(KofSecurity.functions());
        // fatia 3: receiver-typed com dispatch próprio (MemberCallNamespaces)
        m.put("json", List.of("encode", "decode"));
        m.put("log", KofLog.functions());
        m.put("orm", KofOrm.functions());
        m.put("config", KofConfig.functions());
        m.put("gpu", KofGpu.functions());
        m.put("mq", KofMq.functions());
        m.put("validation", KofValidation.functions());
        m.put("observability", KofObservability.functions());
        m.put("tetris", KofTetris.functions());
        m.putAll(KofMedia.functions());
        MEMBERS = java.util.Collections.unmodifiableMap(m);
    }

    // LSP-A (D-POLL-19 19/09; linha 8.3): assinaturas transcritas do DISPATCHER
    // REAL de cada namespace (KofDb.staticCall / KofHttp.staticCall) e travadas
    // comportamento-a-comportamento em StdCatalogSignaturesTest (chamar com a
    // aridade gravada => bind; um bind a mais no db => null). Fonte unica viva,
    // nao comentario: fatia 1 = db+http; demais namespaces entram fatia a fatia
    // SEM fingir cobertura (R6: member sem tabela mantem o hover simples).
    private static final Map<String, Map<String, List<String>>> SIGNATURES =
            Map.of(
            "db", Map.of(
                    "connect", List.of("connect(String url) -> String",
                            "connect(String url, String user, String pass) -> String"),
                    "query", List.of("query(String url, String sql) -> List<String>",
                            "query(String url, String sql, Object... binds[1..4]) -> List<String>"),
                    "execute", List.of("execute(String url, String sql) -> Int",
                            "execute(String url, String sql, Object... binds[1..4]) -> Int"),
                    "close", List.of("close(String url) -> void"),
                    "transaction", List.of("transaction(callback) -> void")),
            "http", Map.of(
                    "get", List.of("get(String url) -> String", "get(String url, String headers...) -> String"),
                    "delete", List.of("delete(String url) -> String", "delete(String url, String headers...) -> String"),
                    "options", List.of("options(String url) -> String", "options(String url, String headers...) -> String"),
                    "post", List.of("post(String url, String body) -> String", "post(String url, String body, String headers...) -> String"),
                    "put", List.of("put(String url, String body) -> String", "put(String url, String body, String headers...) -> String"),
                    "patch", List.of("patch(String url, String body) -> String", "patch(String url, String body, String headers...) -> String"),
                    "status", List.of("status(String url) -> Int"),
                    "timeout", List.of("timeout(Int ms) -> void"),
                    "retry", List.of("retry(Int count) -> void"),
                    "circuit", List.of("circuit(Int threshold) -> void")));

    /** Overloads gravados do membro (vazio = sem tabela ainda; nunca chute, R6). */
    public static List<String> signaturesOf(String ns, String member) {
        var m = SIGNATURES.get(ns);
        return m == null ? List.of() : m.getOrDefault(member, List.of());
    }

    public static Set<String> namespaces() { return MEMBERS.keySet(); }

    public static boolean isNamespace(String ns) { return MEMBERS.containsKey(ns); }

    /** Membros na ordem declarada no typer (estável p/ UI). */
    public static List<String> membersOf(String ns) {
        return MEMBERS.getOrDefault(ns, List.of());
    }
}
