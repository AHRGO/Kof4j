package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * D-ENUM207 (#207, §211) — lowering de {@code enum} para uma CLASSE real.
 *
 * <p>Antes o valor de enum era uma String: {@code Dir.N} virava {@code ldc "N"},
 * nenhuma {@code Dir.class} era emitida, {@code getClass()} devolvia
 * {@code java.lang.String} e {@code instanceof Dir} baixava para
 * {@code instanceof java/lang/String}. Esta unidade torna a constante uma
 * INSTÂNCIA de enum real, emitindo a classe com os ops genéricos de classe que
 * os 4 alvos já suportam (campos estáticos + {@code <clinit>} + métodos) — sem
 * ABI novo por target (freeze regra 5, sem half-landing §241).
 *
 * <p>A classe NÃO estende {@code java/lang/Enum}: um super de plataforma só
 * existiria no JVM e forçaria gate por target (precedente record/§53). Os
 * métodos que a linguagem expõe ({@code name}/{@code ordinal}/{@code toString}
 * /{@code compareTo}) são emitidos aqui; {@code values()}/{@code valueOf()}
 * são baixados no ponto de uso (instâncias reais).
 */
public final class CompilerEnumLowering {

    private CompilerEnumLowering() {}

    /** Campo de instância que guarda o nome textual da constante. */
    static final String NAME_FIELD = "kofName";
    /** Campo de instância que guarda o ordinal da constante. */
    static final String ORDINAL_FIELD = "kofOrdinal";

    static IRClass lowerEnum(CompilerDriver driver, EnumDeclarationNode en, int typeId) {
        String internalName = driver.toInternalName("", en.name());
        Type enumType = new Type.ClassType("", en.name(), List.of());
        int access = driver.computeAccess(en.modifiers())
                | AccessFlags.FINAL | AccessFlags.ENUM;
        List<String> constants = en.constants();

        List<IRField> fields = new ArrayList<>();
        fields.add(new IRField(NAME_FIELD, BuiltinTypes.STRING,
                AccessFlags.PRIVATE | AccessFlags.FINAL, null, List.of()));
        fields.add(new IRField(ORDINAL_FIELD, Type.PrimitiveType.INT,
                AccessFlags.PRIVATE | AccessFlags.FINAL, null, List.of()));
        for (String c : constants) {
            fields.add(new IRField(c, enumType,
                    AccessFlags.PUBLIC | AccessFlags.STATIC | AccessFlags.FINAL | AccessFlags.ENUM,
                    null, List.of()));
        }

        List<IRMethod> methods = new ArrayList<>();
        methods.add(constructor(enumType));
        methods.add(accessor(enumType, "name", BuiltinTypes.STRING, NAME_FIELD));
        methods.add(accessor(enumType, "ordinal", Type.PrimitiveType.INT, ORDINAL_FIELD));
        methods.add(accessor(enumType, "toString", BuiltinTypes.STRING, NAME_FIELD));
        methods.add(compareTo(enumType));
        methods.add(values(enumType, constants));
        methods.add(valueOf(enumType, constants));
        methods.add(clinit(enumType, constants));

        return new IRClass(internalName, "java/lang/Object", List.of(), access,
                fields, methods, List.of(), null, typeId,
                CompilerAnnotations.lowerAnnotations(driver, en.annotations()));
    }

    /** {@code <init>(String name, Int ordinal)} — privado, como no Java. */
    private static IRMethod constructor(Type enumType) {
        List<KofOperation> ops = new ArrayList<>();
        ops.add(new KofLoadLocal(enumType, 0));
        ops.add(new KofLoadLocal(BuiltinTypes.STRING, 1));
        ops.add(new KofStoreField(enumType, NAME_FIELD, BuiltinTypes.STRING));
        ops.add(new KofLoadLocal(enumType, 0));
        ops.add(new KofLoadLocal(Type.PrimitiveType.INT, 2));
        ops.add(new KofStoreField(enumType, ORDINAL_FIELD, Type.PrimitiveType.INT));
        ops.add(new KofReturnVoid());
        List<IRLocalVariable> locals = List.of(
                new IRLocalVariable(0, "this", enumType),
                new IRLocalVariable(1, "name", BuiltinTypes.STRING),
                new IRLocalVariable(2, "ordinal", Type.PrimitiveType.INT));
        return new IRMethod("<init>", Type.PrimitiveType.VOID,
                List.of(BuiltinTypes.STRING, Type.PrimitiveType.INT),
                AccessFlags.PRIVATE, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    /** {@code name()}/{@code ordinal()}/{@code toString()} — leem o campo. */
    private static IRMethod accessor(Type enumType, String name, Type returnType, String field) {
        List<KofOperation> ops = new ArrayList<>();
        ops.add(new KofLoadLocal(enumType, 0));
        ops.add(new KofLoadField(enumType, field, returnType));
        ops.add(new KofReturn(returnType));
        return new IRMethod(name, returnType, List.of(), AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ops)),
                List.of(new IRLocalVariable(0, "this", enumType)));
    }

    /** {@code compareTo(other)} — diferença de ordinal. */
    private static IRMethod compareTo(Type enumType) {
        List<KofOperation> ops = new ArrayList<>();
        ops.add(new KofLoadLocal(enumType, 0));
        ops.add(new KofLoadField(enumType, ORDINAL_FIELD, Type.PrimitiveType.INT));
        ops.add(new KofLoadLocal(enumType, 1));
        ops.add(new KofLoadField(enumType, ORDINAL_FIELD, Type.PrimitiveType.INT));
        ops.add(new KofBinary(KofBinaryOp.SUB, Type.PrimitiveType.INT));
        ops.add(new KofReturn(Type.PrimitiveType.INT));
        return new IRMethod("compareTo", Type.PrimitiveType.INT, List.of(enumType),
                AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ops)),
                List.of(new IRLocalVariable(0, "this", enumType),
                        new IRLocalVariable(1, "other", enumType)));
    }

    /** {@code values()} — {@code List<Dir>} com as instâncias (getstatic). */
    private static IRMethod values(Type enumType, List<String> constants) {
        Type listType = new Type.ClassType("kof", "List", List.of(enumType));
        List<KofOperation> ops = new ArrayList<>();
        ops.add(new KofCall(listType, "kof_list_new", List.of(), listType, KofCallKind.FUNCTION));
        ops.add(new KofStoreLocal(listType, 0));
        for (String c : constants) {
            ops.add(new KofLoadLocal(listType, 0));
            ops.add(new KofGetStatic(enumType, c, enumType));
            ops.add(new KofCall(listType, "kof_list_add", List.of(enumType),
                    Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
        }
        ops.add(new KofLoadLocal(listType, 0));
        ops.add(new KofReturn(listType));
        return new IRMethod("values", listType, List.of(),
                AccessFlags.PUBLIC | AccessFlags.STATIC, List.of(),
                List.of(new IRBasicBlock(0, ops)),
                List.of(new IRLocalVariable(0, "#list", listType)));
    }

    /**
     * {@code valueOf(String)} — devolve a instância cujo nome casa, ou null.
     * Cada constante é um early-return (`getstatic` + `return`), o padrão de
     * retorno que os 4 backends já traduzem.
     */
    private static IRMethod valueOf(Type enumType, List<String> constants) {
        List<KofOperation> ops = new ArrayList<>();
        for (String c : constants) {
            LabelId match = LabelId.create();
            LabelId next = LabelId.create();
            ops.add(new KofLoadLocal(BuiltinTypes.STRING, 0));
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, c));
            ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_equals",
                    List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                    Type.PrimitiveType.BOOL, KofCallKind.FUNCTION));
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
            ops.add(new KofConditionalJump(KofComparison.NE, match, next));
            ops.add(new KofLabel(match));
            ops.add(new KofGetStatic(enumType, c, enumType));
            ops.add(new KofReturn(enumType));
            ops.add(new KofLabel(next));
        }
        ops.add(new KofLoadLiteral(Type.UnknownType.UNKNOWN, null));
        ops.add(new KofReturn(enumType));
        return new IRMethod("valueOf", enumType, List.of(BuiltinTypes.STRING),
                AccessFlags.PUBLIC | AccessFlags.STATIC, List.of(),
                List.of(new IRBasicBlock(0, ops)),
                List.of(new IRLocalVariable(0, "name", BuiltinTypes.STRING)));
    }

    /** {@code <clinit>} — cria e publica cada constante na ordem declarada. */    private static IRMethod clinit(Type enumType, List<String> constants) {
        List<KofOperation> ops = new ArrayList<>();
        for (int i = 0; i < constants.size(); i++) {
            String c = constants.get(i);
            ops.add(new KofNewObject(enumType,
                    List.of(BuiltinTypes.STRING, Type.PrimitiveType.INT)));
            ops.add(new KofDup());
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, c));
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, i));
            ops.add(new KofCall(enumType, "<init>",
                    List.of(BuiltinTypes.STRING, Type.PrimitiveType.INT),
                    Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
            ops.add(new KofPutStatic(enumType, c, enumType));
        }
        ops.add(new KofReturnVoid());
        return new IRMethod("<clinit>", Type.PrimitiveType.VOID, List.of(),
                AccessFlags.STATIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), List.of());
    }
}
