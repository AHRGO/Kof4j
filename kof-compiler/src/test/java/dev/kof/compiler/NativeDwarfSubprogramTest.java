package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.*;

import dev.kof.compiler.Target;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Frente 4 (Debugger nativo) — fatia 1: além da line table (.debug_line via
 * .file/.loc, coberta por {@link NativeDwarfLineInfoTest}), o backend x86 deve
 * emitir um DWARF .debug_info/.debug_abbrev com um DW_TAG_subprogram por função
 * Kof, carimbado com o nome-fonte (DW_AT_name), low_pc/high_pc e linha de
 * declaração. Prova: {@code objdump --dwarf=info} mostra o DIE da função
 * {@code main} com o nome Kof — o que um debugger (gdb/LLDB/DAP) usa p/
 * "info functions" e p/ quebrar ponto por nome sem depender só da mangle
 * {@code Default_Main_main}.
 */
class NativeDwarfSubprogramTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static String runCmd(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        p.waitFor();
        return out;
    }

    @Test
    void nativeEmitDwarfSubprogramInfo(@TempDir Path tempDir) throws Exception {
        String src = """
                Int add(Int a, Int b) { return a + b }
                main() {
                    println(add(2, 3))
                }
                """;
        Path file = tempDir.resolve("Main.kf");
        Files.writeString(file, src);
        Path outDir = tempDir.resolve("out");
        driver.setDebugInfoEnabled(true);
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());

        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binario ausente");

        String info = runCmd("objdump", "--dwarf=info", bin.toString());
        assertTrue(info.contains("DW_TAG_compile_unit"),
                "DWARF .debug_info deve ter um DW_TAG_compile_unit; got: " + head(info));
        assertTrue(info.contains("DW_TAG_subprogram"),
                "DWARF .debug_info deve ter DW_TAG_subprogram por função; got: " + head(info));
        assertTrue(info.contains("\"main\"") || info.contains(": main") || info.contains("main\n"),
                "DW_AT_name da função main deve aparecer; got: " + head(info));
        assertTrue(info.contains("\"add\"") || info.contains(": add") || info.contains("add\n"),
                "DW_AT_name da função add deve aparecer; got: " + head(info));
        assertTrue(info.contains("DW_AT_low_pc"),
                "subprogram deve ter DW_AT_low_pc; got: " + head(info));
        assertTrue(info.contains("DW_AT_high_pc"),
                "subprogram deve ter DW_AT_high_pc; got: " + head(info));
        assertTrue(info.contains("DW_AT_decl_file"),
                "subprogram deve ter DW_AT_decl_file; got: " + head(info));
        // fatia 2: args com DW_AT_location (DW_OP_fbreg) via frame_base(rbp)
        assertTrue(info.contains("DW_TAG_formal_parameter"),
                "subprogram deve ter DW_TAG_formal_parameter p/ args; got: " + head(info));
        assertTrue(info.contains("DW_AT_location") && info.contains("fbreg"),
                "formal_parameter deve ter DW_AT_location fbreg; got: " + head(info));
        assertTrue(info.contains("DW_AT_frame_base"),
                "subprogram deve ter DW_AT_frame_base; got: " + head(info));
        // fatia 3: tipos — base_type DIE + DW_AT_type referenciado nos params
        assertTrue(info.contains("DW_TAG_base_type"),
                "CU deve ter DW_TAG_base_type p/ tipos Kof; got: " + head(info));
        assertTrue(info.contains("DW_AT_type"),
                "formal_parameter deve ter DW_AT_type; got: " + head(info));
        assertTrue(info.contains("Int"),
                "base_type Int deve aparecer; got: " + head(info));

        // binario ainda executa corretamente (DWARF nao pode quebrar codegen)
        Process r = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(r.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).trim();
        int ec = r.waitFor();
        assertEquals(0, ec, "exit code, output: " + out);
        assertEquals("5", out, "programa ainda deve imprimir 5");
    }

    private static String head(String s) {
        return s.length() > 800 ? s.substring(0, 800) : s;
    }
}
