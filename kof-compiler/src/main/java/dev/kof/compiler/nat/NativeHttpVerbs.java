package dev.kof.compiler.nat;

/**
 * HTTP x86-64 — wrappers por verbo (GET/POST/PUT/PATCH/DELETE/OPTIONS c/ e
 * s/ headers), kof_http_status e configurators §259. Extraído de
 * NativeHttpCore (gate ≤500, 17/09 — fatia retry §259); a concatenação em
 * NativeHttpRuntime preserva o assembly byte-a-byte (precedente
 * REFACTOR-500 Fase 8).
 */
public final class NativeHttpVerbs {

    private NativeHttpVerbs() {}

    static String source() {
        return """
            # wrappers ----------------------------------------------------
            .globl kof_http_get
            .type kof_http_get, @function
            kof_http_get:
                leaq .Lhttp_m_get(%rip), %rsi
                xorq %rdx, %rdx
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_get_headers
            .type kof_http_get_headers, @function
            kof_http_get_headers:
                movq %rsi, %rcx
                leaq .Lhttp_m_get(%rip), %rsi
                xorq %rdx, %rdx
                jmp kof_http_core
            .globl kof_http_delete
            .type kof_http_delete, @function
            kof_http_delete:
                leaq .Lhttp_m_delete(%rip), %rsi
                xorq %rdx, %rdx
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_delete_headers
            .type kof_http_delete_headers, @function
            kof_http_delete_headers:
                movq %rsi, %rcx
                leaq .Lhttp_m_delete(%rip), %rsi
                xorq %rdx, %rdx
                jmp kof_http_core
            .globl kof_http_options
            .type kof_http_options, @function
            kof_http_options:
                leaq .Lhttp_m_options(%rip), %rsi
                xorq %rdx, %rdx
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_options_headers
            .type kof_http_options_headers, @function
            kof_http_options_headers:
                movq %rsi, %rcx
                leaq .Lhttp_m_options(%rip), %rsi
                xorq %rdx, %rdx
                jmp kof_http_core
            .globl kof_http_post
            .type kof_http_post, @function
            kof_http_post:
                movq %rsi, %rdx            # body
                leaq .Lhttp_m_post(%rip), %rsi
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_post_headers
            .type kof_http_post_headers, @function
            kof_http_post_headers:
                movq %rdx, %rcx            # headers
                movq %rsi, %rdx            # body
                leaq .Lhttp_m_post(%rip), %rsi
                jmp kof_http_core
            .globl kof_http_put
            .type kof_http_put, @function
            kof_http_put:
                movq %rsi, %rdx
                leaq .Lhttp_m_put(%rip), %rsi
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_post_headers_alias_put
            .globl kof_http_put_headers
            .type kof_http_put_headers, @function
            kof_http_put_headers:
                movq %rdx, %rcx
                movq %rsi, %rdx
                leaq .Lhttp_m_put(%rip), %rsi
                jmp kof_http_core
            .globl kof_http_patch
            .type kof_http_patch, @function
            kof_http_patch:
                movq %rsi, %rdx
                leaq .Lhttp_m_patch(%rip), %rsi
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_patch_headers
            .type kof_http_patch_headers, @function
            kof_http_patch_headers:
                movq %rdx, %rcx
                movq %rsi, %rdx
                leaq .Lhttp_m_patch(%rip), %rsi
                jmp kof_http_core
            # kof_http_status(url) -> int
            .globl kof_http_status
            .type kof_http_status, @function
            kof_http_status:
                leaq .Lhttp_m_get(%rip), %rsi
                xorq %rdx, %rdx
                xorq %rcx, %rcx
                call kof_http_core
                movq .Lhttp_last_status(%rip), %rax
                ret
            # §259: timeout REAL (era ret puro). Guarda segundos; 0 = sem
            # deadline (igual JVM: Duration.ZERO = infinito). Default 15s.
            .globl kof_http_timeout_set
            .type kof_http_timeout_set, @function
            kof_http_timeout_set:
                movslq %edi, %rdi
                testq %rdi, %rdi
                jge .Lhts_store
                xorq %rdi, %rdi
            .Lhts_store:
                movq %rdi, .Lhttp_timeout_s(%rip)
                ret
            # §259 fatia 2: retry REAL (era ret puro). Guarda N (max 0, igual
            # JVM); o core consome como N+1 tentativas.
            .globl kof_http_retry_set
            .type kof_http_retry_set, @function
            kof_http_retry_set:
                movslq %edi, %rdi
                testq %rdi, %rdi
                jge .Lhrs_store
                xorq %rdi, %rdi
            .Lhrs_store:
                movq %rdi, .Lhttp_retry_n(%rip)
                ret
            # §259 fatia 3 (pendente): circuit ainda no-op no nativo
            .globl kof_http_circuit_set
            .type kof_http_circuit_set, @function
            kof_http_circuit_set:
                ret

            .section .data
            .Lhttp_m_get: .asciz "GET"
            .Lhttp_m_post: .asciz "POST"
            .Lhttp_m_put: .asciz "PUT"
            .Lhttp_m_patch: .asciz "PATCH"
            .Lhttp_m_delete: .asciz "DELETE"
            .Lhttp_m_options: .asciz "OPTIONS"
            .section .text            """;
    }
}
