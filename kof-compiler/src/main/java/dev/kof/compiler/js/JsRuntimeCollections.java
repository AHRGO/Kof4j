package dev.kof.compiler.js;

/**
 * Runtime JS dos métodos de coleção novos (#382/#386): indexOf/lastIndexOf/
 * addAll/subList/sort sobre List (Array) e containsValue/putIfAbsent sobre
 * Map (Map com chave canônica kofMapKeyIdx, §104c). Bloco próprio (gate 500
 * no JsRuntimeUiLayout) registrado em JsRuntimeSlices; a poda por
 * alcançabilidade só puxa as unidades usadas.
 */
public final class JsRuntimeCollections {

    private JsRuntimeCollections() {}

    static String COLLECTIONS_RUNTIME = """
            export function kofListIndexOf(list, value) {
                for (let i = 0; i < list.length; i++) {
                    if (kofValEq(list[i], value)) return i;
                }
                return -1;
            }

            export function kofListLastIndexOf(list, value) {
                for (let i = list.length - 1; i >= 0; i--) {
                    if (kofValEq(list[i], value)) return i;
                }
                return -1;
            }

            export function kofListAddAll(list, other) {
                if (!other || other.length === 0) return 0;
                for (let i = 0; i < other.length; i++) list.push(other[i]);
                return 1;
            }

            export function kofListSubList(list, from, to) {
                if (from < 0 || to > list.length || from > to) {
                    throw new Error("Index out of bounds: " + from + " (size " + list.length + ")");
                }
                return list.slice(from, to);
            }

            function kofNaturalCmp(a, b) {
                if (typeof a === "number" && typeof b === "number") {
                    return a < b ? -1 : a > b ? 1 : 0;
                }
                if (typeof a === "bigint" && typeof b === "bigint") {
                    return a < b ? -1 : a > b ? 1 : 0;
                }
                if (typeof a === "string" && typeof b === "string") {
                    return a < b ? -1 : a > b ? 1 : 0;
                }
                if (typeof a === "boolean" && typeof b === "boolean") {
                    return (a === b) ? 0 : (a ? 1 : -1);
                }
                return 0;
            }

            export function kofListSort(list) {
                list.sort(kofNaturalCmp);
            }

            export function kofMapContainsValue(map, value) {
                for (const v of map.values()) {
                    if (kofValEq(v, value)) return 1;
                }
                return 0;
            }

            export function kofMapPutIfAbsent(map, key, value) {
                const k = kofMapKeyIdx(map, key);
                if (k !== undefined) {
                    const prev = map.get(k);
                    return prev === undefined ? null : prev;
                }
                map.set(key, value);
                return null;
            }
            """;
}
