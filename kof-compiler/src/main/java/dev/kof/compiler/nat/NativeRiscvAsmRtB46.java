package dev.kof.compiler.nat;

// DB001 fatia 1 (15/09, dono = 192.168.100.18): auxiliares do consumidor DB
// cross — kof_io_strlen (NUL-terminado, para os char* devolvidos pela
// libsqlite3), kof_io_make_string (KofString de ptr+len cru) e o builder JSON
// (kof_json_builder_new/grow/char/str/result) que monta o objeto {"col":val}
// de cada linha do db.query. Ports 1:1 de RuntimeIo1.java (x86) e
// RuntimeJsonBuilder.java. Builder layout (32B, typeId=101): typeId@0
// super@4 vtable@8 len@16 cap@20 buffer@24. KofStr: typeId@0 super@4
// vtable@8 len@16 cap@20 data@24.
public final class NativeRiscvAsmRtB46 {

    private NativeRiscvAsmRtB46() {}

    static String RISCV_RUNTIME_ASM_B_46 = """
            # kof_io_strlen(str@a0) -> byte count (sem o NUL). Port RuntimeIo1:53.
            .globl kof_io_strlen
            kof_io_strlen:
                mv   t0, a0
                li   a0, 0
            .Lio_strlen_loop:
                add  t1, t0, a0
                lbu  t2, 0(t1)
                beqz t2, .Lio_strlen_done
                addi a0, a0, 1
                j    .Lio_strlen_loop
            .Lio_strlen_done:
                ret

            # kof_io_make_string(data@a0, len@a1) -> KofStr*. Port RuntimeIo1:24.
            .globl kof_io_make_string
            kof_io_make_string:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0
                mv   s1, a1
                addi a0, s1, 25
                call kof_alloc
                mv   s2, a0
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                sw   s1, 16(s2)
                sw   zero, 20(s2)
                addi a0, s2, 24
                mv   a1, s0
                mv   a2, s1
                call kof_memcpy
                addi t0, s2, 24
                add  t0, t0, s1
                sb   zero, 0(t0)
                mv   a0, s2
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_json_builder_new() -> builder* (typeId=101, len=0, cap=64).
            .globl kof_json_builder_new
            kof_json_builder_new:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   s0, 0(sp)
                li   a0, 32
                call kof_alloc
                mv   s0, a0
                li   t0, 101
                sw   t0, 0(s0)
                sw   zero, 4(s0)
                sd   zero, 8(s0)
                sw   zero, 16(s0)
                li   t0, 64
                sw   t0, 20(s0)
                li   a0, 64
                call kof_alloc
                sd   a0, 24(s0)
                mv   a0, s0
                ld   s0, 0(sp)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_json_builder_grow(builder@a0): cap*=2, realloc, copia len.
            .globl kof_json_builder_grow
            kof_json_builder_grow:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0
                lw   t0, 20(s0)
                slli t0, t0, 1
                sw   t0, 20(s0)
                mv   a0, t0
                call kof_alloc
                mv   s1, a0
                lw   s2, 16(s0)
                mv   a0, s1
                ld   a1, 24(s0)
                mv   a2, s2
                call kof_memcpy
                sd   s1, 24(s0)
                mv   a0, s0
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_json_builder_char(builder@a0, char@a1)
            .globl kof_json_builder_char
            kof_json_builder_char:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   s0, 0(sp)
                mv   s0, a0
                lw   t0, 16(s0)
                lw   t1, 20(s0)
                bgeu t1, t0, .Ljson_bch_ok
                mv   a0, s0
                call kof_json_builder_grow
            .Ljson_bch_ok:
                lw   t0, 16(s0)
                ld   t1, 24(s0)
                add  t1, t1, t0
                sb   a1, 0(t1)
                addi t0, t0, 1
                sw   t0, 16(s0)
                ld   s0, 0(sp)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_json_builder_str(builder@a0, str@a1) — anexa KofStr*.
            .globl kof_json_builder_str
            kof_json_builder_str:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s1)
            .Ljson_bst_grow:
                lw   t0, 16(s0)
                add  t0, t0, s2
                lw   t1, 20(s0)
                bgeu t1, t0, .Ljson_bst_ok
                mv   a0, s0
                call kof_json_builder_grow
                j    .Ljson_bst_grow
            .Ljson_bst_ok:
                lw   t0, 16(s0)
                ld   t1, 24(s0)
                add  a0, t1, t0
                addi a1, s1, 24
                mv   a2, s2
                call kof_memcpy
                lw   t0, 16(s0)
                add  t0, t0, s2
                sw   t0, 16(s0)
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_json_builder_result(builder@a0) -> KofStr* congelado.
            .globl kof_json_builder_result
            kof_json_builder_result:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0
                lw   s2, 16(s0)
                addi a0, s2, 25
                call kof_alloc
                mv   s1, a0
                li   t0, 1
                sw   t0, 0(s1)
                sw   zero, 4(s1)
                sd   zero, 8(s1)
                sw   s2, 16(s1)
                sw   zero, 20(s1)
                addi a0, s1, 24
                ld   a1, 24(s0)
                mv   a2, s2
                call kof_memcpy
                addi t0, s1, 24
                add  t0, t0, s2
                sb   zero, 0(t0)
                mv   a0, s1
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            """;
}