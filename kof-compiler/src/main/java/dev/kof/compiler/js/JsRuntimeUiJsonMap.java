package dev.kof.compiler.js;

/** kof-runtime.mjs — decode de mapa JSON (§103.1, GitHub #103). */
public final class JsRuntimeUiJsonMap {
    private JsRuntimeUiJsonMap() {
    }

    static  String JSON_MAP_RUNTIME = """
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

            // §106 residual (13/09): encode<Map<String,T>> no JS. JSON.stringify
            // num `Map` devolve '{}' (Map não tem own enumerable properties) —
            // o §106 declarou "4 backends" mas NUNCA testou o JS (matriz não
            // cobria json.encode(Map)). Aqui montamos o objeto com chaves
            // SORTED (mesma semântica JVM/nativo/interp, decisão 2b) via
            // JSON.stringify por valor (escapa string/número/bool/null
            // corretamente; `tag` fica na assinatura por simetria).
            export function kofJsonEncodeMap(map, tag) {
                const keys = [...map.keys()].sort();
                const parts = [];
                for (const k of keys) {
                    parts.push(JSON.stringify(String(k)) + ':' + JSON.stringify(map.get(k)));
                }
                return '{' + parts.join(',') + '}';
            }

            """;

}
