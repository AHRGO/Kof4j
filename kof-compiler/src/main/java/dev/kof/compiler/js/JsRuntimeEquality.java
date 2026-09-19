package dev.kof.compiler.js;

/** Slice de igualdade do kof-runtime: conteúdo de coleções, records e fp boxed. */
final class JsRuntimeEquality {
    private JsRuntimeEquality() {
    }

    // Extraído de JsRuntimeCore (fatia própria, regra ≤500): os três helpers
    // de igualdade — kofValEq (conteúdo de coleção/objeto), kofRecordEq
    // (null-safe 1/0 p/ == de record) e kofFpEq (contrato do wrapper JVM em
    // float/double boxed). Consumidos por equals de record (JsClassEmitter),
    // contains/indexOf de coleção e busca de chave de mapa (JsRuntimeUiLayout).
    static final String EQUALITY_RUNTIME = """
            // §104c: igualdade de conteúdo em coleções JS. Primitivos/String
            // resolvem no caminho nativo (SameValueZero) — o fallback pega
            // objeto Kof (record) com .equals(other) sintético → 1/0, ou
            // List/Set por conteúdo (#518). Assim `listOf(p1).contains(p2)`,
            // `setOf/mapOf` por conteúdo batem com o oracle JVM sem alterar
            // o armazenamento nem o kofFormat.
            export function kofValEq(a, b) {
                if (a === b) return true;
                if (Number.isNaN(a) && Number.isNaN(b)) return true;
                if (a === null || b === null || a === undefined || b === undefined) return false;
                // #518: List (JS Array) por conteudo = AbstractList.equals da JVM
                // (mesmo tamanho, elemento a elemento, ordem importa). Recorre
                // kofValEq (elemento pode ser record/lista/set aninhado).
                if (Array.isArray(a) && Array.isArray(b)) {
                    if (a.length !== b.length) return false;
                    for (let i = 0; i < a.length; i++) {
                        if (!kofValEq(a[i], b[i])) return false;
                    }
                    return true;
                }
                // #518: Set (JS Set) por conteudo = AbstractSet.equals da JVM
                // (mesmo tamanho + cada elemento de um pertence ao outro). NAO
                // usar .has: SameValueZero compara refs — e exatamente o bug.
                if (a instanceof Set && b instanceof Set) {
                    if (a.size !== b.size) return false;
                    for (const x of a) {
                        let found = false;
                        for (const y of b) {
                            if (kofValEq(x, y)) { found = true; break; }
                        }
                        if (!found) return false;
                    }
                    return true;
                }
                if (typeof a === "object" && typeof a.equals === "function") {
                    return a.equals(b) ? true : false;
                }
                return false;
            }

            // §262(b): igualdade de record null-safe p/ `==`/`!=` no JS (Objects.equals).
            // Devolve NÚMERO 1/0 (a dobra JS do `!=` é `(x === 0)`, que só funciona
            // com número). Guarda os DOIS lados: o `equals` sintético do record NÃO
            // guarda o ARG (this._x === other._x → TypeError se other null).
            export function kofRecordEq(a, b) {
                if (a === b) return 1;
                if (a === null || a === undefined || b === null || b === undefined) return 0;
                if (typeof a.equals === "function") return a.equals(b) ? 1 : 0;
                return 0;
            }

            // #259: igualdade de `Float?`/`Double?`. O `===` diverge do wrapper
            // JVM (oráculo) em 2 casos: `NaN === NaN` false (JVM true) e
            // `0.0 === -0.0` true (JVM false). `Object.is` casa com o wrapper
            // nesses 2 e é idêntico ao `===` no resto. Só entra com tipo
            // ESTÁTICO float/double; Int/Long seguem kofRecordEq (o `-0` de
            // `0 * -1` seria falso-negativo espúrio em aritmética inteira).
            export function kofFpEq(a, b) {
                if (a === null || a === undefined || b === null || b === undefined) return a === b ? 1 : 0;
                if (typeof a === "number" && typeof b === "number") return Object.is(a, b) ? 1 : 0;
                if (typeof a.equals === "function") return a.equals(b) ? 1 : 0;
                return 0;
            }
            """;
}
