package dev.kof.compiler.js;

/** kof-runtime.mjs — decode de mapa JSON (§103.1, GitHub #103). */
public final class JsRuntimeUiJsonMap {
    private JsRuntimeUiJsonMap() {
    }

    static final String JSON_MAP_RUNTIME = """
            // §103.1 (#103): decode<Map<String,T>> no JS — monta um Map
            // real (o else dava objeto puro: m.size/m.get quebravam); com
            // decoder, cada valor é bindado à classe.
            export function kofJsonDecodeMap(obj) {
                const m = new Map();
                if (obj && typeof obj === 'object')
                    for (const [k, v] of Object.entries(obj)) m.set(k, v);
                return m;
            }

            export function kofJsonDecodeObjectMap(obj, decoder) {
                const m = new Map();
                if (obj && typeof obj === 'object')
                    for (const [k, v] of Object.entries(obj)) m.set(k, decoder(v));
                return m;
            }

            """;

}
