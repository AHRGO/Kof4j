package dev.kof.compiler;

import dev.kof.compiler.runtime.RuntimeGc;
import dev.kof.compiler.runtime.RuntimeMemory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G-6b (frente 2 "GC auto-collect", causa (1) do §260 — 16/09): o mark do
 * x86 varre a pilha INTEIRA da thread main (rsp..kof_main_stack_bottom),
 * não apenas o frame corrente [rsp..rbp].
 *
 * <p>Medida com gdb 16/09 (§260): com o scan restrito ao frame, uma String
 * viva no frame de main enquanto um helper aloca era INVISÍVEL ao mark —
 * o sweep liberava vivo (keep corrompido / SIGSEGV no supervisor). Este
 * guard trava o formato do asm (as 3 metades do conserto), para o trigger
 * G-6 não ser religado sobre um mark que voltou a ser restrito:
 * 1) o campo .bss `kof_main_stack_bottom` existe (RuntimeMemory.emitAlloc);
 * 2) o corpo do kof_gc_mark lê o campo como limite alto da varredura, com
 *    cap 64MB e fallback sp..sp+4096 preservados (harness asm sem _start);
 * 3) o prologue `_start` (NativeMethodEmitter.emitStart) grava %rsp no campo
 *    antes do tid — coberto aqui pelo emit do alloc+gc; o lado _start é a
 *    asserção textual no corpo compartilhado via marker abaixo.
 *
 * <p>Q0: no código pré-G-6b, (1) não existe e o mark usa %rbp — o teste
 * falha nas três asserts. O gatilho completo (reuso sob pressão de memória)
 * é a face G-6(a), §260 — ainda aberta.
 */
class NativeX86GcMarkScopeTest {

    @Test
    void allocDeclaresMainThreadStackBottom() {
        StringBuilder sb = new StringBuilder();
        RuntimeMemory.emitAlloc(sb);
        String asm = sb.toString();
        assertTrue(asm.contains("kof_main_stack_bottom: .quad 0"),
                "campo .bss kof_main_stack_bottom ausente do emitAlloc (§260 G-6b)");
    }

    @Test
    void markScansWholeThreadStackNotCurrentFrame() {
        StringBuilder sb = new StringBuilder();
        RuntimeGc.emitGc(sb);
        String asm = sb.toString();
        int mark = asm.indexOf("kof_gc_mark:");
        assertTrue(mark >= 0, "kof_gc_mark ausente");
        int markEnd = asm.indexOf("kof_gc_try_mark", mark);
        String body = asm.substring(mark, markEnd > 0 ? markEnd : mark + 1500);

        assertTrue(body.contains("movq kof_main_stack_bottom(%rip), %r13"),
                "mark deve varrer ate kof_main_stack_bottom (pilha inteira), nao"
                        + " o frame corrente — corpo: " + body);
        assertTrue(body.contains("$67108864"),
                "cap anti-corrupcao de 64MB ausente no limite alto");
        assertTrue(body.contains(".Lgc_mark_stack_fallback"),
                "fallback sp..sp+4096 (harness asm sem _start) deve existir");
        assertFalse(body.contains("movq %rbp, %r13"),
                "mark nao pode usar %rbp (frame corrente) como limite — §260");
        // o outro lado da paridade: maquina manual intacta
        assertTrue(asm.contains("kof_gc_collect_now:"), "collect_now sumiu");
        assertTrue(asm.contains("kof_gc_sweep:"), "sweep sumiu");
    }

    @Test
    void startRecordsStackBottomBeforeAnyUse() throws Exception {
        // O _start e emitido por NativeMethodEmitter.emitStart (package-private,
        // exige IRClass real) — o guard roda na FONTE: a gravação do fundo da
        // pilha deve existir e vir ANTES da gravação do tid no bloco _start.
        Path base = Path.of(System.getProperty("user.dir"),
                "src/main/java/dev/kof/compiler/nat/NativeMethodEmitter.java");
        String src = Files.readString(base);
        int startBlock = src.indexOf("\"_start:\\n\"");
        assertTrue(startBlock >= 0, "bloco _start ausente do emissor");
        String block = src.substring(startBlock, Math.min(src.length(), startBlock + 1600));
        int b = block.indexOf("movq %rsp, kof_main_stack_bottom(%rip)");
        int t = block.indexOf("kof_main_tid");
        assertTrue(b >= 0, "emitStart deve gravar o fundo da pilha no _start (§260 G-6b)");
        assertTrue(t >= 0 && b < t, "o fundo da pilha deve ser gravado ANTES do tid");
    }
}
