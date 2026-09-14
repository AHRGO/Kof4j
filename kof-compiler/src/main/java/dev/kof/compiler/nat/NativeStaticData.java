package dev.kof.compiler.nat;

import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRField;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofGetStatic;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofPutStatic;
import dev.kof.compiler.Type;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emissão do segmento {@code .data} de CAMPOS ESTÁTICOS do backend nativo
 * (bug 41) — símbolo, valor inicial e slots. Extraída do {@code NativeBackend}
 * (gate ≤500, ratchet §140): a tabela de símbolos vive no backend (estado
 * compartilhado com os emitters); aqui fica a RESPONSABILIDADE de nomear,
 * coletar e renderizar os slots (strings estáticas = objetos Kof com
 * header+length+chars, não `.asciz`).
 */
final class NativeStaticData {

    private final NativeBackend nb;

    /** Chave normalizada do dono (internal name) para o símbolo estático. */
    final Map<String, String> symbols = new LinkedHashMap<>();
    final Map<String, Object> values = new LinkedHashMap<>();

    NativeStaticData(NativeBackend nb) { this.nb = nb; }

    /** Registra um campo estático e devolve o símbolo .data (bug 41). */
    String symbol(String ownerKey, String fieldName) {
        return symbol(ownerKey, fieldName, null);
    }

    String symbol(String ownerKey, String fieldName, Object initialValue) {
        String key = ownerKey + "|" + fieldName;
        String label = symbols.get(key);
        if (label == null) {
            label = "kof_static_" + NativeSymbolMangling.sanitizeNameStatic(ownerKey)
                    + "_" + NativeSymbolMangling.sanitizeNameStatic(fieldName);
            symbols.put(key, label);
        }
        if (initialValue != null && !values.containsKey(key)) {
            values.put(key, initialValue);
        }
        return label;
    }

    /** Chave normalizada do dono (internal name) para o símbolo estático. */
    String key(Type ownerType) {
        if (ownerType instanceof Type.ClassType ct) return ct.internalName();
        return ownerType.toString();
    }

    /** Coleta os campos estáticos de todas as classes E operações (bug 41). */
    void collect() {
        for (IRClass clazz : nb.allClassesMap.values()) {
            for (IRField field : clazz.fields()) {
                if ((field.accessFlags() & dev.kof.compiler.AccessFlags.STATIC) != 0) {
                    symbol(clazz.name(), field.name(), field.initialValue());
                }
            }
            for (IRMethod method : clazz.methods()) {
                for (var block : method.basicBlocks()) {
                    for (KofOperation op : block.operations()) {
                        if (op instanceof KofGetStatic gs) symbol(key(gs.ownerType()), gs.name());
                        else if (op instanceof KofPutStatic ps) symbol(key(ps.ownerType()), ps.name());
                    }
                }
            }
        }
    }

    /** Emite os slots dos campos estáticos no .data (um .quad por campo). */
    void emit(StringBuilder sb) {
        for (Map.Entry<String, String> e : symbols.entrySet()) {
            Object v = values.get(e.getKey());
            if (v instanceof String s) {
                // strings estáticas são OBJETOS Kof (header+length+chars), não
                // `.asciz` — o kof_print_string lê length@16 e chars@24.
                String objLabel = e.getValue() + "_obj";
                emitStringObject(sb, objLabel, s);
                sb.append(e.getValue()).append(": .quad ").append(objLabel).append("\n");
            } else {
                sb.append(e.getValue()).append(": .quad ").append(initialText(v)).append("\n");
            }
        }
    }

    private void emitStringObject(StringBuilder sb, String label, String s) {
        String bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + "";
        String escaped = s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\t", "\\t");
        sb.append(label).append(":\n");
        sb.append("    .long 1\n");
        sb.append("    .long 0\n");
        sb.append("    .quad 0\n");
        sb.append("    .long ").append(bytes).append("\n");
        sb.append("    .long 0\n");
        sb.append("    .ascii \"").append(escaped).append("\"\n");
        sb.append("    .byte 0\n");
        sb.append("    .balign 8\n");
    }

    private String initialText(Object v) {
        if (v == null) return "0";
        if (v instanceof Boolean b) return b ? "1" : "0";
        if (v instanceof Integer i) return Integer.toString(i);
        if (v instanceof Long l) return Long.toString(l);
        if (v instanceof Double d) return "0x" + Long.toHexString(Double.doubleToLongBits(d));
        if (v instanceof Float f) return "0x" + Long.toHexString(Double.doubleToLongBits(f.doubleValue()));
        return "0";
    }
}
