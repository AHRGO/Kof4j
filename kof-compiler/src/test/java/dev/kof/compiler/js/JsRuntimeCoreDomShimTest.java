package dev.kof.compiler.js;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #121: o shim de elemento DOM (kofMakeEl, usado quando `document` não existe
 * — hostless/SSR) não tinha dataset/disabled/classList. Roda o CORE_RUNTIME
 * puro num Context GraalJS sem document real (mesma condição do shim:
 * `typeof document === "undefined"`) e exercita a API adicionada direto,
 * sem precisar compilar um programa Kof completo. CORE_RUNTIME tem `export`
 * (ES module) mais adiante, então precisa ser avaliado como módulo — igual
 * ao artefato .mjs real (mimeType application/javascript+module).
 */
class JsRuntimeCoreDomShimTest {

    private Value createShimElement(Context ctx) throws IOException {
        Source core = Source.newBuilder("js", JsRuntimeCore.CORE_RUNTIME, "core.mjs")
                .mimeType("application/javascript+module")
                .build();
        ctx.eval(core);
        return ctx.eval("js", "document.createElement('div')");
    }

    @Test
    void datasetAndDisabledDefaultPresent() throws IOException {
        try (Context ctx = Context.newBuilder("js").allowAllAccess(true).build()) {
            Value el = createShimElement(ctx);
            assertTrue(el.getMember("dataset").hasMembers() || el.getMember("dataset") != null,
                    "dataset deve existir no elemento shim");
            assertFalse(el.getMember("disabled").asBoolean(), "disabled deve começar false");
        }
    }

    @Test
    void classListAddContainsToggleRemove() throws IOException {
        try (Context ctx = Context.newBuilder("js").allowAllAccess(true).build()) {
            createShimElement(ctx);
            Value result = ctx.eval("js", """
                    (function () {
                        const el = document.createElement('div');
                        el.classList.add('active');
                        const afterAdd = el.classList.contains('active');
                        const toggledOff = el.classList.toggle('active');
                        const containsAfterToggleOff = el.classList.contains('active');
                        const toggledOn = el.classList.toggle('active');
                        el.classList.remove('active');
                        const afterRemove = el.classList.contains('active');
                        return JSON.stringify({afterAdd, toggledOff, containsAfterToggleOff, toggledOn, afterRemove});
                    })()
                    """);
            String json = result.asString();
            assertTrue(json.contains("\"afterAdd\":true"), json);
            assertTrue(json.contains("\"toggledOff\":false"), json);
            assertTrue(json.contains("\"containsAfterToggleOff\":false"), json);
            assertTrue(json.contains("\"toggledOn\":true"), json);
            assertTrue(json.contains("\"afterRemove\":false"), json);
        }
    }

    @Test
    void datasetAndDisabledAreMutable() throws IOException {
        try (Context ctx = Context.newBuilder("js").allowAllAccess(true).build()) {
            createShimElement(ctx);
            Value result = ctx.eval("js", """
                    (function () {
                        const el = document.createElement('button');
                        el.dataset.testId = 'save-btn';
                        el.disabled = true;
                        return JSON.stringify({id: el.dataset.testId, disabled: el.disabled});
                    })()
                    """);
            String json = result.asString();
            assertTrue(json.contains("\"id\":\"save-btn\""), json);
            assertTrue(json.contains("\"disabled\":true"), json);
        }
    }
}
