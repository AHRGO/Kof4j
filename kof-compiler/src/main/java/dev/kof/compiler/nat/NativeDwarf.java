package dev.kof.compiler.nat;

import java.util.ArrayList;
import java.util.List;

/**
 * Frente 4 (Debugger nativo), fatia 1: CU DWARF propria do Kof no ELF x86.
 *
 * O `.file`/`.loc` ja gera a line table (.debug_line, fase 5/bug82); aqui
 * acrescenta-se .debug_info/.debug_abbrev com DW_TAG_compile_unit + um
 * DW_TAG_subprogram por funcao Kof (DW_AT_name = nome-fonte da funcao,
 * low_pc = rotulo mangled, high_pc = offset ate o rotulo .Lfe_ emitido no
 * fim de cada funcao, decl_file/decl_line). E o DIE que gdb/LLDB/DAP usam
 * p/ "info functions" e breakpoints por nome sem mangle manual.
 *
 * Medido (prototipo 17/09, as/ld + readelf/objdump --dwarf=info): ao o
 * arquivo .s declarar .debug_info/.debug_abbrev explicitamente, o GAS
 * SUPRIME a CU automatica dele (so a do .loc/.line permanece) — a CU Kof
 * fica univoca no ELF. Codigos validados contra o dwarf2.h do GCC:
 * TAG compile_unit=0x11 subprogram=0x2e; AT name=0x03 low_pc=0x11
 * high_pc=0x12 language=0x13 stmt_list=0x10 decl_file=0x3a decl_line=0x3b;
 * FORM string=0x08 addr=0x01 data1=0x0b data4=0x06 udata=0x0f.
 */
final class NativeDwarf {

    record Fn(String label, String kofName, int declLine) {}

    final List<Fn> fns = new ArrayList<>();

    void add(String label, String kofName, int declLine) {
        fns.add(new Fn(label, kofName, declLine));
    }

    void emit(StringBuilder sb, String sourceFile) {
        if (fns.isEmpty()) return;
        sb.append("\n# --- DWARF Kof: DW_TAG_subprogram por funcao (frente 4) ---\n");
        sb.append("    .section .debug_abbrev\n");
        sb.append(".Lkof_abbrev:\n");
        sb.append("    .byte 1\n    .byte 0x11\n    .byte 1\n");
        sb.append("    .byte 0x03, 0x08\n    .byte 0x13, 0x0b\n    .byte 0x10, 0x06\n");
        sb.append("    .byte 0, 0\n");
        sb.append("    .byte 2\n    .byte 0x2e\n    .byte 0\n");
        sb.append("    .byte 0x11, 0x01\n    .byte 0x12, 0x06\n    .byte 0x03, 0x08\n");
        sb.append("    .byte 0x3a, 0x0b\n    .byte 0x3b, 0x0f\n");
        sb.append("    .byte 0, 0\n");
        sb.append("    .byte 0\n");
        sb.append("\n    .section .debug_info\n");
        sb.append(".Lkof_info:\n");
        sb.append("    .4byte .Lkof_info_end - .Lkof_info - 4\n");
        sb.append("    .2byte 3\n");
        sb.append("    .4byte .Lkof_abbrev\n");
        sb.append("    .byte 8\n");
        sb.append("    .byte 1\n");
        sb.append("    .asciz \"").append(escape(sourceFile)).append("\"\n");
        sb.append("    .byte 0x0c\n");
        sb.append("    .4byte 0\n");
        for (Fn f : fns) {
            sb.append("    .byte 2\n");
            sb.append("    .quad ").append(f.label()).append("\n");
            sb.append("    .4byte .Lfe_").append(f.label()).append(" - ").append(f.label()).append("\n");
            sb.append("    .asciz \"").append(escape(f.kofName())).append("\"\n");
            sb.append("    .byte 1\n");
            sb.append("    .uleb128 ").append(Math.max(1, f.declLine())).append("\n");
        }
        sb.append("    .byte 0\n");
        sb.append(".Lkof_info_end:\n");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
