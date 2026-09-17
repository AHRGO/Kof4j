package dev.kof.compiler.nat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * D-DIAG-EN: as strings de runtime do ASM nativo (.asciz, kof.http) sao tooling —
 * chegam ao stderr do usuario final. Este teste extrai o assembly real dos emissores
 * (sem toolchain/qemu) e trava o idioma English: presenca da frase EN + ausencia do
 * PT antigo. Varredura por diacritico sozinha perdeu "connect falhou"/"nao suportado"
 * (PT sem acento) — o scan final obrigatorio e por LISTA-DE-PALAVRAS.
 */
class NativeHttpRuntimeLanguageTest {

    @Test
    void httpRuntimeStringsAreEnglish() {
        StringBuilder rb = new StringBuilder();
        NativeRiscvHttpCore.emit(rb);
        String asm = NativeHttpCore.source() + NativeHttpPrimitives.source() + rb;
        assertTrue(asm.contains("\"kof.http: connect failed\""), "connect EN");
        assertTrue(
                asm.contains("\"kof.http: https not supported on Native (TLS pending); use http://\""),
                "https EN (gap honesto; codigo HTTP002/TLS preservado na frase)");
        for (String pt : List.of("connect falhou", "nao suportado no Native", "TLS pendente")) {
            assertFalse(stringsOf(asm).contains(pt), "PT morto em literal .asciz http: " + pt);
        }
    }

    /** Concatena somente o conteudo dos literais .asciz (a superficie que o usuario
     *  ve); comentarios asm (# ...) nao contam para a ausencia de PT. */
    private static String stringsOf(String asm) {
        StringBuilder only = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\.asciz\\s+\"([^\"]*)\"").matcher(asm);
        while (m.find()) only.append(m.group(1)).append('\n');
        return only.toString();
    }
}
