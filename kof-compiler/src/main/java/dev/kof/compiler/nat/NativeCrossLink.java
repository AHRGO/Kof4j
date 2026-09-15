package dev.kof.compiler.nat;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Link dinâmico SOB DEMANDA (link-by-use) dos alvos cross riscv64/aarch64.
 *
 * <p>Diretriz da mantenedora (15/09): "liga dinamicamente". O x86_64 já liga
 * dinamicamente (`ld -dynamic-linker /lib64/ld-linux-x86-64.so.2 -lc`); o cross
 * nasceu ESTÁTICO (asm puro, sem libc) — a decisão do §2.3 do
 * `native-multiarch.md` sempre foi dinâmica, mas a implementação divergiu.
 *
 * <p>Modelo escolhido: <b>link-by-use</b>. O binário continua ESTÁTICO enquanto
 * o runtime (já PODADO) não referenciar nenhum símbolo de libc; no momento em
 * que alguma capacidade libc-dependente entra (double→string/FLT001 via
 * snprintf/strtod, kof.db via .so, etc.), o link passa a dinâmico com
 * `-dynamic-linker <loader> --sysroot <sysroot> -lc`. Isso preserva a
 * portabilidade dos binários atuais (nada muda sem um consumidor libc) e
 * destrava os gaps cross SOB DEMANDA.
 *
 * <p>Sysroot: a libc-cross não está em `/usr/<arch>-linux-gnu` neste host (foi
 * extraída em `/tmp/opencode/x`). Resolução em ordem:
 * <ol>
 *   <li>`KOF_CROSS_SYSROOT` (env) — explícito, vence sempre;</li>
 *   <li>instalação de sistema (`/usr/<arch>-linux-gnu/lib/<loader>` presente)
 *       — sem `--sysroot` (caminhos default do ld); é o caso do CI com
 *       `apt-get install libc6-<arch>-cross`;</li>
 *   <li>`/tmp/opencode/x` (o toolchain documentado deste host).</li>
 * </ol>
 * Sem nenhum sysroot → mantém ESTÁTICO + stderr (R6: nunca link quebrado
 * silencioso).
 */
final class NativeCrossLink {

    private NativeCrossLink() {}

    /** Símbolos de libc cuja presença num `call` torna o link dinâmico. Lista
     *  CURADA (não heurística): cresce só quando um consumidor novo entra. */
    static final Set<String> LIBC_SYMBOLS = Set.of(
            "snprintf", "strtod", "printf", "malloc", "calloc", "realloc", "free",
            "pow", "sqrt", "fmod", "dlopen", "dlsym", "dlclose",
            "fopen", "fclose", "fwrite", "fread", "memcpy", "memset",
            "strlen", "strcmp", "strncmp", "open", "read", "write");

    /** true se o texto asm (pós-poda) chama algum símbolo de libc. */
    static boolean needsLibc(String asmText) {
        for (String line : asmText.split("\n", -1)) {
            String t = line.strip();
            if (t.startsWith("#")) continue;
            int hash = t.indexOf('#');
            if (hash > 0) t = t.substring(0, hash).stripTrailing();
            if (t.startsWith("call ")) {
                String sym = t.substring(5).strip();
                if (LIBC_SYMBOLS.contains(sym)) return true;
            }
        }
        return false;
    }

    static String loaderBase(String arch) {
        return switch (arch) {
            case "riscv64" -> "ld-linux-riscv64-lp64d.so.1";
            case "aarch64" -> "ld-linux-aarch64.so.1";
            default -> throw new IllegalArgumentException("arch cross: " + arch);
        };
    }

    /** Caminho do loader COMO VISTO PELO BINÁRIO EM TEMPO DE EXECUÇÃO (layout
     *  Debian: `/lib/<loader>`). É o arg de `-dynamic-linker`. */
    static String ldPathFor(String arch) {
        return "/lib/" + loaderBase(arch);
    }

    /** Prefixo para `QEMU_LD_PREFIX` ao rodar o binário dinâmico: o loader
     *  resolve-se em {@code <prefix>/lib/<loader>} (layout Debian). É a pasta
     *  {@code <arch>-linux-gnu} do sysroot, ou {@code null} se não há libc. */
    static String qemuPrefixFor(String arch) {
        String sysroot = sysrootFor(arch);
        if (sysroot == null) return null;
        return sysroot + "/usr/" + arch + "-linux-gnu";
    }
    /**
     * Sysroot a usar, ou {@code ""} para caminhos de sistema, ou {@code null}
     * se não há libc-cross. Ver a ordem no cabeçalho.
     */
    static String sysrootFor(String arch) {
        String env = System.getenv("KOF_CROSS_SYSROOT");
        if (env != null && !env.isBlank()) return env;
        String loader = loaderBase(arch);
        if (new File("/usr/" + arch + "-linux-gnu/lib/" + loader).exists()) return "";
        if (new File("/tmp/opencode/x/usr/" + arch + "-linux-gnu/lib/" + loader).exists()) return "/tmp/opencode/x";
        return null;
    }

    /** Monta os args do ld. Estático → só os args de hoje + gc-sections.
     *  Dinâmico → + `--dynamic-linker` + `--sysroot` (se houver) + `-lc`. */
    static String[] ldArgs(String ld, Path binFile, Path objFile, String arch,
                           boolean dynamic, String sysroot) {
        List<String> a = new ArrayList<>();
        a.add(ld);
        if (arch.equals("riscv64")) a.add("--no-relax");
        a.add("--gc-sections");
        if (!dynamic) {
            a.add("-o");
            a.add(binFile.toString());
            a.add(objFile.toString());
            return a.toArray(new String[0]);
        }
        // `--allow-shlib-undefined`: a libc.so do sysroot referencia símbolos
        // GLIBC_PRIVATE do loader; sem isto o ld aborta (o runtime resolve-os).
        a.add("--allow-shlib-undefined");
        if (sysroot != null && !sysroot.isEmpty()) a.add("--sysroot=" + sysroot);
        a.add("-dynamic-linker");
        a.add(ldPathFor(arch));
        a.add("-o");
        a.add(binFile.toString());
        a.add(objFile.toString());
        a.add("-lc");
        return a.toArray(new String[0]);
    }
}
