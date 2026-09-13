package dev.kof.compiler.js;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Apoio de teste para JS escrito à mão que importa do runtime.
 *
 * O runtime é podado pela lista de import do módulo COMPILADO (issue #97,
 * T2/S-6). Uma sonda colada no artefato depois da compilação traz imports que
 * o compilador nunca viu — sem declará-los, o export some e a sonda quebra com
 * "does not provide an export named". Declarar aqui usa o mesmo caminho de
 * união do build multi-módulo: o runtime é reescrito com as sementes da sonda
 * somadas às do programa, e o cabeçalho `// kof:seeds` registra a inclusão.
 *
 * Mora no pacote de teste de propósito: dá acesso ao writer sem abrir API
 * pública só para teste.
 */
public final class JsRuntimeTestSupport {

    private static final Pattern RUNTIME_IMPORT = Pattern.compile(
            "import\\s*\\{([^}]*)\\}\\s*from\\s*['\"]\\./kof-runtime(-io)?\\.mjs['\"]");

    private JsRuntimeTestSupport() {}

    /** Reescreve o runtime de {@code outDir} incluindo o que {@code js} importa dele. */
    public static void includeImportsOf(Path outDir, String js) throws IOException {
        Set<String> core = new LinkedHashSet<>();
        Set<String> io = new LinkedHashSet<>();
        Matcher m = RUNTIME_IMPORT.matcher(js);
        while (m.find()) {
            Collection<String> target = m.group(2) == null ? core : io;
            for (String name : m.group(1).split(",")) {
                String n = name.trim();
                if (!n.isEmpty()) target.add(n);
            }
        }
        if (core.isEmpty() && io.isEmpty()) return;
        new JsArtifactWriter().writeRuntime(outDir, List.copyOf(core), List.copyOf(io));
    }
}
