package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * §279 (KofJS ICE): um `if` sobre o null de um PRIMITIVO nulavel cuja condicao o
 * otimizador dobrava deixava o marcador `KofStatementIf` (§267) ORFAO — sem o
 * `KofConditionalJump` que ele anotava — e o dispatcher JS abortava com
 * `COMP002 unexpected op in expression statement`, enquanto JVM/Native/Script
 * compilavam. A dobra foi eliminada pelo fix do #278 (D-NULL-INTENT, `495445cd`,
 * "ramo null de if") ao tornar o teste de null uma checagem REAL em vez de uma
 * dobra literal, e isso fechou o ICE do §279 de brinde — mas sem um pin dedicado
 * para as DUAS formas medidas. Este teste trava essas duas formas COMPILANDO e
 * RODANDO corretamente no JS (golden "n"), de modo que a regressao do marcador
 * orfao nao volte silenciosa (Q0/Q7). Escopo: o ICE do backend JS — o alvo que
 * abortava. O erro de RUNTIME do JVM para `Int? > 0` (relacional sobre primitivo
 * nulavel) e um bug SEPARADO (familia D-NULL-INTENT / #278 / #438, dono = lane do
 * compilador .22); por isso aqui se afirma o EXITO de COMPILACAO dos dois alvos e
 * o resultado de RUNTIME so do JS, nao do JVM.
 */
class JsNullablePrimitiveIfIceTest {

    private final CompilerDriver driver = new CompilerDriver();

    // Forma (a): null-check de Int? numa cadeia else-if (o gatilho KofStatementIf).
    private static final String ELSE_IF_KOF = """
            String probe(Int? v) {
                if (v == null) { return "n" } else if (v > 0) { return "e" } else { return "z" }
            }
            main() { println(probe(null)) }
            """;

    // Forma (b): null-check de Boolean? num if SEM else, como ultimo statement de void
    // (o gatilho KofReturnVoid).
    private static final String VOID_TAIL_KOF = """
            void probe(Boolean? flag) {
                if (flag == null) { println("n") }
            }
            main() { probe(null) }
            """;

    @Test
    void nullablePrimitiveElseIfCompilesOnJs(@TempDir Path dir) throws Exception {
        assertJsCompilesAndJvmCompiles(dir, "elseif", ELSE_IF_KOF);
    }

    @Test
    void nullablePrimitiveVoidTailCompilesOnJs(@TempDir Path dir) throws Exception {
        assertJsCompilesAndJvmCompiles(dir, "voidtail", VOID_TAIL_KOF);
    }

    @Test
    void nullablePrimitiveElseIfRunsCorrectlyOnJs(@TempDir Path dir) throws Exception {
        assertEquals("n", jsRun(dir, "elseif-run", ELSE_IF_KOF),
                "JS deve rodar o if dobrado do §279 e imprimir o ramo correto");
    }

    @Test
    void nullablePrimitiveVoidTailRunsCorrectlyOnJs(@TempDir Path dir) throws Exception {
        assertEquals("n", jsRun(dir, "voidtail-run", VOID_TAIL_KOF));
    }

    private void assertJsCompilesAndJvmCompiles(Path dir, String tag, String kof)
            throws IOException {
        Path jsSrc = dir.resolve(tag + ".kf");
        Files.writeString(jsSrc, kof);
        CompilationResult rjs = driver.compile(jsSrc, dir.resolve("out-" + tag + "-js"), Target.JS);
        assertFalse(iceIn(rjs), () -> tag + ": ICE COMP002 no JS voltou (§279): "
                + rjs.diagnostics().getDiagnostics());
        assertTrue(rjs.success(), () -> tag + ": JS deve compilar o if dobrado (§279): "
                + rjs.diagnostics().getDiagnostics());
        // O outro alvo que NAO era o do ICE segue compilando (regressao de um so lado).
        Path jvmSrc = dir.resolve(tag + "-jvm.kf");
        Files.writeString(jvmSrc, kof);
        CompilationResult rjvm = driver.compile(jvmSrc, dir.resolve("out-" + tag + "-jvm"), Target.JVM);
        assertTrue(rjvm.success(), () -> tag + ": JVM deve compilar: "
                + rjvm.diagnostics().getDiagnostics());
    }

    private String jsRun(Path dir, String tag, String kof) throws IOException {
        Path src = dir.resolve(tag + ".kf");
        Files.writeString(src, kof);
        CompilationResult r = driver.compile(src, dir.resolve("out-" + tag), Target.JS);
        assertTrue(r.success(), () -> tag + ": JS deve compilar (§279): " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(dir.resolve("out-" + tag).resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, ec, tag + ": JS exit code, output: " + out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
    }

    private boolean iceIn(CompilationResult r) {
        return r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "COMP002".equals(d.code()) || "unexpected op in expression statement".equals(d.message()));
    }
}
