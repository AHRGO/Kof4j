#!/usr/bin/env python3
"""LSP-A: regenera o literal SIGNATURES de StdCatalog.java (fonte unica das
assinaturas de hover). A tabela e um artefato de codigo: cada forma foi
MEDIDA no dispatcher real do ns e travada comportamento-a-`staticCall` em
`StdCatalogSignaturesTest`. Membro sem tabela mantem hover simples (R6).

Uso: python3 scripts/gen_signatures.py check|write
"""
import io
import re
import sys

CAT = "kof-compiler/src/main/java/dev/kof/compiler/StdCatalog.java"

# Blocos por fatia LSP-A (1-4 vivem EXTRAIDOS do arquivo; 5+ declarados aqui)
EXTRA_FATIAS = {
    "orm": [
        ("create", ["create(String entity) -> Bool"]),
        ("save", ["save(String entity, Object row) -> Object"]),
        ("find", ["find(String entity, Object id) -> Object"]),
        ("all", ["all(String entity) -> List"]),
        ("delete", ["delete(String entity, Object id) -> Bool"]),
        ("count", ["count(String entity) -> Long",
                   "count(String entity, String where, Object value) -> Long"]),
        ("deleteAll", ["deleteAll(String entity) -> Bool"]),
        ("where", ["where(String entity, String cond, Object value) -> List",
                   "where(String entity, String col, String op, Object value) -> List"]),
        ("saveAll", ["saveAll(String entity, List rows) -> Bool"]),
        ("page", ["page(String entity, Object offset, Object limit) -> List"]),
        ("migrate", ["migrate(String url, String user, String pass) -> Bool"]),
    ],
    "config": [
        ("get", ["get(String key) -> String"]),
        ("env", ["env(String key) -> String"]),
        ("has", ["has(String key) -> Bool"]),
        ("str", ["str(String key, String d) -> String"]),
        ("int", ["int(String key, Int d) -> Int"]),
        ("long", ["long(String key, Long d) -> Long"]),
        ("bool", ["bool(String key, Bool d) -> Bool"]),
        ("required", ["required(String key) -> String"]),
    ],
    "gpu": [
        ("available", ["available() -> Bool"]),
        ("failReason", ["failReason() -> String"]),
        ("dispatchMatmul", ["dispatchMatmul(Array<Int> a, Array<Int> b, Array<Int> c, Int m, Int n, Int k) -> Int"]),
        ("dispatchMatmul64", ["dispatchMatmul64(Array<Long> a, Array<Long> b, Array<Long> c, Int m, Int n, Int k) -> Int"]),
        ("mvSetShape", ["mvSetShape(Int rows, Int cols) -> Int"]),
        ("mvLoadW", ["mvLoadW(Array<Long> w, Int rows, Int cols) -> Int"]),
        ("mvMatvec", ["mvMatvec(Array<Long> w, Array<Long> x, Int rows, Int cols) -> Int"]),
        ("mvPutW", ["mvPutW(Int slot, Array<Long> w, Int rows, Int cols) -> Int"]),
        ("mvRun", ["mvRun(Int slot, Array<Long> x, Array<Long> out, Int rows, Int cols, Long reserved) -> Int"]),
        ("mvPut32", ["mvPut32(Int slot, Array<Int> w, Int rows, Int cols) -> Int"]),
        ("mvRun32", ["mvRun32(Int slot, Array<Long> x, Array<Long> out, Int rows, Int cols, Long reserved) -> Int"]),
        ("mvPutSp", ["mvPutSp(Int slot, Array<Int> w, Array<Int> sp, Int rows, Int cols) -> Int"]),
        ("mvRunSp", ["mvRunSp(Int slot, Array<Long> x, Array<Long> out, Int rows, Int cols, Long reserved) -> Int"]),
    ],
    "mq": [
        ("publish", ["publish(String topic, Object payload) -> void"]),
        ("subscribe", ["subscribe(String topic, Object handler) -> void"]),
        ("unsubscribe", ["unsubscribe(String topic, Object handler) -> void"]),
        ("queue", ["queue() -> String"]),
        ("push", ["push(String queue, Object value) -> void"]),
        ("pop", ["pop(String queue) -> Object"]),
        ("queueSize", ["queueSize(String queue) -> Int"]),
    ],
    "validation": [
        ("required", ["required(String s) -> Bool"]),
        ("notBlank", ["notBlank(String s) -> Bool"]),
        ("minLength", ["minLength(String s, Int n) -> Bool"]),
        ("maxLength", ["maxLength(String s, Int n) -> Bool"]),
        ("lengthBetween", ["lengthBetween(String s, Int lo, Int hi) -> Bool"]),
        ("isEmail", ["isEmail(String s) -> Bool"]),
        ("isUrl", ["isUrl(String s) -> Bool"]),
        ("matches", ["matches(String s, String regex) -> Bool"]),
        ("isInt", ["isInt(String s) -> Bool"]),
        ("isLong", ["isLong(String s) -> Bool"]),
        ("inRange", ["inRange(Int v, Int lo, Int hi) -> Bool"]),
        ("min", ["min(Int v, Int min) -> Bool"]),
        ("max", ["max(Int v, Int max) -> Bool"]),
        ("formatCpf", ["formatCpf(String s) -> String"]),
        ("formatCep", ["formatCep(String s) -> String"]),
        ("formatCnpj", ["formatCnpj(String s) -> String"]),
        ("isCpf", ["isCpf(String s) -> Bool"]),
        ("isCnpj", ["isCnpj(String s) -> Bool"]),
        ("isCep", ["isCep(String s) -> Bool"]),
        ("isPis", ["isPis(String s) -> Bool"]),
        ("isNis", ["isNis(String s) -> Bool"]),
        ("isIpv4", ["isIpv4(String s) -> Bool"]),
        ("isMac", ["isMac(String s) -> Bool"]),
        ("isPort", ["isPort(Int p) -> Bool"]),
        ("isCreditCard", ["isCreditCard(String s) -> Bool"]),
        ("isIpv6", ["isIpv6(String s) -> Bool"]),
        ("isDomain", ["isDomain(String s) -> Bool"]),
    ],
    "observability": [
        ("health", ["health() -> String"]),
        ("readiness", ["readiness() -> Bool"]),
        ("liveness", ["liveness() -> Bool"]),
        ("counter", ["counter(String name) -> Int"]),
        ("increment", ["increment(String name, Int by) -> Int"]),
        ("gauge", ["gauge(String name, Int value) -> void"]),
        ("histogram", ["histogram(String name, Int value) -> void"]),
        ("metrics", ["metrics() -> String"]),
        ("requestId", ["requestId() -> String"]),
        ("correlationId", ["correlationId() -> String"]),
        ("traceId", ["traceId() -> String"]),
        ("spanId", ["spanId() -> String"]),
        ("spanStart", ["spanStart(String name) -> String"]),
        ("spanEnd", ["spanEnd(String id) -> String"]),
        ("exportSpans", ["exportSpans() -> String"]),
    ],
    "tetris": [("run", ["run() -> void"])],
}

HEADER = (
    "    private static final Map<String, Map<String, List<String>>> SIGNATURES =\n"
    "            // 19/09 LSP-A fatias 1-5: forma MEDIDA no dispatcher real de cada\n"
    "            // ns e travada comportamento-a-`staticCall` (StdCatalogSignaturesTest).\n"
    "            // Membro sem tabela mantem hover simples (R6: nunca chute).\n"
    "            // ARTEFATO DO GERADOR scripts/gen_signatures.py \u2014 nao editar a m\u00e3o.\n"
    "            java.util.Map.ofEntries(\n")


def match_close(txt, start):
    d, k, inq, esc = 0, start, False, False
    while k < len(txt):
        c = txt[k]
        if esc:
            esc = False
        elif c == "\\":
            esc = True
        elif c == '"':
            inq = not inq
        elif not inq:
            if c == "(":
                d += 1
            elif c == ")":
                d -= 1
                if d == 0:
                    return k
        k += 1
    raise ValueError("parencsese nao fechado")


def extract(s):
    i0 = s.index("private static final Map<String, Map<String, List<String>>> SIGNATURES")
    j0 = s.index("java.util.Map.ofEntries(", i0)
    end = match_close(s, j0)
    body = s[j0 + 1:end]
    groups, pos = [], 0
    while True:
        m = re.compile(r'Map\.entry\("(\w+)", java\.util\.Map\.ofEntries\(').search(body, pos)
        if not m:
            break
        op = body.index("(", m.end() - 1)
        cl = match_close(body, op)
        inner, items, q = body[op + 1:cl], [], 0
        while True:
            mm = re.compile(r'Map\.entry\("(\w+)", List\.of\(').search(inner, q)
            if not mm:
                break
            name = mm.group(1)
            op2 = inner.index("(", mm.end() - 1)
            cl2 = match_close(inner, op2)
            sigs = re.findall(r'"((?:[^"\\]|\\.)*)"', inner[op2 + 1:cl2])
            items.append((name, sigs))
            q = cl2 + 1
        groups.append((m.group(1), items))
        pos = cl + 1
    return groups


def block(ns, items):
    ent = ",\n                    ".join(
        'Map.entry("%s", List.of(%s))' % (k, ", ".join('"%s"' % x for x in sigs))
        for k, sigs in items)
    return '            Map.entry("%s", java.util.Map.ofEntries(\n                    %s))' % (ns, ent)


def render(groups):
    return HEADER + ",\n".join(block(ns, it) for ns, it in groups) + ");\n\n"


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "check"
    s = io.open(CAT, encoding="utf-8").read()
    groups = extract(s)
    have = {ns for ns, _ in groups}
    for ns in ["orm", "config", "gpu", "mq", "validation", "observability", "tetris"]:
        if ns not in have:
            groups.append((ns, EXTRA_FATIAS[ns]))
    out = render(groups)
    i0 = s.index("    private static final Map<String, Map<String, List<String>>> SIGNATURES")
    i1 = s.index("    /** Overloads gravados", i0)
    s2 = s[:i0] + out + s[i1:]
    d = 0
    inq = esc = False
    for ch in s2:
        if esc:
            esc = False
        elif ch == "\\":
            esc = True
        elif ch == '"':
            inq = not inq
        elif not inq:
            if ch == "(":
                d += 1
            elif ch == ")":
                d -= 1
    assert d == 0, "profundidade %d — gerador abortado" % d
    nm = sum(len(g) for _, g in groups)
    ns_ = sum(len(x) for _, g in groups for _, x in g)
    print("%d ns, %d membros, %d formas" % (len(groups), nm, ns_))
    if mode == "write" and s2 != s:
        io.open(CAT, "w", encoding="utf-8").write(s2)
        print("gravado")
    else:
        print("inalterado" if s2 == s else "modo check (sem escrita)")


if __name__ == "__main__":
    main()
