package dev.kof.compiler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * X5.3 (`D-X5-SURFACE`/`D-TYPE-VARIANCE`) — restrição de POSIÇÃO da variância
 * declaration-site. O cheque de atribuibilidade
 * ({@link TypeChecker#genericArgsCompatible}) sozinho NÃO preserva a solidez:
 * um `out T` num campo gravável (ou parâmetro) deixa a covariância escrever um
 * valor do tipo errado num alias, e um `in T` num retorno deixa a
 * contravariância expor um valor do tipo errado. Sem esta guarda a fatia
 * seria uma fachada insegura (Q7).
 *
 * <p>Regras v1 (conservadoras — ocorrência textual do nome do type-param em
 * qualquer ponto do tipo, inclusive aninhado):
 * <ul>
 *   <li><b>out T</b> — proibido em parâmetro de método/construtor e em campo
 *       GRAVÁVEL (classe); permitido em retorno e em componente de record/
 *       campo de interface (somente-leitura = posição de saída).</li>
 *   <li><b>in T</b> — proibido em retorno e em QUALQUER campo/componente
 *       (todo campo é lido = posição de saída); permitido em parâmetros.</li>
 * </ul>
 * Um type-param homônimo declarado pelo PRÓPRIO método sombreia o do tipo e
 * não dispara a guarda. A variança em posição de herança (type-args do
 * `extends`/`implements`) fica para X5.3b.
 */
final class VarianceChecks {

    private VarianceChecks() {}

    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_]");

    static void checkClass(SemanticAnalyzer sa, ClassDeclarationNode cls) {
        Map<String, String> vs = variances(cls.typeParameters());
        if (vs.isEmpty()) return;
        for (AstNode member : cls.members()) checkMember(sa, vs, member, false, List.of());
    }

    static void checkRecord(SemanticAnalyzer sa, RecordDeclarationNode rec) {
        Map<String, String> vs = variances(rec.typeParameters());
        if (vs.isEmpty()) return;
        // Componentes de record são somente-leitura (getter) → saída:
        // `in T` é proibido; `out T` é permitido.
        for (RecordComponentNode comp : rec.components()) {
            for (Map.Entry<String, String> e : vs.entrySet()) {
                if ("in".equals(e.getValue()) && occurs(comp.type(), e.getKey())) {
                    report(sa, comp.position(), e.getKey(), e.getValue(), "record component");
                }
            }
        }
        for (AstNode member : rec.members()) checkMember(sa, vs, member, true, List.of());
    }

    static void checkInterface(SemanticAnalyzer sa, InterfaceDeclarationNode iface) {
        Map<String, String> vs = variances(iface.typeParameters());
        if (vs.isEmpty()) return;
        for (AstNode member : iface.members()) checkMember(sa, vs, member, true, List.of());
    }

    private static void checkMember(SemanticAnalyzer sa, Map<String, String> vs, AstNode member,
                                    boolean readOnlyFields, List<String> localShadows) {
        if (member instanceof FieldDeclarationNode field) {
            // Campo de classe é gravável → `out` proibido; campo de record/
            // interface é somente-leitura → `out` permitido. `in` é sempre
            // proibido (o campo é lido).
            for (Map.Entry<String, String> e : vs.entrySet()) {
                if (localShadows.contains(e.getKey())) continue;
                if ("out".equals(e.getValue()) && readOnlyFields) continue;
                if (occurs(field.type(), e.getKey())) {
                    report(sa, field.position(), e.getKey(), e.getValue(), "field");
                }
            }
        } else if (member instanceof MethodDeclarationNode method) {
            // Metodos nao declaram type-params proprios nesta versao (apenas
            // funcoes top-level) — sem sombreamento a tratar aqui.
            for (Map.Entry<String, String> e : vs.entrySet()) {
                if (localShadows.contains(e.getKey())) continue;
                if ("out".equals(e.getValue())) {
                    for (FormalParameterNode p : method.parameters()) {
                        if (occurs(p.type(), e.getKey())) {
                            report(sa, p.position(), e.getKey(), e.getValue(), "parameter");
                        }
                    }
                } else {
                    if (occurs(method.returnType(), e.getKey())) {
                        report(sa, method.position(), e.getKey(), e.getValue(), "return");
                    }
                }
            }
        } else if (member instanceof ConstructorDeclarationNode ctor) {
            for (Map.Entry<String, String> e : vs.entrySet()) {
                if (localShadows.contains(e.getKey())) continue;
                if (!"out".equals(e.getValue())) continue;
                for (FormalParameterNode p : ctor.parameters()) {
                    if (occurs(p.type(), e.getKey())) {
                        report(sa, p.position(), e.getKey(), e.getValue(), "parameter");
                    }
                }
            }
        }
    }

    /** Type-params `out`/`in` (nome → variância) de uma declaração. */
    private static Map<String, String> variances(List<String> typeParameters) {
        Map<String, String> out = new LinkedHashMap<>();
        if (typeParameters == null) return out;
        for (String tp : typeParameters) {
            String v = TypeParams.variance(tp);
            if (!v.isEmpty()) out.put(TypeParams.name(tp), v);
        }
        return out;
    }

    /** O tipo textual {@code text} menciona o identificador {@code name}? */
    private static boolean occurs(String text, String name) {
        if (text == null || name == null || name.isEmpty()) return false;
        int from = 0;
        while (true) {
            int i = text.indexOf(name, from);
            if (i < 0) return false;
            boolean leftOk = i == 0 || !WORD.matcher(text.substring(i - 1, i)).matches();
            int end = i + name.length();
            boolean rightOk = end >= text.length() || !WORD.matcher(text.substring(end, end + 1)).matches();
            if (leftOk && rightOk) return true;
            from = i + 1;
        }
    }

    private static void report(SemanticAnalyzer sa, SourcePosition pos, String name,
                               String variance, String where) {
        if (sa.diagnostics() == null) return;
        String msg = "out".equals(variance)
                ? "type parameter '" + name + "' is declared 'out' but occurs in an input position ("
                        + where + ") — an 'out' parameter is covariant, so it may appear only in"
                        + " output positions (return types, read-only components); a writable use"
                        + " would let a covariant alias store a value of the wrong type"
                : "type parameter '" + name + "' is declared 'in' but occurs in an output position ("
                        + where + ") — an 'in' parameter is contravariant, so it may appear only in"
                        + " input positions (parameters); a readable use would let a contravariant"
                        + " alias expose a value of the wrong type";
        sa.diagnostics().error(pos != null ? pos.file() : "", pos != null ? pos.line() : 0,
                pos != null ? pos.column() : 0, 0, msg, "SEM082");
    }
}
