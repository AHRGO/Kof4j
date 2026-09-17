package dev.kof.compiler.nat;

import dev.kof.compiler.IRLocalVariable;
import java.util.ArrayList;
import java.util.List;

/**
 * Frente 4 (Debugger nativo), fatia 1: CU DWARF propria do Kof no ELF x86.
 *
 * O `.file`/`.loc` ja gera a line table (.debug_line, fase 5/bug82); aqui
 * acrescenta-se .debug_info/.debug_abbrev com DW_TAG_compile_unit + um
 * DW_TAG_subprogram por funcao Kof (DW_AT_name = nome-fonte da funcao,
 * low_pc = rotulo mangled, high_pc = offset ate o rotulo .Lfe_ emitido no
 * fim de cada funcao, decl_file/decl_line + frame_base DW_OP_reg6(rbp)).
 * E o DIE que gdb/LLDB/DAP usam p/ "info functions" e breakpoints por nome
 * sem mangle manual.
 *
 * Fatia 2 (17/09): DW_TAG_formal_parameter/DW_TAG_variable filhos de cada
 * subprogram com DW_AT_location = DW_OP_fbreg no MESMO deslocamento que o
 * prologue usa (`-(index+1)*8(%rbp)` — layout do NativeMethodEmitter); o
 * debugger le assim os args/locals sem entender o ABI do Kof.
 *
 * Medido (prototipo 17/09, as/ld + readelf/objdump --dwarf=info): ao o
 * arquivo .s declarar .debug_info/.debug_abbrev explicitamente, o GAS
 * SUPRIME a CU automatica dele (so a do .loc/.line permanece) — a CU Kof
 * fica univoca no ELF. Codigos validados contra o dwarf2.h do GCC:
 * TAG compile_unit=0x11 subprogram=0x2e formal_parameter=0x05
 * variable=0x34; AT name=0x03 location=0x02 low_pc=0x11 high_pc=0x12
 * language=0x13 stmt_list=0x10 decl_file=0x3a decl_line=0x3b
 * frame_base=0x40; FORM string=0x08 addr=0x01 data1=0x0b data4=0x06
 * udata=0x0f exprloc=0x18; OP reg6=0x76 fbreg=0x91.
 */
final class NativeDwarf {

    record Local(String name, int offset) {}

    record Fn(String label, String kofName, int declLine, List<Local> params, List<Local> locals) {}

    final List<Fn> fns = new ArrayList<>();

    /** Slots do prologue: Kof guarda todo local/arg `i` em -(i+1)*8(%rbp). */
    static int slotOffset(int index) {
        return -(index + 1) * 8;
    }

    void add(String label, String kofName, int declLine, List<Local> params, List<Local> locals) {
        fns.add(new Fn(label, kofName, declLine, params, locals));
    }

    void emit(StringBuilder sb, String sourceFile) {
        if (fns.isEmpty()) return;
        sb.append("\n# --- DWARF Kof: DW_TAG_subprogram por funcao (frente 4) ---\n");
        sb.append("    .section .debug_abbrev\n");
        sb.append(".Lkof_abbrev:\n");
        // 1: compile_unit (com filhos). low_pc/high_pc: o gdb associa a CU ao
        // codigo do programa APENAS via ranges — sem elas a CU e ignorada no
        // "info locals" (medido 17/09, gdb 15 no ELF do DwarfProbe2).
        sb.append("    .byte 1\n    .byte 0x11\n    .byte 1\n");
        sb.append("    .byte 0x03, 0x08\n    .byte 0x13, 0x0b\n    .byte 0x10, 0x06\n");
        sb.append("    .byte 0x11, 0x01\n    .byte 0x12, 0x06\n");
        sb.append("    .byte 0, 0\n");
        // 2: subprogram (com filhos: params/locals)
        sb.append("    .byte 2\n    .byte 0x2e\n    .byte 1\n");
        sb.append("    .byte 0x11, 0x01\n    .byte 0x12, 0x06\n    .byte 0x03, 0x08\n");
        sb.append("    .byte 0x3a, 0x0b\n    .byte 0x3b, 0x0f\n    .byte 0x40, 0x18\n");
        sb.append("    .byte 0, 0\n");
        // 3: formal_parameter (sem filhos)
        sb.append("    .byte 3\n    .byte 0x05\n    .byte 0\n");
        sb.append("    .byte 0x03, 0x08\n    .byte 0x02, 0x18\n");
        sb.append("    .byte 0, 0\n");
        // 4: variable (sem filhos)
        sb.append("    .byte 4\n    .byte 0x34\n    .byte 0\n");
        sb.append("    .byte 0x03, 0x08\n    .byte 0x02, 0x18\n");
        sb.append("    .byte 0, 0\n");
        sb.append("    .byte 0\n");
        sb.append("\n    .section .debug_info\n");
        sb.append(".Lkof_info:\n");
        sb.append("    .4byte .Lkof_info_end - .Lkof_info - 4\n");
        // v4: so a partir da DWARF4 DW_AT_high_pc FORM_data4 = OFFSET de
        // low_pc (na v3 o gdb interpretava como endereco absoluto -> a CU
        // inteira era descartada como "non-debugging", medido 17/09).
        // Header DWARF4 = version(2) unit_type(1) address_size(1)
        // abbrev_offset(4) — ordem DIFERENTE da v3 (nao mover por engano).
        sb.append("    .2byte 4\n");
        sb.append("    .4byte .Lkof_abbrev\n");
        sb.append("    .byte 8\n");   // address size
        sb.append("    .byte 1\n");
        sb.append("    .asciz \"").append(escape(sourceFile)).append("\"\n");
        sb.append("    .byte 0x0c\n");
        sb.append("    .4byte 0\n");
        sb.append("    .quad ").append(fns.get(0).label()).append("\n");
        sb.append("    .4byte .Lfe_").append(fns.get(fns.size() - 1).label())
          .append(" - ").append(fns.get(0).label()).append("\n");
        for (Fn f : fns) {
            sb.append("    .byte 2\n");
            sb.append("    .quad ").append(f.label()).append("\n");
            sb.append("    .4byte .Lfe_").append(f.label()).append(" - ").append(f.label()).append("\n");
            sb.append("    .asciz \"").append(escape(f.kofName())).append("\"\n");
            sb.append("    .byte 1\n");
            sb.append("    .uleb128 ").append(Math.max(1, f.declLine())).append("\n");
            sb.append("    .byte 1\n    .byte 0x56\n"); // frame_base: DW_OP_reg6=0x50+6 (rbp). 0x76 seria breg6 (pediria operando)
            for (Local p : f.params()) {
                emitLoc(sb, 3, p);
            }
            for (Local v : f.locals()) {
                emitLoc(sb, 4, v);
            }
            sb.append("    .byte 0\n"); // null: fim dos filhos do subprogram
        }
        sb.append("    .byte 0\n"); // null: fim dos filhos do CU
        sb.append(".Lkof_info_end:\n");
    }

    private static void emitLoc(StringBuilder sb, int abbrev, Local loc) {
        byte[] fbreg = sleb128(loc.offset());
        sb.append("    .byte ").append(abbrev).append("\n");
        sb.append("    .asciz \"").append(escape(loc.name())).append("\"\n");
        sb.append("    .byte ").append(1 + fbreg.length).append("\n");
        sb.append("    .byte 0x91");
        for (byte b : fbreg) {
            sb.append(", 0x").append(String.format("%02x", b));
        }
        sb.append("\n");
    }

    static byte[] sleb128(long value) {
        List<Byte> out = new ArrayList<>();
        boolean more = true;
        while (more) {
            byte b = (byte) (value & 0x7f);
            value >>= 7;
            boolean signBitSet = (b & 0x40) != 0;
            boolean negative = value < 0;
            if ((negative && signBitSet) || (!negative && !signBitSet)) {
                more = false;
            } else {
                b |= 0x80;
            }
            out.add(b);
        }
        byte[] r = new byte[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
