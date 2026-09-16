package dev.kof.compiler.nat;

// S13b (plan-stdlib-expansion §2, P0): kof_string_to_*_or_default riscv64 —
// briefing §43 ("falha de parse = OrNull/OrDefault"). Wrapper sobre os
// parseadores EXISTENTES (B30 int/long bug 79; B31 double/float bug 82; regra
// 2, zero parse novo): instala um handler no kof_exc_chain (MESMO mecanismo
// do try/catch Kof — KofTryStart/KofCatchStart em NativeRiscvCrossEmit, frame
// de 32 bytes handler/sp/s11/chain; kof_throw_string desempilha com jr t4),
// chama kof_string_to_* e, se o parse lançar, o handler restaura a chain e
// DEVOLVE o default (nunca lança — paridade com JvmStringCoreRuntime
// try/catch e wrappers JS/x86 RuntimeStringParseOrDefault). Convenções de
// retorno cross (B31): Int/Long = a0; Double = bits crus em a0. aarch64
// herda linha-a-linha no tradutor (conjunto la/ld/sd/jr/beqz já suportado).
// NOTA lane: B34–B39 são de OUTRAS lanes (UTF-16 strings, §97/§111, double
// mod) — este é o B41 (regra 8: nunca reciclar número de fatia alheia).
public final class NativeRiscvAsmRtB41 {

    private NativeRiscvAsmRtB41() {}

    private static  String TEMPLATE = """
            .globl %1$s
            %1$s:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                # frame do handler: [0]=handler [8]=sp [16]=s11(rbp) [24]=chain antigo
                la   t0, %2$s_handler
                sd   t0, 0(sp)
                sd   sp, 8(sp)
                sd   s11, 16(sp)
                la   t1, kof_exc_chain
                ld   t2, 0(t1)
                sd   t2, 24(sp)
                sd   sp, 0(t1)
                # args: a0 = String (já pronto); default (a1) salvo no frame
                sd   a1, 24(sp)
                call %3$s
                # sucesso: restaura a chain e devolve a0
                la   t1, kof_exc_chain
                ld   t2, 24(sp)
                sd   t2, 0(t1)
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                addi sp, sp, 48
                ret
            %2$s_handler:
                # chega com a String do erro em a0; devolve o default
                la   t1, kof_exc_chain
                ld   t2, 24(sp)
                sd   t2, 0(t1)
                ld   a0, 24(sp)
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                addi sp, sp, 48
                ret
            """;

    static  String RISCV_RUNTIME_ASM_B_41 = """
            .section .text

            # kof_string_to_int_or_default(str, def) -> Int (contrato §43:
            # falha DEVOLVE def; paridade JvmStringCoreRuntime/wrappers JS/x86)
            """ + String.format(TEMPLATE, "kof_string_to_int_or_default", ".Lstiod", "kof_string_to_int") + """

            # kof_string_to_long_or_default(str, def) -> Long (idem, B30 base)
            """ + String.format(TEMPLATE, "kof_string_to_long_or_default", ".Lstlod", "kof_string_to_long") + """

            # kof_string_to_double_or_default(str, def) -> Double (bits crus em
            # a0; base = B31 .Lpd). Default chega como bits crus em a1.
            """ + String.format(TEMPLATE, "kof_string_to_double_or_default", ".Lstdod", "kof_string_to_double") + """
            .section .text
            """;
}
