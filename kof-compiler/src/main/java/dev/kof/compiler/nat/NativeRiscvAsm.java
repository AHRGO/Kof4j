package dev.kof.compiler.nat;

/**
 * FASE 3 (REFACTOR-500): runtime assembly riscv64 do NativeBackend.
 * Os 4 blocos originais (RISCV_RUNTIME_ASM/_STRN002_/_B/_MAPSET_) eram
 * ~4700 linhas numa só classe; aqui são fatiados em NativeRiscvAsm*
 * (≤500) e remontados por concatenação — valor byte-idêntico ao original
 * (prova: diff do .s gerado nos 3 targets).
 */
public final class NativeRiscvAsm {

    private NativeRiscvAsm() {}

    // String concatenação literal = variável-constante (JLS 15.28): o javac
    // DOBRA o valor no constant pool de quem referencia. Editar só
    // NativeRiscvAsmRt0 (ex.: G-0 do GC cross) e recompilar incremental deixa
    // o valor antigo embutido em classes-consumidoras não-recompiladas (test
    // de slice via getstatic = bytes velhos; RiscvSlices via reflection =
    // bytes novos) → split-brain FALSO. StringBuilder = mesmo bytes,
    // resolvido no <clinit> a cada JVM (mesma razão do runtimeB() abaixo).
    static final String RISCV_RUNTIME_ASM = runtimeRt();
    private static String runtimeRt() {
        return new StringBuilder()
                .append(NativeRiscvAsmRt0.RISCV_RUNTIME_ASM_0)
                .append(NativeRiscvAsmRt1.RISCV_RUNTIME_ASM_1)
                .toString();
    }
    static final String RISCV_STRN002_ASM = runtimeStrn();
    private static String runtimeStrn() {
        return new StringBuilder()
                .append(NativeRiscvAsmStrn0.RISCV_STRN002_ASM_0)
                .append(NativeRiscvAsmStrn1.RISCV_STRN002_ASM_1)
                .toString();
    }
    // A cadeia B_0..B_9 ultrapassa o limite de 64KB de string-constante do pool
    // quando dobrada em compile-time (javac "constant string too long" no uso).
    // Concatenar via StringBuilder = mesmo bytes, calculado no <clinit>.
    static final String RISCV_RUNTIME_ASM_B = runtimeB();
    private static String runtimeB() {
        return new StringBuilder()
                .append(NativeRiscvAsmRtB0.RISCV_RUNTIME_ASM_B_0)
                .append(NativeRiscvAsmRtB1.RISCV_RUNTIME_ASM_B_1)
                // §272 face (c): observability spans/IDs reais (era stub constante no B1).
                .append(NativeRiscvAsmObs.RISCV_ASM_OBS)
                .append(NativeRiscvAsmRtB2.RISCV_RUNTIME_ASM_B_2)
                .append(NativeRiscvAsmRtB3.RISCV_RUNTIME_ASM_B_3)
                .append(NativeRiscvAsmRtB4.RISCV_RUNTIME_ASM_B_4)
                .append(NativeRiscvAsmRtB5.RISCV_RUNTIME_ASM_B_5)
                .append(NativeRiscvAsmRtB6.RISCV_RUNTIME_ASM_B_6)
                .append(NativeRiscvAsmRtB7.RISCV_RUNTIME_ASM_B_7)
                .append(NativeRiscvAsmRtB8.RISCV_RUNTIME_ASM_B_8)
                .append(NativeRiscvAsmRtB9.RISCV_RUNTIME_ASM_B_9)
                .append(NativeRiscvAsmRtB10.RISCV_RUNTIME_ASM_B_10)
                .append(NativeRiscvAsmRtB11.RISCV_RUNTIME_ASM_B_11)
                .append(NativeRiscvAsmRtB12.RISCV_RUNTIME_ASM_B_12)
                .append(NativeRiscvAsmRtB13.RISCV_RUNTIME_ASM_B_13)
                .append(NativeRiscvAsmRtB14.RISCV_RUNTIME_ASM_B_14)
                .append(NativeRiscvAsmRtB15.RISCV_RUNTIME_ASM_B_15)
                .append(NativeRiscvAsmRtB16.RISCV_RUNTIME_ASM_B_16)
                .append(NativeRiscvAsmRtB17.RISCV_RUNTIME_ASM_B_17)
                .append(NativeRiscvAsmRtB18.RISCV_RUNTIME_ASM_B_18)
                .append(NativeRiscvAsmRtB19.RISCV_RUNTIME_ASM_B_19)
                .append(NativeRiscvAsmRtB20.RISCV_RUNTIME_ASM_B_20)
                .append(NativeRiscvAsmRtB21.RISCV_RUNTIME_ASM_B_21)
                .append(NativeRiscvAsmRtB22.RISCV_RUNTIME_ASM_B_22)
                .append(NativeRiscvAsmRtB23.RISCV_RUNTIME_ASM_B_23)
                .append(NativeRiscvAsmRtB24.RISCV_RUNTIME_ASM_B_24)
                .append(NativeRiscvAsmRtB25.RISCV_RUNTIME_ASM_B_25)
                .append(NativeRiscvAsmRtB25b.RISCV_RUNTIME_ASM_B_25B)
                .append(NativeRiscvAsmRtB26.RISCV_RUNTIME_ASM_B_26)
                .append(NativeRiscvAsmRtB27.RISCV_RUNTIME_ASM_B_27)
                .append(NativeRiscvAsmRtB28.RISCV_RUNTIME_ASM_B_28)
                .append(NativeRiscvAsmRtB29.RISCV_RUNTIME_ASM_B_29)
                .append(NativeRiscvAsmRtB30.RISCV_RUNTIME_ASM_B_30)
                .append(NativeRiscvAsmRtB31.RISCV_RUNTIME_ASM_B_31)
                .append(NativeRiscvAsmRtB32.RISCV_RUNTIME_ASM_B_32)
                .append(NativeRiscvAsmRtB33.RISCV_RUNTIME_ASM_B_33)
                .append(NativeRiscvAsmRtB34.RISCV_RUNTIME_ASM_B_34)
                .append(NativeRiscvAsmRtB35.RISCV_RUNTIME_ASM_B_35)
                .append(NativeRiscvAsmRtB36.RISCV_RUNTIME_ASM_B_36)
                .append(NativeRiscvAsmRtB37.RISCV_RUNTIME_ASM_B_37)
                .append(NativeRiscvAsmRtB38.RISCV_RUNTIME_ASM_B_38)
                .append(NativeRiscvAsmRtB39.RISCV_RUNTIME_ASM_B_39)
                .append(NativeRiscvAsmRtB40.RISCV_RUNTIME_ASM_B_40)
                // S13b (plan-stdlib-expansion): parse com default (§43) —
                // wrapper c/ handler no exc_chain; B34–B39 = outras lanes.
                .append(NativeRiscvAsmRtB41.RISCV_RUNTIME_ASM_B_41)
                // G-1 (NATIVE002 face 1, 15/09): kof_alloc (free-list) +
                // kof_free + kof_memstats — memória riscv64.
                .append(NativeRiscvAsmRtB42.RISCV_RUNTIME_ASM_B_42)
                // G-3 (NATIVE002 face 1, 15/09): mark conservador riscv64
                // (kof_gc_try_mark/mark_transitive/mark) sobre a gc-list do G-2.
                .append(NativeRiscvAsmRtB43.RISCV_RUNTIME_ASM_B_43)
                // G-4 (NATIVE002 face 1, 15/09): sweep + collect riscv64
                // (kof_gc_sweep/collect_now/collect/tick). O kof_alloc do B42
                // chama kof_gc_collect (forward ref resolvido pelo `as`).
                .append(NativeRiscvAsmRtB44.RISCV_RUNTIME_ASM_B_44)
                // FLT001 (NATIVE002, 15/09): Double/Float -> String via libc
                // (snprintf/strtod) — link dinâmico sob demanda. Fecha o gap
                // FLT001 no riscv64/aarch64.
                .append(NativeRiscvAsmRtB45.RISCV_RUNTIME_ASM_B_45)
                // DB001 (15/09): builder JSON + strlen — port RuntimeIo1/RuntimeJsonBuilder.
                .append(NativeRiscvAsmRtB46.RISCV_RUNTIME_ASM_B_46)
                // DB001 (15/09): runtime kof.db SQLite — port Db2/Db4/Db5 (gerado).
                .append(NativeRiscvAsmRtB47.RISCV_RUNTIME_ASM_B_47)
                // CONC001 (15/09): helpers de concorrência de alta ordem —
                // done/poll/cancel/cancelled/selectAny/awaitTimeout (port
                // RuntimeConcurrency; cancel por TID real via gettid+clone ctid).
                .append(NativeRiscvAsmRtB48.RISCV_RUNTIME_ASM_B_48)
                // §284 (18/09): box de erasure — kof_box_*/unbox/box_to_string
                // (port RuntimeErasureBox x86; aarch64 herda via tradutor).
                .append(NativeRiscvAsmRtB49.RISCV_RUNTIME_ASM_B_49)
                // DB-3/DB-1 cross slice A (22/09): ORM F1a no riscv64 —
                // kof_orm_delete_all + kof_orm_count (port RuntimeOrm1) sobre
                // o kof.db SQLite de RtB46/RtB47; aarch64 herda via tradutor.
                .append(NativeRiscvAsmRtB50.RISCV_RUNTIME_ASM_B_50)
                .toString();
    }
    static final String RISCV_MAPSET_ASM = runtimeMapset();
    private static String runtimeMapset() {
        return new StringBuilder()
                .append(NativeRiscvAsmMapset0.RISCV_MAPSET_ASM_0)
                .append(NativeRiscvAsmMapset1.RISCV_MAPSET_ASM_1)
                .append(NativeRiscvAsmMapset2.RISCV_MAPSET_ASM_2)
                .append(NativeRiscvAsmLookups0.RISCV_LOOKUPS_ASM_0)
                .toString();
    }
}
