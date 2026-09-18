package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X10 fatia 1 — trava do StdCatalog contra a FONTE REAL dos typers (padrão
 * RuntimeSlices): (a) para cada um dos 7 namespaces, a lista publicada é
 * EXATAMENTE o conjunto de case-literals do `switch (name)` do staticMethod;
 * (b) o dispatch do KofStd cobre exatamente as chaves do catálogo;
 * (c) cada membro do catálogo resolve para um StdCall não-nulo em alguma
 * forma de aridade/tipo plausível (o catálogo não inventa nomes).
 */
class StdCatalogTest {

    private static String methodBody(String src, String signature) {
        int i = src.indexOf(signature);
        assertTrue(i >= 0, "assinatura ausente: " + signature);
        int open = src.indexOf('{', i);
        int depth = 0, k = open;
        while (true) {
            char c = src.charAt(k);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) break;
            }
            k++;
        }
        return src.substring(open, k + 1);
    }

    private static Set<String> caseLiterals(String body) {
        int sw = body.indexOf("switch (name)");
        assertTrue(sw >= 0, "sem switch (name): " + body.substring(0, Math.min(80, body.length())));
        String blk = methodBody(body.substring(sw), "");
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("case \"(\\w+)\"").matcher(blk);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private static String source(String cls) throws IOException {
        return Files.readString(Path.of("src/main/java/dev/kof/compiler/" + cls + ".java"));
    }

    @Test
    void catalogMatchesTyperCaseLiterals() throws Exception {
        record Entry(String ns, String cls, List<String> fns) {}
        List<Entry> entries = List.of(
                new Entry("math", "KofMath", KofMath.functions()),
                new Entry("strings", "KofStrings", KofStrings.functions()),
                new Entry("encoding", "KofEncoding", KofEncoding.functions()),
                new Entry("net", "KofNet", KofNet.functions()),
                new Entry("uuid", "KofUuid", KofUuid.functions()),
                new Entry("random", "KofRandom", KofRandom.functions()),
                new Entry("rng", "KofRng", KofRng.functions()));
        for (Entry e : entries) {
            Set<String> inSource = caseLiterals(methodBody(source(e.cls()),
                    "staticMethod(String namespace"));
            assertEquals(inSource, new LinkedHashSet<>(e.fns()),
                    e.ns() + ": catalog != case-literals do " + e.cls());
            assertEquals(e.fns(), StdCatalog.membersOf(e.ns()), e.ns() + ": membros");
        }
    }

    @Test
    void kofStdDispatchCoversExactlyTheCatalog() throws Exception {
        String std = source("KofStd");
        Set<String> dispatched = new LinkedHashSet<>();
        Matcher m = Pattern.compile("Kof(\\w+)\\.staticMethod\\(").matcher(std);
        while (m.find()) dispatched.add("Kof" + m.group(1));
        Set<String> catalogClasses = new LinkedHashSet<>(List.of(
                "KofMath", "KofStrings", "KofEncoding", "KofNet",
                "KofUuid", "KofRandom", "KofRng"));
        assertEquals(dispatched, catalogClasses,
                "dispatch do KofStd mudou sem atualizar o catálogo");
        assertEquals(7, StdCatalog.namespaces().size(), StdCatalog.namespaces().toString());
        for (String ns : List.of("math", "strings", "encoding", "net", "uuid", "random", "rng")) {
            assertTrue(StdCatalog.isNamespace(ns), ns);
        }
    }

    @Test
    void everyCatalogMemberResolvesInRealTyper() {
        List<Type> prim = List.of();
        List<List<Type>> shapes = new ArrayList<>();
        Type[] ts = {Type.PrimitiveType.INT, BuiltinTypes.STRING, Type.PrimitiveType.BOOL,
                Type.PrimitiveType.DOUBLE, Type.PrimitiveType.LONG, Type.PrimitiveType.CHAR};
        shapes.add(prim);
        for (Type a : ts) {
            shapes.add(List.of(a));
            for (Type b : ts) {
                shapes.add(List.of(a, b));
                for (Type c : ts) {
                    shapes.add(List.of(a, b, c));
                }
            }
        }
        for (String ns : StdCatalog.namespaces()) {
            for (String fn : StdCatalog.membersOf(ns)) {
                boolean ok = false;
                for (List<Type> sh : shapes) {
                    if (KofStd.staticMethod(ns, fn, sh) != null) { ok = true; break; }
                }
                assertTrue(ok, ns + "." + fn + " não resolve no typer — catálogo inventado?");
            }
        }
    }
}
