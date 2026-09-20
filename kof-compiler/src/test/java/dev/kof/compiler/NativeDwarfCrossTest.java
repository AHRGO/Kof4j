package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * X7-2 (DWARF cross): os alvos riscv64/aarch64 emitiam ZERO `.debug_*` — o
 * DWARF x86 (`NativeDwarf`/`.file`/`.loc`) não chegava aos tradutores. A
 * fatia 1 é a line table (arch-independente no GAS): o `.s` cross carrega
 * `.file 1 "<fonte>"` + `.loc 1 <linha> 0` por operação, do mesmo
 * {@code KofDebugInfo} do x86 — o `as` do alvo converte em `.debug_line` e o
 * gdb faz `break Main.kf:2` na fonte Kof. Os DIEs de subprogram
 * (frame_base por ABI: s11 riscv / x29 aarch) ficam para a fatia 2.
 *
 * Provas aqui rodam SEM toolchain cross (o `.s` é escrito antes do `as`; o
 * `ToolchainMissing` é capturado depois): o ELF com `.debug_line` real só é
 * verificado quando o binário existe (CI com qemu/as).
 */
class NativeDwarfCrossTest {

    private static final String SRC = """
            main() {
                println("dwarf")
                var x = 1
            }
            """;

    private Path compileToAsm(Path tmp, Target target, boolean debugInfo) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, SRC);
        Path outDir = tmp.resolve("out-" + target.name().toLowerCase());
        CompilerDriver driver = new CompilerDriver();
        driver.setDebugInfoEnabled(debugInfo);
        driver.compile(file, outDir, target);
        try (Stream<Path> s = Files.walk(outDir)) {
            Path asm = s.filter(p -> p.toString().endsWith(".s")).findFirst().orElse(null);
            assertNotNull(asm, "o .s cross deve ser escrito antes do assembler; target=" + target);
            return asm;
        }
    }

    @Test
    void riscv64EmitsDwarfFileAndLoc(@TempDir Path tmp) throws Exception {
        String asm = Files.readString(compileToAsm(tmp, Target.NATIVE_RISCV64, true));
        assertTrue(asm.contains(".file 1 \"Main.kf\""),
                "riscv .s deve declarar o arquivo-fonte Kof; primeiras linhas: " + head(asm));
        assertTrue(asm.contains(".loc 1 2 0") || asm.contains(".loc 1 3 0"),
                "linha do corpo do main (2/3) deve virar .loc; .loc presentes: " + locLines(asm));
    }

    @Test
    void aarch64EmitsDwarfFileAndLoc(@TempDir Path tmp) throws Exception {
        String asm = Files.readString(compileToAsm(tmp, Target.NATIVE_AARCH64, true));
        assertTrue(asm.contains(".file 1 \"Main.kf\""),
                "aarch64 .s deve herdar o .file (diretivas `.` passam verbatim pelo tradutor); inicio: " + head(asm));
        assertTrue(asm.contains(".loc 1 2 0") || asm.contains(".loc 1 3 0"),
                "aarch64 .s deve herdar os .loc; .loc presentes: " + locLines(asm));
    }

    @Test
    void crossLineTablesAreOffWhenDebugInfoDisabled(@TempDir Path tmp) throws Exception {
        String asm = Files.readString(compileToAsm(tmp, Target.NATIVE_RISCV64, false));
        assertFalse(asm.contains(".file 1"), "debugInfo off nao pode emitir .file");
        assertFalse(asm.contains(".loc 1"), "debugInfo off nao pode emitir .loc");
    }

    @Test
    void crossElfCarriesDebugLineWhenToolchainPresent(@TempDir Path tmp) throws Exception {
        Path asm = compileToAsm(tmp, Target.NATIVE_RISCV64, true);
        Path bin = asm.resolveSibling(asm.getFileName().toString().replace(".s", ""));
        Assumptions.assumeTrue(Files.exists(bin),
                "ELF cross exige riscv64-as/ld + qemu no host — prova .s-only aqui (CI executa)");
        String lines = runCmd("objdump", "--dwarf=decodedline", bin.toString());
        assertTrue(lines.contains("Main.kf"),
                "ELF riscv deve ter .debug_line apontando p/ a fonte Kof; got: " + head(lines));
        assertTrue(lines.matches("(?s).*Main\\.kf\\s+2\\s.*") || lines.matches("(?s).*Main\\.kf\\s+3\\s.*"),
                "linha do corpo do main mapeada no .debug_line riscv; got: " + head(lines));
    }

    private static String locLines(String text) {
        StringBuilder sb = new StringBuilder();
        for (String l : text.split("\n")) {
            if (l.contains(".loc 1")) sb.append(l.strip()).append(' ');
        }
        return sb.length() == 0 ? "(nenhuma)" : sb.toString();
    }

    private static String head(String text) {
        String[] ls = text.split("\n");
        return String.join("\n", java.util.Arrays.copyOfRange(ls, 0, Math.min(ls.length, 8)));
    }

    private static String runCmd(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        p.getInputStream().transferTo(buf);
        int ec = p.waitFor();
        assertTrue(ec == 0 || ec == 1, "cmd exit " + ec + " para " + String.join(" ", cmd));
        return buf.toString(StandardCharsets.UTF_8);
    }
}
