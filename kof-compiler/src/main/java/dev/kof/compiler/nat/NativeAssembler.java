package dev.kof.compiler.nat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * FASE 3 (REFACTOR-500): invocação do as/ld nativo (x86_64 host).
 * Extraído verbatim de NativeBackend (assemble/runCommand/ToolchainMissing);
 * os flags de link (db/mysql/concurrency) viram parâmetros.
 */
public final class NativeAssembler {

    private NativeAssembler() {}

    /** Toolchain ausente (binário não encontrado) — gracioso: mantém asm,
     *  assumeToolchain() pula o teste. NÃO confundir com falha de as/ld. */
    static final class ToolchainMissing extends IOException {
        ToolchainMissing(String m) { super(m); }
    }

    /** #431: `ffiLibs` = as `library()` dos `extern` bound (link-by-use). Um
     *  caminho (contém '/') entra como input posicional do ld; um soname vira
     *  `-l:<nome>` — exatamente o padrão do SQLite (DB001), sem dlopen. */
    static void assemble(Path asmFile, Path binFile, boolean usesDb, boolean usesMysql,
                   boolean usesConcurrency, java.util.Collection<String> ffiLibs,
                   boolean usesPow, boolean freestanding) throws IOException {
        Path objFile = asmFile.resolveSibling(asmFile.getFileName() + ".o");
        boolean bare = freestanding && System.getProperty("os.name", "").toLowerCase().contains("linux");
        Path asmToAssemble = asmFile;
        if (bare) {
            // B-1b: seção por FUNÇÃO no .text (reusa NativeCrossSections, o
            // mesmo passe do cross, lição §445) → o ld com --gc-sections
            // descarta o que o programa não alcança. Com a face (i) do B-1b
            // (panic imprime string, sem dispatcher genérico), o hello deixa
            // de arrastar dtoa/pthread/usleep — o link fecha SEM libc e SEM
            // `--unresolved-symbols=ignore-all`.
            asmToAssemble = asmFile.resolveSibling(asmFile.getFileName() + ".bare.s");
            Files.writeString(asmToAssemble,
                    NativeCrossSections.sectionizeTextFunctions(Files.readString(asmFile)));
        }
        System.err.println("NativeBackend: assembling " + asmFile);
        try {
            runCommand(new String[]{"as", "-o", objFile.toString(), asmToAssemble.toString()}, "as");
        } catch (IOException e) {
            System.err.println("NativeBackend: as failed: " + e.getMessage());
            throw e;
        }
        // B-1: perfil freestanding (x86_64) — link ESTÁTICO, sem
        // `-dynamic-linker` e sem `-lc`; o binário só fala com o SO pela
        // costura kof_plat_* (B-0). As capacidades libc-dependentes já foram
        // recusadas em NativeBackend.assemble (NATIVE003). B-1b: as refs libc
        // de funções não-alcançadas morrem no `--gc-sections`.
        if (bare) {
            try {
                runCommand(new String[]{"ld", "-o", binFile.toString(), objFile.toString(),
                        "--gc-sections", "-e", "_start"}, "ld");
            } catch (IOException e) {
                // R6: no perfil freestanding nenhuma capacidade libc entra —
                // se sobrou símbolo libc (ex.: `println(Double)` alcança
                // snprintf/strtod do dtoa), a recusa é NOMEADA, não um
                // "undefined reference" cru do ld.
                if (String.valueOf(e.getMessage()).contains("undefined reference")) {
                    throw new IOException("NATIVE003: perfil freestanding nao suporta libc — "
                            + "o programa usa uma capacidade libc (float-print/db/concurrency/pow/ffi); "
                            + "use o perfil host ou remova a dependencia. Detalhe: " + e.getMessage());
                }
                throw e;
            }
            Files.deleteIfExists(objFile);
            Files.deleteIfExists(asmToAssemble);
            if (System.getenv("KOF_KEEP_ASM") == null) Files.deleteIfExists(asmFile);
            binFile.toFile().setExecutable(true);
            return;
        }
        // Native always needs dynamic linker + libc now (printf for float, db optionally)
        // to keep single codegen path; plain integer programs still work via ld+ld.so.
        boolean needsDynamic = true;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (needsDynamic && os.contains("linux")) {
            java.util.List<String> cmdL = new java.util.ArrayList<>(java.util.Arrays.asList(
                    "ld", "-o", binFile.toString(), objFile.toString(),
                    "-dynamic-linker", "/lib64/ld-linux-x86-64.so.2", "-lc"));
            if (usesDb) {
                cmdL.add("-l:libsqlite3.so.0");
                if (usesMysql) cmdL.add("-l:libmariadb.so.3");
            }
            if (usesConcurrency) cmdL.add("-l:libpthread.so.0");
            // R2 fatia 1 (20/09): pow → libm só POR USO (decisão 7a mantida;
            // o EAGER "sempre ligado" caducou — o shim `call pow` do monolito
            // é FRACO desde aqui: `.weak pow` em RuntimeMath, então linka sem
            // libm e o simbolo nunca e alcancado quando usesPow=false, porque
            // o unicos call-sites nascem do scan usesPow no NativeBackend).
            // Recusa em riscv/aarch = KofMath.supportedOn (MATH001) — la o
            // link e estatico sem libc. Prova: LinkByUseTest (readelf medido).
            if (usesPow) cmdL.add("-lm");
            // #431: as libs dos `extern` bound entram no link (posicional se é
            // caminho, `-l:` se é soname). Arquivo ausente → erro honesto do ld
            // (nunca um binário que resolve em runtime pra faltar).
            for (String lib : ffiLibs) {
                cmdL.add(lib.indexOf('/') >= 0 ? lib : "-l:" + lib);
            }
            runCommand(cmdL.toArray(new String[0]), "ld");
        } else {
            if (usesDb) {
                String os2 = System.getProperty("os.name", "").toLowerCase();
                if (os2.contains("linux")) {
                    String[] extra = usesMysql
                            ? new String[]{"-l:libsqlite3.so.0", "-l:libmariadb.so.3"}
                            : new String[]{"-l:libsqlite3.so.0"};
                    String[] cmd = new String[7 + extra.length];
                    cmd[0] = "ld"; cmd[1] = "-o"; cmd[2] = binFile.toString(); cmd[3] = objFile.toString();
                    cmd[4] = "-dynamic-linker"; cmd[5] = "/lib64/ld-linux-x86-64.so.2"; cmd[6] = "-lc";
                    System.arraycopy(extra, 0, cmd, 7, extra.length);
                    runCommand(cmd, "ld");
                } else {
                    runCommand(new String[]{"ld", "-o", binFile.toString(), objFile.toString()}, "ld");
                }
            } else {
                runCommand(new String[]{"ld", "-o", binFile.toString(), objFile.toString()}, "ld");
            }
        }
        Files.deleteIfExists(objFile);
        if (System.getenv("KOF_KEEP_ASM") == null) Files.deleteIfExists(asmFile);
        binFile.toFile().setExecutable(true);
    }

    static void runCommand(String[] cmd, String name) throws IOException {
        Process p;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            p = pb.start();
        } catch (IOException e) {
            throw new ToolchainMissing(name + " not available: " + e.getMessage());
        }
        try {
            String output = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            if (p.exitValue() != 0) {
                throw new IOException(name + " failed (exit " + p.exitValue() + "): " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(name + " interrupted");
        }
    }
}
