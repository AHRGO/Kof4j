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
            java.util.Map.ofEntries(
            Map.entry("db", Map.of(
                    "connect", List.of("connect(String url) -> String",
                            "connect(String url, String user, String pass) -> String"),
                    "query", List.of("query(String url, String sql) -> List<String>",
                            "query(String url, String sql, Object... binds[1..4]) -> List<String>"),
                    "execute", List.of("execute(String url, String sql) -> Int",
                            "execute(String url, String sql, Object... binds[1..4]) -> Int"),
                    "close", List.of("close(String url) -> void"),
                    "transaction", List.of("transaction(callback) -> void"))),
            Map.entry("http", Map.of(
                    "get", List.of("get(String url) -> String", "get(String url, String headers...) -> String"),
                    "delete", List.of("delete(String url) -> String", "delete(String url, String headers...) -> String"),
                    "options", List.of("options(String url) -> String", "options(String url, String headers...) -> String"),
                    "post", List.of("post(String url, String body) -> String", "post(String url, String body, String headers...) -> String"),
                    "put", List.of("put(String url, String body) -> String", "put(String url, String body, String headers...) -> String"),
                    "patch", List.of("patch(String url, String body) -> String", "patch(String url, String body, String headers...) -> String"),
                    "status", List.of("status(String url) -> Int"),
                    "timeout", List.of("timeout(Int ms) -> void"),
                    "retry", List.of("retry(Int count) -> void"),
                    "circuit", List.of("circuit(Int threshold) -> void"))),
            Map.entry("time", Map.ofEntries(
                    Map.entry("sleep", List.of("sleep(Int ms) -> void")),
                    Map.entry("now", List.of("now() -> Long")),
                    Map.entry("collect", List.of("collect() -> void")),
                    Map.entry("interval", List.of("interval(Int ms, callback) -> String")),
                    Map.entry("cancel", List.of("cancel(String id) -> void")),
                    Map.entry("isLeapYear", List.of("isLeapYear(Int year) -> Bool")),
                    Map.entry("daysInMonth", List.of("daysInMonth(Int year, Int month) -> Int")),
                    Map.entry("dayOfWeek", List.of("dayOfWeek(Int y, Int m, Int d) -> Int")),
                    Map.entry("isWeekend", List.of("isWeekend(Int y, Int m, Int d) -> Bool")),
                    Map.entry("daysBetween", List.of("daysBetween(Int y1, Int m1, Int d1, Int y2, Int m2, Int d2) -> Int")),
                    Map.entry("isToday", List.of("isToday(Int y, Int m, Int d) -> Bool")),
                    Map.entry("addDays", List.of("addDays(String iso, Int days) -> String")),
                    Map.entry("diffDays", List.of("diffDays(String isoA, String isoB) -> Int")),
                    Map.entry("todayIso", List.of("todayIso() -> String")),
                    Map.entry("formatDateIso", List.of("formatDateIso(Int y, Int m, Int d) -> String")),
                    Map.entry("parseDateIso", List.of("parseDateIso(String iso) -> Int")),
                    Map.entry("tzOffsetSeconds", List.of("tzOffsetSeconds() -> Int")),
                    Map.entry("hoursBetween", List.of("hoursBetween(Int y1, Int m1, Int d1, Int h1, Int y2, Int m2, Int d2, Int h2) -> Int")))),
            Map.entry("cache", Map.of(
                    "get", List.of("get(String key) -> String"),
                    "set", List.of("set(String key, String value) -> void",
                            "set(String key, String value, Int ttlSeconds) -> void"),
                    "ttl", List.of("ttl(String key) -> Int"),
                    "delete", List.of("delete(String key) -> void"),
                    "clear", List.of("clear() -> void"))),
            Map.entry("process", Map.of(
                    "run", List.of("run(String program, String... args) -> Result"),
                    "spawn", List.of("spawn(String program, String... args) -> Handle"),
                    "exit", List.of("exit(Int code) -> void"))),
            Map.entry("shell", Map.of(
                    "cmd", List.of("cmd(String program, List<String> args) -> List<String>"),
                    "run", List.of("run(String program) -> Result", "run(String program, List<String> args) -> Result"),
                    "pipeline", List.of("pipeline(List<List<String>> stages) -> Result"),
                    "ok", List.of("ok(result) -> Bool"))),
            Map.entry("net", Map.of(
                    "scheme", List.of("scheme(String url) -> String"),
                    "host", List.of("host(String url) -> String"),
                    "port", List.of("port(String url) -> String"),
                    "path", List.of("path(String url) -> String"),
                    "query", List.of("query(String url) -> String"),
                    "fragment", List.of("fragment(String url) -> String"),
                    "queryEncode", List.of("queryEncode(String s) -> String"),
                    "queryDecode", List.of("queryDecode(String s) -> String"))),
            Map.entry("uuid", Map.of(
                    "isUuid", List.of("isUuid(String s) -> Bool"),
                    "v4", List.of("v4() -> String"),
                    "v7", List.of("v7() -> String"))),
            Map.entry("random", Map.of(
                    "double", List.of("double() -> Double"),
                    "boolean", List.of("boolean() -> Bool"),
                    "int", List.of("int(Int n) -> Int"),
                    "hex", List.of("hex(Int n) -> String"),
                    "randomBytesHex", List.of("randomBytesHex(Int n) -> String"),
                    "randomInt", List.of("randomInt(Int n) -> Int"),
                    "randomBoolean", List.of("randomBoolean() -> Bool"),
                    "randomString", List.of("randomString(Int n, String s) -> String"))),
            Map.entry("rng", Map.of(
                    "seed", List.of("seed(Int n) -> void"),
                    "int", List.of("int(Int n) -> Int"),
                    "boolean", List.of("boolean() -> Bool"),
                    "double", List.of("double() -> Double"),
                    "string", List.of("string(Int n, String s) -> String"))),
            Map.entry("encoding", Map.of(
                    "hexEncode", List.of("hexEncode(String s) -> String"),
                    "hexDecode", List.of("hexDecode(String s) -> String"),
                    "base64Encode", List.of("base64Encode(String s) -> String"),
                    "base64Decode", List.of("base64Decode(String s) -> String"),
                    "urlEncode", List.of("urlEncode(String s) -> String"),
                    "urlDecode", List.of("urlDecode(String s) -> String"),
                    "base64UrlEncode", List.of("base64UrlEncode(String s) -> String"),
                    "base64UrlDecode", List.of("base64UrlDecode(String s) -> String"))),
            Map.entry("strings", java.util.Map.ofEntries(
                    Map.entry("isAlpha", List.of("isAlpha(String s) -> Bool")),
                    Map.entry("isNumeric", List.of("isNumeric(String s) -> Bool")),
                    Map.entry("isAlphaNumeric", List.of("isAlphaNumeric(String s) -> Bool")),
                    Map.entry("isAscii", List.of("isAscii(String s) -> Bool")),
                    Map.entry("isUpperCase", List.of("isUpperCase(String s) -> Bool")),
                    Map.entry("isLowerCase", List.of("isLowerCase(String s) -> Bool")),
                    Map.entry("count", List.of("count(String s, String needle) -> Int")),
                    Map.entry("capitalize", List.of("capitalize(String s) -> String")),
                    Map.entry("uncapitalize", List.of("uncapitalize(String s) -> String")),
                    Map.entry("reverse", List.of("reverse(String s) -> String")),
                    Map.entry("toCamelCase", List.of("toCamelCase(String s) -> String")),
                    Map.entry("toPascalCase", List.of("toPascalCase(String s) -> String")),
                    Map.entry("toSnakeCase", List.of("toSnakeCase(String s) -> String")),
                    Map.entry("toKebabCase", List.of("toKebabCase(String s) -> String")),
                    Map.entry("slugify", List.of("slugify(String s) -> String")),
                    Map.entry("escapeHtml", List.of("escapeHtml(String s) -> String")),
                    Map.entry("unescapeHtml", List.of("unescapeHtml(String s) -> String")),
                    Map.entry("escapeJson", List.of("escapeJson(String s) -> String")),
                    Map.entry("removeWhitespace", List.of("removeWhitespace(String s) -> String")),
                    Map.entry("normalizeWhitespace", List.of("normalizeWhitespace(String s) -> String")),
                    Map.entry("dedent", List.of("dedent(String s) -> String")),
                    Map.entry("repeat", List.of("repeat(String s, Int n) -> String")),
                    Map.entry("truncate", List.of("truncate(String s, Int n) -> String")),
                    Map.entry("indent", List.of("indent(String s, Int n) -> String")),
                    Map.entry("padLeft", List.of("padLeft(String s, Int n, String pad) -> String")),
                    Map.entry("padRight", List.of("padRight(String s, Int n, String pad) -> String")))));;

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
