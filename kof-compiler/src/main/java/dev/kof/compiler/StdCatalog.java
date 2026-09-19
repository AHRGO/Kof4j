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
            // 19/09 LSP-A fatias 1-4: forma MEDIDA no dispatcher real de cada ns e travada
            // comportamento-a-`staticCall` (StdCatalogSignaturesTest). Membro sem tabela
            // mantem hover simples (R6: nunca chute). Reescrito por gerador na fatia 4.
            java.util.Map.ofEntries(
            Map.entry("db", java.util.Map.ofEntries(
                    Map.entry("connect", List.of("connect(String url) -> String", "connect(String url, String user, String pass) -> String")),
                    Map.entry("query", List.of("query(String url, String sql) -> List<String>", "query(String url, String sql, Object... binds[1..4]) -> List<String>")),
                    Map.entry("execute", List.of("execute(String url, String sql) -> Int", "execute(String url, String sql, Object... binds[1..4]) -> Int")),
                    Map.entry("close", List.of("close(String url) -> void")),
                    Map.entry("transaction", List.of("transaction(callback) -> void")))),
            Map.entry("http", java.util.Map.ofEntries(
                    Map.entry("get", List.of("get(String url) -> String", "get(String url, String headers...) -> String")),
                    Map.entry("delete", List.of("delete(String url) -> String", "delete(String url, String headers...) -> String")),
                    Map.entry("options", List.of("options(String url) -> String", "options(String url, String headers...) -> String")),
                    Map.entry("post", List.of("post(String url, String body) -> String", "post(String url, String body, String headers...) -> String")),
                    Map.entry("put", List.of("put(String url, String body) -> String", "put(String url, String body, String headers...) -> String")),
                    Map.entry("patch", List.of("patch(String url, String body) -> String", "patch(String url, String body, String headers...) -> String")),
                    Map.entry("status", List.of("status(String url) -> Int")),
                    Map.entry("timeout", List.of("timeout(Int ms) -> void")),
                    Map.entry("retry", List.of("retry(Int count) -> void")),
                    Map.entry("circuit", List.of("circuit(Int threshold) -> void")))),
            Map.entry("time", java.util.Map.ofEntries(
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
            Map.entry("cache", java.util.Map.ofEntries(
                    Map.entry("get", List.of("get(String key) -> String")),
                    Map.entry("set", List.of("set(String key, String value) -> void", "set(String key, String value, Int ttlSeconds) -> void")),
                    Map.entry("ttl", List.of("ttl(String key) -> Int")),
                    Map.entry("delete", List.of("delete(String key) -> void")),
                    Map.entry("clear", List.of("clear() -> void")))),
            Map.entry("process", java.util.Map.ofEntries(
                    Map.entry("run", List.of("run(String program, String... args) -> Result")),
                    Map.entry("spawn", List.of("spawn(String program, String... args) -> Handle")),
                    Map.entry("exit", List.of("exit(Int code) -> void")))),
            Map.entry("shell", java.util.Map.ofEntries(
                    Map.entry("cmd", List.of("cmd(String program, List<String> args) -> List<String>")),
                    Map.entry("run", List.of("run(String program) -> Result", "run(String program, List<String> args) -> Result")),
                    Map.entry("pipeline", List.of("pipeline(List<List<String>> stages) -> Result")),
                    Map.entry("ok", List.of("ok(result) -> Bool")))),
            Map.entry("net", java.util.Map.ofEntries(
                    Map.entry("scheme", List.of("scheme(String url) -> String")),
                    Map.entry("host", List.of("host(String url) -> String")),
                    Map.entry("port", List.of("port(String url) -> String")),
                    Map.entry("path", List.of("path(String url) -> String")),
                    Map.entry("query", List.of("query(String url) -> String")),
                    Map.entry("fragment", List.of("fragment(String url) -> String")),
                    Map.entry("queryEncode", List.of("queryEncode(String s) -> String")),
                    Map.entry("queryDecode", List.of("queryDecode(String s) -> String")))),
            Map.entry("uuid", java.util.Map.ofEntries(
                    Map.entry("isUuid", List.of("isUuid(String s) -> Bool")),
                    Map.entry("v4", List.of("v4() -> String")),
                    Map.entry("v7", List.of("v7() -> String")))),
            Map.entry("random", java.util.Map.ofEntries(
                    Map.entry("double", List.of("double() -> Double")),
                    Map.entry("boolean", List.of("boolean() -> Bool")),
                    Map.entry("int", List.of("int(Int n) -> Int")),
                    Map.entry("hex", List.of("hex(Int n) -> String")),
                    Map.entry("randomBytesHex", List.of("randomBytesHex(Int n) -> String")),
                    Map.entry("randomInt", List.of("randomInt(Int n) -> Int")),
                    Map.entry("randomBoolean", List.of("randomBoolean() -> Bool")),
                    Map.entry("randomString", List.of("randomString(Int n, String s) -> String")))),
            Map.entry("rng", java.util.Map.ofEntries(
                    Map.entry("seed", List.of("seed(Int n) -> void")),
                    Map.entry("int", List.of("int(Int n) -> Int")),
                    Map.entry("boolean", List.of("boolean() -> Bool")),
                    Map.entry("double", List.of("double() -> Double")),
                    Map.entry("string", List.of("string(Int n, String s) -> String")))),
            Map.entry("encoding", java.util.Map.ofEntries(
                    Map.entry("hexEncode", List.of("hexEncode(String s) -> String")),
                    Map.entry("hexDecode", List.of("hexDecode(String s) -> String")),
                    Map.entry("base64Encode", List.of("base64Encode(String s) -> String")),
                    Map.entry("base64Decode", List.of("base64Decode(String s) -> String")),
                    Map.entry("urlEncode", List.of("urlEncode(String s) -> String")),
                    Map.entry("urlDecode", List.of("urlDecode(String s) -> String")),
                    Map.entry("base64UrlEncode", List.of("base64UrlEncode(String s) -> String")),
                    Map.entry("base64UrlDecode", List.of("base64UrlDecode(String s) -> String")))),
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
                    Map.entry("padRight", List.of("padRight(String s, Int n, String pad) -> String")))),
            Map.entry("math", java.util.Map.ofEntries(
                    Map.entry("abs", List.of("abs(Int n) -> Int")),
                    Map.entry("sign", List.of("sign(Int n) -> Int")),
                    Map.entry("clamp", List.of("clamp(Int v, Int lo, Int hi) -> Int")),
                    Map.entry("min", List.of("min(Int a, Int b) -> Int")),
                    Map.entry("max", List.of("max(Int a, Int b) -> Int")),
                    Map.entry("isEven", List.of("isEven(Int n) -> Bool")),
                    Map.entry("isOdd", List.of("isOdd(Int n) -> Bool")),
                    Map.entry("isPositive", List.of("isPositive(Int n) -> Bool")),
                    Map.entry("isNegative", List.of("isNegative(Int n) -> Bool")),
                    Map.entry("isZero", List.of("isZero(Int n) -> Bool")),
                    Map.entry("sqrt", List.of("sqrt(Double x) -> Double")),
                    Map.entry("lerp", List.of("lerp(Double a, Double b, Double t) -> Double")),
                    Map.entry("percentage", List.of("percentage(Double part, Double whole) -> Double")),
                    Map.entry("isInteger", List.of("isInteger(Double x) -> Bool")),
                    Map.entry("isDecimal", List.of("isDecimal(Double x) -> Bool")),
                    Map.entry("roundTo", List.of("roundTo(Double v, Int decimals) -> Double")),
                    Map.entry("pow", List.of("pow(Double base, Double exp) -> Double")),
                    Map.entry("parseInt", List.of("parseInt(String s) -> Int")),
                    Map.entry("parseLong", List.of("parseLong(String s) -> Long")),
                    Map.entry("parseDouble", List.of("parseDouble(String s) -> Double")),
                    Map.entry("parseIntOrDefault", List.of("parseIntOrDefault(String s, Int d) -> Int")),
                    Map.entry("parseLongOrDefault", List.of("parseLongOrDefault(String s, Long d) -> Long")),
                    Map.entry("parseDoubleOrDefault", List.of("parseDoubleOrDefault(String s, Double d) -> Double")))),
            Map.entry("log", java.util.Map.ofEntries(
                    Map.entry("debug", List.of("debug(String msg) -> void")),
                    Map.entry("info", List.of("info(String msg) -> void")),
                    Map.entry("warn", List.of("warn(String msg) -> void")),
                    Map.entry("error", List.of("error(String msg) -> void")))));

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
