package dev.kof.compiler;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * X10 fatia 1 (fila X, IMPLEMENTATION-UNIVERSAL-PLATFORM) — catálogo de
 * membros dos namespaces stdlib roteado por {@link KofStd#staticMethod}
 * (math, strings, encoding, net, uuid, random, rng — fatia 1; time, http, db,
 * cache, process e os 6 namespaces de segurança — fatia 2). Alimenta o completion
 * domain-aware do LSP SEM parser paralelo: os nomes são uma transcrição
 * dos `case` dos próprios typers, e o `StdCatalogTest` trava as duas pontas
 * contra a fonte real — lista ≠ case-literals do `switch (name)` ou
 * dispatch do KofStd ≠ chaves do catálogo ⇒ vermelho (padrão
 * RuntimeSlices: transcription protegida por teste, nunca confiança).
 *
 * <p>Escopo honesto: json/ui/ffi/web-server etc. têm roteamento ainda
 * próprio (receiver-typed) — faces seguintes do X10 (R6: não fingir
 * cobertura total).
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
        m.putAll(KofSecurity.functions());
        MEMBERS = java.util.Collections.unmodifiableMap(m);
    }

    public static Set<String> namespaces() { return MEMBERS.keySet(); }

    public static boolean isNamespace(String ns) { return MEMBERS.containsKey(ns); }

    /** Membros na ordem declarada no typer (estável p/ UI). */
    public static List<String> membersOf(String ns) {
        return MEMBERS.getOrDefault(ns, List.of());
    }
}
