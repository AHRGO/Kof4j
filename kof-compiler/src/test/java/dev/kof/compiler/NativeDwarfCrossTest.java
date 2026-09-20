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
 * Host-dependência (corrigida 19/09): o `.s` NÃO sobrevive a um assemble bem
 * sucedido — `NativeArchEmitter` o apaga depois do link, exceto sob
 * {@code KOF_KEEP_ASM}. Logo, num host SEM toolchain cross o `.s` fica (o
 * {@code ToolchainMissing} é capturado depois) e a prova é o texto; num host
 * COM toolchain o `.s` some e a prova é o ELF — que exige o objdump do ALVO
 * (o `objdump` do host não decodifica riscv/aarch64). O teste cobre as duas
 * rotas e só faz skip honesto quando nenhuma está disponível.
 */
class NativeDwarfCrossTest {

    private static final String SRC = """
            main() {
                println("dwarf")
                var x = 1
            }
            """;

    private Path compile(Path tmp, Target target, boolean debugInfo) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, SRC);
        Path outDir = tmp.resolve("out-" + target.name().toLowerCase());
        CompilerDriver driver = new CompilerDriver();
        driver.setDebugInfoEnabled(debugInfo);
        driver.compile(file, outDir, target);
        return outDir;
    }

    private static Path asmOrNull(Path outDir) throws Exception {
        try (Stream<Path> s = Files.walk(outDir)) {
            return s.filter(p -> p.toString().endsWith(".s")).findFirst().orElse(null);
        }
    }

    private static Path binOrNull(Path outDir) {
        Path b = outDir.resolve("Default").resolve("Main");
        return Files.exists(b) ? b : null;
    }

    /** objdump do ALVO quando existir (o do host não decodifica cross). */
    private static String objdump(Target target) {
        String t = target == Target.NATIVE_RISCV64 ? "riscv64-linux-gnu-objdump"
                : "aarch64-linux-gnu-objdump";
        return Files.exists(Path.of("/usr/bin/" + t)) ? t : "objdump";
    }

    private void assertDwarfSource(Path outDir, Target target, String arch) throws Exception {
        Path asm = asmOrNull(outDir);
        if (asm != null) {
            String text = Files.readString(asm);
            assertTrue(text.contains(".file 1 \"Main.kf\""),
                    arch + " .s deve declarar o arquivo-fonte Kof; inicio: " + head(text));
            assertTrue(text.contains(".loc 1 2 0") || text.contains(".loc 1 3 0"),
                    arch + " linha do corpo do main (2/3) deve virar .loc; .loc presentes: " + locLines(text));
            return;
        }
        Path bin = binOrNull(outDir);
        Assumptions.assumeTrue(bin != null,
                arch + ": nem .s (toolchain apagou) nem ELF — toolchain parcial?");
        String lines = runCmd(objdump(target), "--dwarf=decodedline", bin.toString());
        assertTrue(lines.contains("Main.kf"),
                arch + " ELF deve ter .debug_line apontando p/ a fonte Kof; got: " + head(lines));
        assertTrue(lines.matches("(?s).*Main\\.kf\\s+2\\s.*") || lines.matches("(?s).*Main\\.kf\\s+3\\s.*"),
                arch + " linha do corpo do main mapeada no .debug_line; got: " + head(lines));
    }

    @Test
    void riscv64EmitsDwarfFileAndLoc(@TempDir Path tmp) throws Exception {
        assertDwarfSource(compile(tmp, Target.NATIVE_RISCV64, true), Target.NATIVE_RISCV64, "riscv64");
    }

    @Test
    void aarch64EmitsDwarfFileAndLoc(@TempDir Path tmp) throws Exception {
        assertDwarfSource(compile(tmp, Target.NATIVE_AARCH64, true), Target.NATIVE_AARCH64, "aarch64");
    }

    @Test
    void crossLineTablesAreOffWhenDebugInfoDisabled(@TempDir Path tmp) throws Exception {
        Path outDir = compile(tmp, Target.NATIVE_RISCV64, false);
        Path asm = asmOrNull(outDir);
        if (asm != null) {
            String text = Files.readString(asm);
            assertFalse(text.contains(".file 1"), "debugInfo off nao pode emitir .file");
            assertFalse(text.contains(".loc 1"), "debugInfo off nao pode emitir .loc");
            return;
        }
        Path bin = binOrNull(outDir);
        Assumptions.assumeTrue(bin != null, "riscv64: sem .s nem ELF");
        String lines = runCmd(objdump(Target.NATIVE_RISCV64), "--dwarf=decodedline", bin.toString());
        assertFalse(lines.contains("Main.kf"),
                "debugInfo off: ELF nao pode ter .debug_line da fonte Kof; got: " + head(lines));
    }

    // ---- fatia 2 (X7-2): DIEs CU/subprogram no cross --------------------------
    // Espelho do padrao da lane irma (5d9c855c): .s quando existe (host sem
    // toolchain), senao o ELF via objdump DO ALVO (o do host nao decodifica
    // cross) — nunca pressupor os dois. A antiga prova ELF-only daqui foi
    // engolida por essa reestruturacao (assertDwarfSource + fallback por alvo).

    @Test
    void riscv64EmitsSubprogramDiesWithOwnFrameBase(@TempDir Path tmp) throws Exception {
        Path out = compile(tmp, Target.NATIVE_RISCV64, true);
        Path asm = asmOrNull(out);
        if (asm != null) {
            String text = Files.readString(asm);
            assertTrue(text.contains(".section .debug_abbrev"), "riscv deve emitir a tabela de abreviacoes");
            assertTrue(text.contains(".section .debug_info"), "riscv deve emitir o CU/subprogram DIE");
            assertTrue(text.contains(".asciz \"main\""), "DW_AT_name da funcao Kof (gdb `info functions`)");
            assertTrue(text.contains(".Lfe_"), "rotulo de fim de funcao p/ DW_AT_high_pc (offset)");
            // frame_base riscv = DW_OP_regx x27 (s11): 0x90 0x1b — NUNCA o 0x56
            // do rbp. Negativas restritas ao bloco .debug_*: o blob de runtime
            // do .s contem hex arbitrario (varrer o arquivo inteiro daria
            // falso-positivo — armadilha medida no desenvolvimento da prova).
            String dbg = text.substring(text.indexOf(".debug_abbrev"));
            assertTrue(dbg.contains("0x90") && !dbg.contains("0x56"),
                    "frame_base do DIE riscv deve ser regx-x27 (0x90,0x1b), nao o reg6 x86");
            assertTrue(dbg.contains("0x91"), "locals/params com DW_OP_fbreg");
            return;
        }
        Path bin = binOrNull(out);
        Assumptions.assumeTrue(bin != null, "riscv64: nem .s nem ELF — toolchain parcial?");
        String info = runCmd(objdump(Target.NATIVE_RISCV64), "--dwarf=info", bin.toString());
        assertTrue(info.contains("DW_TAG_subprogram") && info.contains("main"),
                "ELF riscv deve conter o DIE subprogram de main; got: " + head(info));
        assertTrue(info.contains("DW_OP_reg27"), "frame_base riscv = x27/s11; got: " + head(info));
    }

    @Test
    void aarch64DiesCarryArmFrameBaseNotRiscv(@TempDir Path tmp) throws Exception {
        Path out = compile(tmp, Target.NATIVE_AARCH64, true);
        Path asm = asmOrNull(out);
        if (asm != null) {
            String text = Files.readString(asm);
            assertTrue(text.contains(".section .debug_info"), "aarch deve herdar os DIEs (diretivas verbatim)");
            assertTrue(text.contains(".asciz \"main\""), "nome Kof no DIE aarch");
            // frame_base aarch64 = DW_OP_reg29 (fp=x29) = 0x6D — nem 0x56 (rbp)
            // nem 0x90 (regx riscv): o DIE nasce codificado p/ o alvo ANTES da
            // traducao (NativeDwarf.Arch), o tradutor nao mexe em bytes DWARF.
            String dbg = text.substring(text.indexOf(".debug_abbrev"));
            assertTrue(dbg.contains("0x6d") || dbg.contains("0x6D"),
                    "frame_base do DIE aarch deve ser DW_OP_reg29 (0x6d)");
            assertFalse(dbg.contains("0x56"), "nao pode carregar o rbp do x86");
            assertFalse(dbg.contains("0x90"), "nao pode carregar o regx riscv no ELF ARM");
            return;
        }
        Path bin = binOrNull(out);
        Assumptions.assumeTrue(bin != null, "aarch64: nem .s nem ELF — toolchain parcial?");
        String info = runCmd(objdump(Target.NATIVE_AARCH64), "--dwarf=info", bin.toString());
        assertTrue(info.contains("DW_TAG_subprogram") && info.contains("main"),
                "ELF aarch deve conter o DIE subprogram de main; got: " + head(info));
        assertTrue(info.contains("DW_OP_reg29"), "frame_base aarch = x29/fp; got: " + head(info));
    }

    @Test
    void debugInfoOffStripsCrossDiesToo(@TempDir Path tmp) throws Exception {
        Path out = compile(tmp, Target.NATIVE_AARCH64, false);
        Path asm = asmOrNull(out);
        if (asm != null) {
            String text = Files.readString(asm);
            assertFalse(text.contains(".debug_abbrev"), "debugInfo off: sem tabela de abreviacoes");
            assertFalse(text.contains(".debug_info"), "debugInfo off: sem DIEs");
            return;
        }
        Path bin = binOrNull(out);
        Assumptions.assumeTrue(bin != null, "aarch64: nem .s nem ELF — toolchain parcial?");
        String info = runCmd(objdump(Target.NATIVE_AARCH64), "--dwarf=info", bin.toString());
        assertFalse(info.contains("DW_TAG_subprogram"), "debugInfo off: ELF sem DIE subprogram");
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
