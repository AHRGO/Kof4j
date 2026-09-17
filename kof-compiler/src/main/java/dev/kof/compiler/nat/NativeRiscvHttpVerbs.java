package dev.kof.compiler.nat;

/**
 * FASE 3 (REFACTOR-500) + §259 (17/09): wrappers por verbo riscv64 (get/post/
 * put/options c/ e s/ headers, status) extraidos de NativeRiscvHttpCore (gate
 * ≤500 — porta §259 dos 3 knobs); a concatenação em NativeBackend.emitRiscvHttp
 * preserva o asm byte-a-byte (precedente REFACTOR-500 Fase 8 no x86).
 */
public final class NativeRiscvHttpVerbs {

    private NativeRiscvHttpVerbs() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            # wrappers: a0=url [a1=body|headers] -> a0=KofString (status em .Lhttp_last_status)
            .globl kof_http_get
            kof_http_get:
                la   a1, .Lhttp_m_get
                li   a2, 0
                li   a4, 0
                j    kof_http_core
            .globl kof_http_get_headers
            kof_http_get_headers:
                mv   a4, a1
                la   a1, .Lhttp_m_get
                li   a2, 0
                j    kof_http_core
            .globl kof_http_post
            kof_http_post:
                mv   a2, a1
                la   a1, .Lhttp_m_post
                li   a4, 0
                j    kof_http_core
            .globl kof_http_post_headers
            kof_http_post_headers:
                mv   a4, a2
                mv   a2, a1
                la   a1, .Lhttp_m_post
                j    kof_http_core
            .globl kof_http_delete
            kof_http_delete:
                la   a1, .Lhttp_m_delete
                li   a2, 0
                li   a4, 0
                j    kof_http_core
            .globl kof_http_put
            kof_http_put:
                mv   a2, a1
                la   a1, .Lhttp_m_put
                li   a4, 0
                j    kof_http_core
            .globl kof_http_patch
            kof_http_patch:
                mv   a2, a1
                la   a1, .Lhttp_m_patch
                li   a4, 0
                j    kof_http_core
            .globl kof_http_status
            kof_http_status:
                addi sp, sp, -16
                sd   ra, 8(sp)
                la   a1, .Lhttp_m_get
                li   a2, 0
                li   a4, 0
                call kof_http_core
                la   a0, .Lhttp_last_status
                ld   a0, 0(a0)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            .globl kof_http_options
            kof_http_options:
                la   a1, .Lhttp_m_options
                li   a2, 0
                li   a4, 0
                j    kof_http_core
            .globl kof_http_options_headers
            kof_http_options_headers:
                mv   a4, a1
                la   a1, .Lhttp_m_options
                li   a2, 0
                j    kof_http_core
            # §259: configurators + helpers de circuit reais em NativeRiscvHttpSupport
            """);
    }
}
