package dev.kof.compiler.nat;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.Type;

/**
 * FASE 3 (REFACTOR-500): emissão x86_64 de calls (println/print, box/unbox,
 * valueOf, construtores, dispatch virtual/interface, channel/list, calls
 * nativas kof_*). Extraído verbatim de NativeBackend.emitCall; os dois
 * helpers que ainda vivem no backend (resolveCalleeName,
 * findVirtualMethodIndex) são acessados via campo nb.
 */
public final class NativeX86Calls {

    /** §107: tag de elemento/vetor de coleção → argumento do
     *  kof_{list,set,map}_to_string. 0=int/char/short/byte, 1=String, 2=Long,
     *  3=Bool, 4=Double, 5=Float, 6=desconhecido/record/aninhado (→ "?",
     *  face do §104b-ii). SEM056 garante homogeneidade, então UMA tag basta. */
    static int collectionTag(Type t) {
        Type e = t instanceof Type.NullableType nt ? nt.inner() : t;
        if (e instanceof Type.PrimitiveType pt) {
            switch (pt.name()) {
                case "int", "char", "short", "byte": return 0;
                case "long": return 2;
                case "bool": return 3;
                case "float": return 5;
                default: return NativeTypeKinds.isDoubleType(pt) ? 4 : 6;
            }
        }
        if (BuiltinTypes.isString(e)) return 1;
        return 6;
    }

    // §284-map (18/09): VALOR de Map da familia Int/Long e caixa fisica
    // (contrato de escrita no CollectionCallLowerer, igual ao HashMap do
    // JVM) — tag 7 = "caixa numerica" no kof_elem_to_string: despacha por
    // MAGIC+tag via kof_box_to_string; nao-box passa cru (mapas antigos
    // emitidos raw por outras rotas continuam imprimindo certo).
    static int mapValueTag(Type t) {
        Type e = t instanceof Type.NullableType nt ? nt.inner() : t;
        if (e instanceof Type.PrimitiveType pt && unboxFn(pt.name()) != null) return 7;
        return collectionTag(t);
    }

    /** §284: funcao de box por tipo primitivo (tags da tabela de colecao). */
    static String boxFn(String primName) {
        switch (primName) {
            case "int", "char", "short", "byte": return "kof_box_int";
            case "long": return "kof_box_long";
            case "bool", "boolean": return "kof_box_bool";
            case "double": return "kof_box_double";
            case "float": return "kof_box_float";
            default: return null;
        }
    }

    /** §284: funcao de unbox por tipo esperado (v1: inteiros; demais = passthrough). */
    static String unboxFn(String primName) {
        switch (primName) {
            case "int", "char", "short", "byte": return "kof_unbox_int";
            // §284-map: Long aceita caixa Int OU Long (Number.longValue)
            case "long": return "kof_unbox_long";
            default: return null;
        }
    }

    /** §284-map: variante soft (consumidor de `Int?`) — cru passa cru. */
    static String unboxSoftFn(String primName) {
        switch (primName) {
            case "int", "char", "short", "byte": return "kof_unbox_int_soft";
            case "long": return "kof_unbox_long_soft";
            default: return null;
        }
    }

    /** §284-map: receiver de `.equals` que no native e a caixa do slot. */
    static boolean isBoxedNumericReceiver(Type t) {
        Type u = t instanceof Type.NullableType nt ? nt.inner() : t;
        if (!(u instanceof Type.ClassType ct)) return false;
        String n = ct.name();
        return switch (n) {
            case "Integer", "java/lang/Integer", "Long", "java/lang/Long",
                 "Character", "java/lang/Character", "Short", "java/lang/Short",
                 "Byte", "java/lang/Byte" -> true;
            default -> false;
        };
    }

    private final NativeBackend nb;

    NativeX86Calls(NativeBackend nb) { this.nb = nb; }

    void emitCall(StringBuilder sb, KofCall kc) {
        if ("kof_box".equals(kc.methodName())) {
            // §284 (FIXADO): box real 24B [magic][tag][value] (RuntimeErasureBox).
            // Valor ja esta no topo da pilha de maquina (conv dos calls kof_*:
            // pop arg → call → push result). Referencia NAO primitiva passa
            // cru — ponteiro ja e o valor de objeto (paridade com o JVM, que
            // nao embrulha referencias).
            Type p0 = kc.parameterTypes().isEmpty() ? Type.UnknownType.UNKNOWN
                    : kc.parameterTypes().get(0);
            String fn = p0 instanceof Type.PrimitiveType pt ? boxFn(pt.name()) : null;
            if (fn == null) return;
            sb.append("    popq %rdi\n");
            sb.append("    call ").append(fn).append("\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if ("kof_unbox".equals(kc.methodName()) || "kof_unbox_soft".equals(kc.methodName())) {
            // §284: le o value do box (invariante: so chega aqui box valido —
            // os emissores pareiam box/unbox pelo tipo do KofCall). Retorno
            // nao-primitivo = nao era box → passa cru.
            // §284-map: a variante SOFT (consumidores de `Int?` no native)
            // passa cru o que nao e box e nao-truca o valor — o slot de Map
            // hoje e fisicamente boxed e a variavel local, crua.
            Type ret = kc.returnType();
            String fn = ret instanceof Type.PrimitiveType pt
                    ? ("kof_unbox_soft".equals(kc.methodName())
                            ? unboxSoftFn(pt.name()) : unboxFn(pt.name()))
                    : null;
            if (fn == null) return;               // nao-primitivo: ponteiro ja e o valor
            sb.append("    popq %rdi\n");
            sb.append("    call ").append(fn).append("\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "equals".equals(kc.methodName())
                && isBoxedNumericReceiver(kc.ownerType())) {
            // §284-map: `tL.equals(tR)` do RecordEqualityLowerer (I6) sobre
            // wrapper numerico — no native o wrapper nao existe; o slot de
            // Map e a caixa MAGIC, entao a igualdade e kof_box_equals
            // (caixa=valor, cru=identidade, null=CCE/nullo-null=true).
            sb.append("    popq %rsi\n");
            sb.append("    popq %rdi\n");
            sb.append("    call kof_box_equals\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "println".equals(kc.methodName())) {
            Type argType = kc.parameterTypes().isEmpty() ? Type.UnknownType.UNKNOWN : kc.parameterTypes().get(0);
            if (argType instanceof Type.PrimitiveType pt && ("int".equals(pt.name()) || "char".equals(pt.name())
                    || "long".equals(pt.name()) || "short".equals(pt.name()) || "byte".equals(pt.name()))) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_print_int\n");
                sb.append("    leaq .Lnewline(%rip), %rdi\n");
                sb.append("    call kof_print\n");
            } else if (argType instanceof Type.PrimitiveType pt && NativeTypeKinds.isFloatType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movd %edi, %xmm0\n");
                sb.append("    call kof_print_float\n");
                sb.append("    leaq .Lnewline(%rip), %rdi\n");
                sb.append("    call kof_print\n");
            } else if (argType instanceof Type.PrimitiveType pt && NativeTypeKinds.isDoubleType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movq %rdi, %xmm0\n");
                sb.append("    call kof_print_double\n");
                sb.append("    leaq .Lnewline(%rip), %rdi\n");
                sb.append("    call kof_print\n");
            } else if (BuiltinTypes.isString(argType)) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_println_string\n");
            } else {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_println\n");
            }
            // o receiver (System.out via KofGetStatic) é descartado — o
            // runtime nativo kof_println_* não usa o PrintStream.
            sb.append("    addq $8, %rsp\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "print".equals(kc.methodName())) {
            Type argType = kc.parameterTypes().isEmpty() ? Type.UnknownType.UNKNOWN : kc.parameterTypes().get(0);
            if (BuiltinTypes.isString(argType)) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_print_string\n");
            } else if (argType instanceof Type.PrimitiveType pt && NativeTypeKinds.isFloatType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movd %edi, %xmm0\n");
                sb.append("    call kof_print_float\n");
            } else if (argType instanceof Type.PrimitiveType pt && NativeTypeKinds.isDoubleType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movq %rdi, %xmm0\n");
                sb.append("    call kof_print_double\n");
            } else {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_print\n");
            }
            // o receiver (System.out) é descartado — o runtime kof_print não usa.
            sb.append("    addq $8, %rsp\n");
            return;
        }
        if (NativeX86StringCalls.emit(nb, sb, kc)) return;
        // STDLIB S10: random.double retorna bits em xmm0 (mesma convenção de
        // kof_string_to_double); int/hex/boolean seguem rax via o caminho
        // genérico FUNCTION abaixo (tail-jmp no runtime).
        if ("kof_random_double".equals(kc.methodName())
                || "kof_rng_double".equals(kc.methodName())) {
            sb.append("    call ").append(kc.methodName()).append("\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // STDLIB S1b: sqrt(Double)->Double. Arg chega como 8 bytes de bits
        // IEEE na pilha (convenção double do native — ver println/kof_random_
        // double); sqrtsd opera em xmm0, resultado volta empilhado cru.
        if ("kof_math_sqrt".equals(kc.methodName())) {
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    call kof_math_sqrt\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // STDLIB S1b.2 (decisão 7a): pow(a,b) = pow@PLT (libm — flag -lm no
        // NativeAssembler quando usesPow). Vai pelo caminho GENÉRICO (igual
        // percentage): base→%rdi, exp→%rsi como 8 bits crus cada (pilha
        // 1-slot), shim RuntimeMath.kof_math_pow movimenta p/ xmm0/xmm1,
        // alinha a pilha e chama pow; retorno = bits crus em %rax (pushq do
        // genérico). Recusado em riscv/aarch via KofMath.supportedOn (MATH001).
        // CONC001: spawn/await no Native
        if ("kof_spawn".equals(kc.methodName()) || "kof_spawn_result".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call ").append(kc.methodName()).append("\n");
            if ("kof_spawn_result".equals(kc.methodName())) sb.append("    pushq %rax\n");
            return;
        }
        if ("kof_await".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_await\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // CONC001 (residual): done/poll não-bloqueantes — 1 arg (handle), valor em rax
        if ("kof_done".equals(kc.methodName()) || "kof_poll".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call ").append(kc.methodName()).append("\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // cancel(handle) -> bool; selectAny(list) -> valor pronto
        if ("kof_cancel".equals(kc.methodName()) || "kof_select_any".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call ").append(kc.methodName()).append("\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if ("kof_cancelled".equals(kc.methodName())) {
            sb.append("    call kof_cancelled\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // awaitTimeout(handle, ms): 2 args (handle em rdi, ms em esi); valor em rax
        if ("kof_await_timeout".equals(kc.methodName())) {
            sb.append("    popq %r12\n");
            sb.append("    popq %rdi\n");
            sb.append("    movl %r12d, %esi\n");
            sb.append("    call kof_await_timeout\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if ("kof_spawn_join_all".equals(kc.methodName())) {
            sb.append("    call kof_spawn_join_all\n");
            return;
        }
        if (kc.kind() == KofCallKind.STATIC && "valueOf".equals(kc.methodName())) {
            Type argType = kc.parameterTypes().isEmpty() ? Type.UnknownType.UNKNOWN : kc.parameterTypes().get(0);
            // T? (get de Map, SG-008): o despacho usa o INNER — sem isso o
            // Nullable(primitivo) não casava nenhum branch e o raw int
            // seguia para println_string (SIGSEGV, bug 87)
            Type dispatchType = argType instanceof Type.NullableType nt ? nt.inner() : argType;
            // §284-map (18/09): Nullable(Int/Short/Byte/Long) = caixa fisica do
            // slot de Map (escrita no lowerer; leitura Nullable(V) preserva o
            // null). O INNER cru NAO vale aqui — despacha pela caixa
            // (box_to_string imprime Int/Long como numero = golden JVM do
            // contexto de erasure; o Char nulavel ja chega DESEMBALADO do
            // lowerer, ramo acima, e nao passa por aqui).
            if (argType instanceof Type.NullableType nnt
                    && nnt.inner() instanceof Type.PrimitiveType ipt
                    && unboxFn(ipt.name()) != null) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_box_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.PrimitiveType pt && "char".equals(pt.name())) {
                // char → string UTF-8 (kof_int_to_string imprimia o
                // número do codepoint: String.valueOf(0xE9 as Char)
                // devolvia "233" em vez de "é")
                sb.append("    popq %rdi\n");
                sb.append("    call kof_char_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.PrimitiveType pt && ("int".equals(pt.name())
                    || "short".equals(pt.name()) || "byte".equals(pt.name()))) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_int_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.PrimitiveType pt && "long".equals(pt.name())) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_long_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.PrimitiveType pt && "bool".equals(pt.name())) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_bool_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.PrimitiveType pt && NativeTypeKinds.isFloatType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movd %edi, %xmm0\n");
                sb.append("    call kof_float_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.PrimitiveType pt && NativeTypeKinds.isDoubleType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movq %rdi, %xmm0\n");
                sb.append("    call kof_double_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.ClassType ct && BuiltinTypes.isList(ct)) {
                // §107: List/Map/Set são tipos de RUNTIME (sem vtable) — o
                // ramo genérico abaixo achava tosIdx=-1 e NÃO EMITIA NADA:
                // o ponteiro cru caía em kof_println_string = lixo (R6).
                // A tag do elemento vem do typer (SEM056: homogênea).
                sb.append("    popq %rdi\n");
                sb.append("    movl $").append(collectionTag(BuiltinTypes.listElement(ct)))
                  .append(", %esi\n");
                sb.append("    call kof_list_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.ClassType ct && BuiltinTypes.isSet(ct)) {
                sb.append("    popq %rdi\n");
                sb.append("    movl $").append(collectionTag(BuiltinTypes.setElement(ct)))
                  .append(", %esi\n");
                sb.append("    call kof_set_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.ClassType ct && BuiltinTypes.isMap(ct)) {
                sb.append("    popq %rdi\n");
                sb.append("    movl $").append(collectionTag(BuiltinTypes.mapKey(ct)))
                  .append(", %esi\n");
                sb.append("    movl $").append(mapValueTag(BuiltinTypes.mapValue(ct)))
                  .append(", %edx\n");
                sb.append("    call kof_map_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (BuiltinTypes.isObject(dispatchType)) {
                // §284: `Object` tem vtable base registrada (typeId 0) — o
                // ramo genérico abaixo LERIA o campo tag do box como ponteiro
                // de vtable (SIGSEGV). O static type Object na erasure SEMPRE
                // carrega um box de primitivo ou referencia real:
                // kof_box_to_string despacha por MAGIC+tag e passa nao-box
                // cru (referencia real com toString = caso raro, vira
                // passthrough — paridade de impressao mantida p/ o corpus).
                sb.append("    popq %rdi\n");
                sb.append("    call kof_box_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (dispatchType instanceof Type.ClassType ct && !BuiltinTypes.isString(dispatchType)) {
                // valueOf(objeto) → obj.toString() via vtable (records têm
                // toString no IR; String é identity). Paridade com o JVM.
                int tosIdx = nb.findVirtualMethodIndex(ct.name(), "toString", java.util.List.of());
                if (tosIdx >= 0) {
                    sb.append("    popq %rax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    movq 8(%rax), %rbx\n");
                    sb.append("    addq $").append(tosIdx * 8).append(", %rbx\n");
                    sb.append("    movq (%rbx), %rbx\n");
                    sb.append("    popq %rdi\n");
                    sb.append("    call *%rbx\n");
                    sb.append("    pushq %rax\n");
                } else {
                    // §284: static type sem vtable (Object/Nullable(Object))
                    // — o valor na pilha pode ser um BOX de erasure; sem este
                    // ramo o box cru caia em println_string (SIGSEGV).
                    // kof_box_to_string despacha por MAGIC+tag e passa
                    // nao-box cru (invariante preservado).
                    sb.append("    popq %rdi\n");
                    sb.append("    call kof_box_to_string\n");
                    sb.append("    pushq %rax\n");
                }
            }
            return;
        }
        if (kc.kind() == KofCallKind.CONSTRUCTOR && "<init>".equals(kc.methodName())) {
            int argCount = kc.parameterTypes().size();
            String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            int stackArgs = Math.max(0, argCount - 5);
            if (stackArgs > 0) {
                // Save stack args to local frame (high offsets to avoid collision)
                for (int s = stackArgs - 1; s >= 0; s--) {
                    int off = 256 + s * 8;
                    sb.append("    popq %rax\n");
                    sb.append("    movq %rax, -").append(off).append("(%rbp)\n");
                }
                // Pop 5 register args
                for (int i = 4; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                // Pop this
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
                // Push stack args back (arg6 por último → fica no topo da
                // stack → 16(%rbp) no callee; SysV exige essa ordem)
                for (int s = stackArgs - 1; s >= 0; s--) {
                    int off = 256 + s * 8;
                    sb.append("    pushq -").append(off).append("(%rbp)\n");
                }
            } else {
                for (int i = argCount - 1; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
            }
            String ctorLabel = nb.resolveCalleeName(kc);
            sb.append("    call ").append(ctorLabel).append("\n");
            if (stackArgs > 0) {
                // callee é caller-clean: remove os stack args empilhados de
                // volta. O pop subsequente do consumidor desempilha o push
                // duplicado do receptor (mesmo contrato do caminho <=5 args).
                sb.append("    addq $").append(stackArgs * 8).append(", %rsp\n");
            }
            return;
        }

        if (kc.kind() == KofCallKind.INSTANCE
                && (BuiltinTypes.isMap(kc.ownerType()) || BuiltinTypes.isSet(kc.ownerType()))) {
            String collFn = (kc.methodName().startsWith("kof_map_") || kc.methodName().startsWith("kof_set_"))
                    ? kc.methodName() : null;
            if (collFn != null) {
                int argCount = kc.parameterTypes().size();
                String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
                for (int i = argCount - 1; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
                // §123: tag de chave no header do map (off 40). 1=String
                // (kof_string_equals), 0=raw cmpq. Unknown NÃO toca (mantém o
                // default 1 — String é o caso histórico).
                // §126(a): CONJUNÇÃO receptor×arg — equals de String só quando
                // AMBOS os tipos conhecidos são String. Qualquer outro par
                // (tipos errados em qualquer direção: A1 Int-arg em String-map,
                // A2 String-arg em Int-map) cai no raw cmpq, que NUNCA deref e
                // produz exatamente o miss do JVM (0/null) — sem SIGSEGV, sem
                // rejeição, sem regressão dos targets que já rodavam.
                if (collFn.startsWith("kof_map_")) {
                    Type mkt = BuiltinTypes.mapKey(kc.ownerType());
                    Type mat = argCount >= 1 ? kc.parameterTypes().get(0) : null;
                    if (mkt instanceof Type.NullableType nt) mkt = nt.inner();
                    if (mat instanceof Type.NullableType nt) mat = nt.inner();
                    boolean ktKnown = mkt != null && !(mkt instanceof Type.UnknownType);
                    boolean atKnown = mat != null && !(mat instanceof Type.UnknownType);
                    if (ktKnown && atKnown) {
                        sb.append("    movl $").append(
                                BuiltinTypes.isString(mkt) && BuiltinTypes.isString(mat) ? 1 : 0)
                          .append(", 40(%rdi)\n");
                    } else if (ktKnown) {
                        sb.append("    movl $").append(BuiltinTypes.isString(mkt) ? 1 : 0)
                          .append(", 40(%rdi)\n");
                    } else if (atKnown) {
                        sb.append("    movl $").append(BuiltinTypes.isString(mat) ? 1 : 0)
                          .append(", 40(%rdi)\n");
                    }
                }
                sb.append("    call ").append(collFn).append("\n");
                if (!Type.isVoid(kc.returnType())) {
                    sb.append("    pushq %rax\n");
                    // §284-map (18/09): leitura de Map com retorno PRIMITIVO
                    // declarado (get/getOrDefault com V pinado Int/Long)
                    // recebe a caixa do slot e desemboxa no call-site —
                    // espelha JvmOpCollections (emitBoxIfPrimitive nas escritas
                    // + unbox/checkcast no ret). Ret Nullable(V) NAO
                    // desempacota aqui: null precisa sobreviver; o consumidor
                    // (comparacao/print) desempacota guiado pelo tipo.
                    if (("kof_map_get".equals(collFn) || "kof_map_get_or_default".equals(collFn))
                            && kc.returnType() instanceof Type.PrimitiveType rpt) {
                        // §284-map SOFT: caixa abre, cru passa, null -> CCE.
                        String ufm = unboxSoftFn(rpt.name());
                        if (ufm != null) {
                            sb.append("    popq %rdi\n");
                            sb.append("    call ").append(ufm).append("\n");
                            sb.append("    pushq %rax\n");
                        }
                    }
                }
                return;
            }
        }
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isChannel(kc.ownerType())) {
            // Canais: receiver (Channel) + args na pilha; asm: chan=rdi, value=rsi.
            String chFn = kc.methodName().startsWith("kof_channel_") ? kc.methodName() : null;
            if (chFn != null) {
                int argCount = kc.parameterTypes().size();
                String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
                for (int i = argCount - 1; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
                sb.append("    call ").append(chFn).append("\n");
                if (!Type.isVoid(kc.returnType())) {
                    sb.append("    pushq %rax\n");
                }
                return;
            }
        }
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isList(kc.ownerType())) {
            String listFn = kc.methodName().startsWith("kof_list_") ? kc.methodName() : null;
            if (listFn != null) {
                int argCount = kc.parameterTypes().size();
                String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
                for (int i = argCount - 1; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
                sb.append("    call ").append(listFn).append("\n");
                if (!Type.isVoid(kc.returnType())) {
                    sb.append("    pushq %rax\n");
                }
                return;
            }
        }
        if (kc.kind() == KofCallKind.INSTANCE && kc.ownerType() instanceof Type.ClassType ct) {
            int vtableIdx = nb.findVirtualMethodIndex(ct.name(), kc.methodName(), kc.parameterTypes());
            if (vtableIdx >= 0) {
                int argCount = kc.parameterTypes().size();
                String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
                int stackArgs = Math.max(0, argCount - 5);
                if (stackArgs > 0) {
                    // salva stack args em slots do frame (ordem: argN no slot alto)
                    for (int s = stackArgs - 1; s >= 0; s--) {
                        int off = 256 + s * 8;
                        sb.append("    popq %r10\n");
                        sb.append("    movq %r10, -").append(off).append("(%rbp)\n");
                    }
                }
                for (int i = Math.min(argCount, 5) - 1; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
                if (stackArgs > 0) {
                    // arg6 por último → topo → 16(%rbp) no callee
                    for (int s = stackArgs - 1; s >= 0; s--) {
                        int off = 256 + s * 8;
                        sb.append("    pushq -").append(off).append("(%rbp)\n");
                    }
                }
                sb.append("    movq 8(%rax), %rbx\n");
                sb.append("    addq $").append(vtableIdx * 8).append(", %rbx\n");
                sb.append("    movq (%rbx), %rbx\n");
                sb.append("    call *%rbx\n");
                if (stackArgs > 0) {
                    sb.append("    addq $").append(stackArgs * 8).append(", %rsp\n");
                }
                if (!Type.isVoid(kc.returnType())) {
                    sb.append("    pushq %rax\n");
                }
                return;
            }
        }
        if (kc.kind() == KofCallKind.INTERFACE && kc.ownerType() instanceof Type.ClassType ct) {
            int vtableIdx = nb.findVirtualMethodIndex(ct.name(), kc.methodName(), kc.parameterTypes());
            if (vtableIdx >= 0) {
                int argCount = kc.parameterTypes().size();
                String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
                int stackArgs = Math.max(0, argCount - 5);
                if (stackArgs > 0) {
                    // salva stack args em slots do frame (ordem: argN no slot alto)
                    for (int s = stackArgs - 1; s >= 0; s--) {
                        int off = 256 + s * 8;
                        sb.append("    popq %r10\n");
                        sb.append("    movq %r10, -").append(off).append("(%rbp)\n");
                    }
                }
                for (int i = Math.min(argCount, 5) - 1; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
                if (stackArgs > 0) {
                    // arg6 por último → topo → 16(%rbp) no callee
                    for (int s = stackArgs - 1; s >= 0; s--) {
                        int off = 256 + s * 8;
                        sb.append("    pushq -").append(off).append("(%rbp)\n");
                    }
                }
                sb.append("    movq 8(%rax), %rbx\n");
                sb.append("    addq $").append(vtableIdx * 8).append(", %rbx\n");
                sb.append("    movq (%rbx), %rbx\n");
                sb.append("    call *%rbx\n");
                if (stackArgs > 0) {
                    sb.append("    addq $").append(stackArgs * 8).append(", %rsp\n");
                }
                if (!Type.isVoid(kc.returnType())) {
                    sb.append("    pushq %rax\n");
                }
                return;
            }
        }

        int argCount = kc.parameterTypes().size();
        String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
        int stackArgs = Math.max(0, argCount - 6);
        if (stackArgs > 0) {
            // S7f (13/09): funções com 7+ args NÃO são descartadas — o emit
            // anterior fazia addq $stackArgs*8 (perdia os args silenciosamente
            // e o callee lia lixo na stack). ABI SysV: args 7..N na stack do
            // callee, arg7 no menor endereço (0(%rsp) na entry) => na pilha do
            // emitter, arg7 tem que ficar NO TOPO no call. Salvo os args
            // 8..N (topo da pilha) em slots do frame, popo os 6 regs, e
            // re-empilho em ordem reversa (argN primeiro => arg7 no topo).
            for (int s = stackArgs - 1; s >= 0; s--) {
                // slots altos do frame local (padrão do ramo CONSTRUCTOR)
                int off = 256 + s * 8;
                sb.append("    popq %r10\n");
                sb.append("    movq %r10, -").append(off).append("(%rbp)\n");
            }
        }
        for (int i = Math.min(argCount, 6) - 1; i >= 0; i--) {
            sb.append("    popq ").append(intRegs[i]).append("\n");
        }
        if (stackArgs > 0) {
            // re-push: argN primeiro ... arg7 por último (topo = 0(%rsp))
            for (int s = stackArgs - 1; s >= 0; s--) {
                int off = 256 + s * 8;
                sb.append("    pushq -").append(off).append("(%rbp)\n");
            }
        }
        String callee = nb.resolveCalleeName(kc);
        sb.append("    call ").append(callee).append("\n");
        if (stackArgs > 0) {
            // limpa os stack args após o call (caller-cleanup, padrão dos
            // ramos INSTANCE/INTERFACE acima)
            sb.append("    addq $").append(stackArgs * 8).append(", %rsp\n");
        }
        if (!Type.isVoid(kc.returnType())) {
            sb.append("    pushq %rax\n");
        }
    }
}
