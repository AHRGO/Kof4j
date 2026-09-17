package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.*;

import dev.kof.compiler.vk.VkChain64Asm;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * D-DIAG-EN: as strings .asciz do runtime Vulkan viram mensagem de stderr do
 * usuario. Varredura por diacritico sozinha perdeu "simbolo faltando"/"sem compute
 * queue" (PT sem acento) — o teste extrai o ASM real e trava EN + ausencia-PT.
 */
class VkRuntimeAsmLanguageTest {

    @Test
    void vkRuntimeStringsAreEnglish() {
        StringBuilder sb = new StringBuilder();
        RuntimeVk.emitVkStubs(sb);
        String asm = sb.toString() + VkChain64Asm.source();
        assertTrue(asm.contains("\"libvkchain: missing symbol (GPU003)\""), "GPU003 EN");
        assertTrue(asm.contains("\"libvkchain: init failed (GPU004)\""), "GPU004 EN");
        assertTrue(asm.contains("\"libvkchain loaded\""), "loaded EN");
        assertTrue(asm.contains("\"no physical device\""), "device EN");
        assertTrue(asm.contains("\"no compute queue\""), "queue EN");
        assertTrue(asm.contains("\"spv read failed\""), "spv EN");
        assertTrue(asm.contains("\"no host-visible memory\""), "mem EN");
        for (String pt : List.of("simbolo faltando", "init falhou", "carregada",
                "nenhum physical", "sem compute queue", "spv leitura", "sem mem")) {
            assertFalse(asm.contains(pt), "PT morto no ASM vk: " + pt);
        }
    }
}
