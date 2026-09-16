package dev.kof.compiler.nat;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.CompilerClassLowering;
import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofBinaryOp;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.Type;

import java.util.List;

/**
 *  * Emissão cross riscv64 (parte 2): binary, cond-jump, call, ABI de regs,
 * Extraído verbatim de NativeBackend (FASE 3, REFACTOR-500); estado do
 * backend acessado via campo `nb` (padrão CompilerClassLowering).
 */
public final class NativeRiscvCrossOps {

    private final NativeBackend nb;

    private final NativeRiscvCrossEmit other;

    NativeRiscvCrossOps(NativeBackend nb, NativeRiscvCrossEmit other) { this.nb = nb; this.other = other; }

    void emitCrossBinaryRiscv(StringBuilder sb, KofBinary kb) {
        // b = topo, a = abaixo; resultado = a OP b
        Type opTy = kb.operandType();
        boolean isFloat = NativeTypeKinds.isFloatType(opTy);
        boolean isDouble = NativeTypeKinds.isDoubleType(opTy);
        if (isFloat || isDouble) {
            // §146 cross (12/09, #101): MOD de Float/Double variável caía no
            // `default` vazio e devolvia o dividendo (mesma raiz do x86).
            // Double: fmod via kof_double_mod (B40 — a - trunc(a/b)*b, NaN p/
            // 0/Inf/NaN e |q|>=2^63, paridade JVM). Float: promove p/ double,
            // chama o MESMO helper, trunca de volta (fcvt.s.d; o caller
            // espera bits de float no push).
            if (kb.op() == KofBinaryOp.MOD) {
                String s = isFloat ? "s" : "d";
                sb.append("    pop t0\n    fmv.").append(s).append(".x f1, t0\n");
                sb.append("    pop t1\n    fmv.").append(s).append(".x f0, t1\n");
                if (isFloat) sb.append("    fcvt.d.s f0, f0\n    fcvt.d.s f1, f1\n");
                sb.append("    fmv.x.d a0, f0\n    fmv.x.d a1, f1\n");
                sb.append("    call kof_double_mod\n");
                sb.append("    fmv.d.x f0, a0\n");
                if (isFloat) sb.append("    fcvt.s.d f0, f0\n");
                sb.append("    fmv.x.").append(s).append(" t0, f0\n");
                other.pushRiscv(sb, "t0");
                return;
            }
            String s = isFloat ? "s" : "d";
            sb.append("    pop t0\n    fmv.").append(s).append(".x f1, t0\n");
            sb.append("    pop t1\n    fmv.").append(s).append(".x f0, t1\n");
            switch (kb.op()) {
                case ADD -> sb.append("    fadd.").append(s).append(" f0, f0, f1\n");
                case SUB -> sb.append("    fsub.").append(s).append(" f0, f0, f1\n");
                case MUL -> sb.append("    fmul.").append(s).append(" f0, f0, f1\n");
                case DIV -> sb.append("    fdiv.").append(s).append(" f0, f0, f1\n");
                case EQ -> { sb.append("    feq.").append(s).append(" t1, f0, f1\n    mv t0, t1\n"); }
                // NE = NOT(EQ): feq dá 0 p/ NaN (IEEE) e seqz inverte — o
                // antigo fle+snez dizia NaN != NaN falso (divergia do x86/
                // JVM/JS = true; achado na prova MATH001 11/09).
                case NE -> { sb.append("    feq.").append(s).append(" t1, f0, f1\n    seqz t0, t1\n"); }
                case LT -> { sb.append("    flt.").append(s).append(" t0, f0, f1\n"); }
                case LE -> { sb.append("    fle.").append(s).append(" t0, f0, f1\n"); }
                case GT -> { sb.append("    fgt.").append(s).append(" t0, f0, f1\n"); }
                case GE -> { sb.append("    fge.").append(s).append(" t0, f0, f1\n"); }
                default -> { }
            }
            if (kb.op() == KofBinaryOp.EQ || kb.op() == KofBinaryOp.NE) {
                other.pushRiscv(sb, "t0");
            } else if (kb.op() == KofBinaryOp.LT || kb.op() == KofBinaryOp.LE
                    || kb.op() == KofBinaryOp.GT || kb.op() == KofBinaryOp.GE) {
                other.pushRiscv(sb, "t0");
            } else {
                sb.append("    fmv.x.").append(s).append(" t0, f0\n");
                other.pushRiscv(sb, "t0");
            }
            return;
        }
        sb.append("    pop t0\n");   // b
        sb.append("    pop t1\n");   // a
        boolean int32 = NativeTypeKinds.isInt32Type(opTy);
        switch (kb.op()) {
            case ADD -> sb.append("    add t1, t1, t0\n");
            case SUB -> sb.append("    sub t1, t1, t0\n");
            case MUL -> sb.append("    mul t1, t1, t0\n");
            case DIV -> sb.append("    div t1, t1, t0\n");
            case MOD -> sb.append("    rem t1, t1, t0\n");
            case EQ -> { sb.append("    sub t2, t1, t0\n"); sb.append("    seqz t1, t2\n"); }
            case NE -> { sb.append("    sub t2, t1, t0\n"); sb.append("    snez t1, t2\n"); }
            case LT -> sb.append("    slt t1, t1, t0\n");
            // sle/sge NÃO existem na ISA riscv — só slt. a<=b = !(b<a);
            // a>=b = !(a<b). seqz já é usado por EQ/NE (tradutor conhece).
            case LE -> { sb.append("    slt t2, t0, t1\n"); sb.append("    seqz t1, t2\n"); }
            case GT -> sb.append("    slt t1, t0, t1\n");
            case GE -> { sb.append("    slt t2, t1, t0\n"); sb.append("    seqz t1, t2\n"); }
            case AND -> sb.append("    and t1, t1, t0\n");
            case OR -> sb.append("    or t1, t1, t0\n");
            case XOR -> sb.append("    xor t1, t1, t0\n");
            case SHL -> sb.append("    sll t1, t1, t0\n");
            case SHR -> sb.append("    sra t1, t1, t0\n");
            case USHR -> sb.append("    srl t1, t1, t0\n");
        }
        if (int32 && (kb.op() == KofBinaryOp.ADD || kb.op() == KofBinaryOp.SUB || kb.op() == KofBinaryOp.MUL)) {
            sb.append("    sext.w t1, t1\n");
        }
        other.pushRiscv(sb, "t1");
    }

    void emitCrossCondJumpRiscv(StringBuilder sb, KofConditionalJump kc) {
        sb.append("    pop t0\n");   // b (topo)
        sb.append("    pop t1\n");   // a (abaixo)
        String cond;
        switch (kc.comparison()) {
            case EQ -> cond = "bne";
            case NE -> cond = "beq";
            case LT -> cond = "bge";
            case LE -> cond = "bgt";
            case GT -> cond = "ble";
            case GE -> cond = "blt";
            default -> cond = "b";
        }
        sb.append("    ").append(cond).append(" t1, t0, ").append(nb.resolveLabel(kc.falseLabel())).append("\n");
        sb.append("    j ").append(nb.resolveLabel(kc.trueLabel())).append("\n");
    }

    void emitCrossCallRiscv(StringBuilder sb, KofCall kc) {
        String mn = kc.methodName();
        Type argType = kc.parameterTypes().isEmpty() ? Type.UnknownType.UNKNOWN : kc.parameterTypes().get(0);
        if ("kof_box".equals(mn) || "kof_unbox".equals(mn)) return;

        // println / print (PrintStream)
        if (kc.kind() == KofCallKind.INSTANCE && ("println".equals(mn) || "print".equals(mn))) {
            boolean nl = "println".equals(mn);
            sb.append("    pop a0\n");
            // T? (get de Map, SG-008/bug 87): despacho pelo INNER — sem isso
            // Nullable(primitivo) caía no println_string sobre raw int (segv)
            Type dispatchType = argType instanceof Type.NullableType nt ? nt.inner() : argType;
            if (dispatchType instanceof Type.PrimitiveType pt) {
                String cn = Type.canonicalPrimitiveName(pt.name());
                switch (cn) {
                    case "int", "char", "short", "byte" -> {
                        sb.append(nl ? "    call kof_println_int\n" : "    call kof_print_int\n");
                    }
                    case "long" -> sb.append(nl ? "    call kof_println_int\n" : "    call kof_print_int\n");
                    case "bool", "boolean" -> {
                        sb.append("    call kof_bool_to_string\n");
                        sb.append(nl ? "    call kof_println_string\n" : "    call kof_print_string\n");
                    }
                    case "float" -> {
                        // FLT001 (fechado 15/09): println(double/float) direto
                        // de System.out (não passa pelo valueOf do sugar).
                        sb.append("    call kof_float_to_string\n");
                        sb.append(nl ? "    call kof_println_string\n" : "    call kof_print_string\n");
                    }
                    case "double" -> {
                        sb.append("    call kof_double_to_string\n");
                        sb.append(nl ? "    call kof_println_string\n" : "    call kof_print_string\n");
                    }
                    default -> sb.append(nl ? "    call kof_println_string\n" : "    call kof_print_string\n");
                }
            } else {
                sb.append(nl ? "    call kof_println_string\n" : "    call kof_print_string\n");
            }
            // o receiver (System.out via KofGetStatic) é descartado — o
            // runtime nativo não usa o PrintStream.
            sb.append("    addi sp, sp, 8\n");
            sb.append("    li a0, 0\n");
            other.pushRiscv(sb, "a0");
            return;
        }

        // String.valueOf (STATIC)
        if (kc.kind() == KofCallKind.STATIC && "valueOf".equals(mn)) {
            // T? (Map.get→V? desde SG-008/bug 87): despacho pelo INNER. Sem
            // isso, valueOf(m.get(k)) com V?=Int nao casava o branch primitivo
            // e nao emitia NADA — o raw Int ficava na pilha e o println virava
            // println_string sobre inteiro (SIGSEGV riscv64/aarch64, bug 88).
            // Espelha o dispatchType ja aplicado ao println acima e ao
            // valueOf no NativeX86Calls (9436da12 corrigiu x86 mas esqueceu o
            // cross aqui — mesma familia, lane paridade R5).
            Type vArgType = argType instanceof Type.NullableType nt ? nt.inner() : argType;
            if (vArgType instanceof Type.PrimitiveType pt) {
                String cn = Type.canonicalPrimitiveName(pt.name());
                if ("float".equals(cn) || "double".equals(cn)) {
                    // FLT001 (fechado 15/09): double/float→string via slice B45
                    // (libc snprintf/strtod, link dinâmico sob demanda). Os bits
                    // crus já estão no topo da pilha de valor; a string volta em
                    // a0. ABI: kof_float_to_string(a0=low32),
                    // kof_double_to_string(a0=bits).
                    if ("float".equals(cn)) {
                        sb.append("    pop a0\n    call kof_float_to_string\n");
                    } else {
                        sb.append("    pop a0\n    call kof_double_to_string\n");
                    }
                    other.pushRiscv(sb, "a0");
                    return;
                }
                if ("int".equals(cn) || "char".equals(cn) || "short".equals(cn) || "byte".equals(cn) || "long".equals(cn)) {
                    sb.append("    pop a0\n    call kof_int_to_string\n");
                    other.pushRiscv(sb, "a0");
                } else if ("bool".equals(cn) || "boolean".equals(cn)) {
                    sb.append("    pop a0\n    call kof_bool_to_string\n");
                    other.pushRiscv(sb, "a0");
                }
            } else if (vArgType instanceof Type.ClassType ct && (BuiltinTypes.isList(ct)
                    || BuiltinTypes.isSet(ct) || BuiltinTypes.isMap(ct))) {
                // §107-cross: List/Map/Set são tipos de runtime (sem vtable
                // toString) — o ramo genérico não emitia nada e o ponteiro cru
                // caía em kof_println_string = lixo (`@` medido no qemu). A tag
                // do elemento vem do typer (SEM056: homogênea), igual x86.
                // FP-em-coleção (tags 4/5): fechado em 15/09 — o helper
                // kof_elem_to_string do B39 chama kof_double_to_string/
                // kof_float_to_string (slice B45), igual ao x86.
                // Record/aninhado (tag 6) fica `?` no helper (cara do §104b-ii).
                Type elem = BuiltinTypes.isMap(ct) ? null
                        : BuiltinTypes.isList(ct) ? BuiltinTypes.listElement(ct)
                        : BuiltinTypes.setElement(ct);
                int ktag = NativeX86Calls.collectionTag(BuiltinTypes.isMap(ct) ? BuiltinTypes.mapKey(ct) : elem);
                int vtag = BuiltinTypes.isMap(ct) ? NativeX86Calls.collectionTag(BuiltinTypes.mapValue(ct)) : -1;
                sb.append("    pop a0\n");
                if (BuiltinTypes.isList(ct)) {
                    sb.append("    li a1, ").append(ktag).append("\n");
                    sb.append("    call kof_list_to_string\n");
                } else if (BuiltinTypes.isSet(ct)) {
                    sb.append("    li a1, ").append(ktag).append("\n");
                    sb.append("    call kof_set_to_string\n");
                } else {
                    sb.append("    li a1, ").append(ktag).append("\n");
                    sb.append("    li a2, ").append(vtag).append("\n");
                    sb.append("    call kof_map_to_string\n");
                }
                other.pushRiscv(sb, "a0");
            }
            return;
        }

        // String.length (propriedade → INSTANCE sem args)
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType()) && "length".equals(mn)) {
            sb.append("    pop a0\n    call kof_string_length\n");
            other.pushRiscv(sb, "a0");
            return;
        }

        // §145 (12/09, #101): isEmpty = (length == 0); sem ramo caía no
        // fallback genérico (link-fail). seqz zera/nonzero→1, convenção Bool.
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType()) && "isEmpty".equals(mn)) {
            sb.append("    pop a0\n    call kof_string_length\n");
            sb.append("    seqz a0, a0\n");
            other.pushRiscv(sb, "a0");
            return;
        }

        // métodos String com receiver + args (charAt/substring/contains/...)
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())) {
            String fn = switch (mn) {
                case "charAt" -> "kof_string_char_at";
                case "substring" -> "kof_string_substring";
                case "contains" -> "kof_string_contains";
                case "startsWith" -> "kof_string_starts_with";
                case "endsWith" -> "kof_string_ends_with";
                case "indexOf" -> "kof_string_index_of";
                case "concat" -> "kof_string_concat";
                case "toInt" -> "kof_string_to_int";
                case "trim" -> "kof_string_trim";
                case "toUpperCase" -> "kof_string_to_upper";
                case "toLowerCase" -> "kof_string_to_lower";
                case "lastIndexOf" -> "kof_string_last_index_of";
                case "equalsIgnoreCase" -> "kof_string_equals_ignore_case";
                // §97 cross (B36): métodos declarados no reference (equals/
                // compareTo/hashCode). Sem entry aqui caíam no fallback
                // genérico (pop só de a0 → receiver fica na pilha, link-fail
                // String_equals). O bloco abaixo faz pop a1..aN + pop a0.
                case "equals" -> "String_equals";
                case "compareTo" -> "String_compareTo";
                case "hashCode" -> "String_hashCode";
                default -> null;
            };
            if (fn != null) {
                int argCount = kc.parameterTypes().size();
                // §102 cross (B35): com 2+ args o 2º (from) vive em a2 —
                // roteia p/ o helper _2 (clamps JDK em code units UTF-16),
                // idem ao dispatch de aridade do x86 em NativeX86StringCalls.
                if (argCount >= 2 && (mn.equals("indexOf") || mn.equals("lastIndexOf")
                        || mn.equals("startsWith"))) {
                    fn = fn + "2";
                }
                if ("substring".equals(mn) && argCount == 1) {
                    // §111 cross: sentinela "até o fim" = -1 (0 colide com o
                    // 0 legítimo do 2-arg — mesmo fix x86 do maintainer).
                    sb.append("    pop a1\n    li a2, -1\n");
                } else {
                    for (int i = argCount - 1; i >= 0; i--) {
                        sb.append("    pop ").append(crossArgReg(i + 1)).append("\n");
                    }
                }
                sb.append("    pop a0\n");
                sb.append("    call ").append(fn).append("\n");
                if (!Type.isVoid(kc.returnType())) other.pushRiscv(sb, "a0");
                return;
            }
            // replace(char,char) vs replace(String,String): dispatch por tipo
            if ("replace".equals(mn) && kc.parameterTypes().size() == 2) {
                Type first = kc.parameterTypes().get(0);
                boolean charArgs = first instanceof Type.PrimitiveType pt
                        && "char".equals(Type.canonicalPrimitiveName(pt.name()));
                sb.append("    pop a2\n    pop a1\n    pop a0\n");
                sb.append("    call ").append(charArgs ? "kof_string_replace_char" : "kof_string_replace").append("\n");
                other.pushRiscv(sb, "a0");
                return;
            }
            // split(sep) -> String[] (array de KofString)
            if ("split".equals(mn) && kc.parameterTypes().size() == 1) {
                sb.append("    pop a1\n    pop a0\n");
                sb.append("    call kof_string_split\n");
                other.pushRiscv(sb, "a0");
                return;
            }
        }

        // kof_string_equals / concat como FUNCTION (frontend emite assim)
        if (kc.kind() == KofCallKind.FUNCTION && ("kof_string_equals".equals(mn) || "kof_string_concat".equals(mn))) {
            int argCount = kc.parameterTypes().size();
            for (int i = argCount - 1; i >= 0; i--) {
                sb.append("    pop ").append(crossArgReg(i)).append("\n");
            }
            sb.append("    call ").append(mn).append("\n");
            if (!Type.isVoid(kc.returnType())) other.pushRiscv(sb, "a0");
            return;
        }

        // coleções (List/Map/Set) — kof_list_*/kof_map_*/kof_set_*
        if (kc.kind() == KofCallKind.INSTANCE && mn.startsWith("kof_")) {
            int argCount = kc.parameterTypes().size();
            for (int i = argCount - 1; i >= 0; i--) {
                sb.append("    pop ").append(crossArgReg(i + 1)).append("\n");
            }
            sb.append("    pop a0\n");
            // §123: tag de chave no header do map (off 40) — 1=String
            // (kof_string_equals), 0=raw cmpq (Int/Long/... senão chave Int
            // vira PONTEIRO → SIGSEGV). Unknown NÃO toca (mantém default 1).
            // §126(a): CONJUNÇÃO receptor×arg (espelha o x86) — equals só
            // quando ambos String; tipos errados em qualquer direção viram
            // raw cmpq = miss seguro (0/null como o JVM), nunca SIGSEGV.
            if (mn.startsWith("kof_map_")) {
                Type mkt = BuiltinTypes.mapKey(kc.ownerType());
                Type mat = argCount >= 1 ? kc.parameterTypes().get(0) : null;
                if (mkt instanceof Type.NullableType nt) mkt = nt.inner();
                if (mat instanceof Type.NullableType nt) mat = nt.inner();
                boolean ktKnown = mkt != null && !(mkt instanceof Type.UnknownType);
                boolean atKnown = mat != null && !(mat instanceof Type.UnknownType);
                int tag = -1;
                if (ktKnown && atKnown) {
                    tag = BuiltinTypes.isString(mkt) && BuiltinTypes.isString(mat) ? 1 : 0;
                } else if (ktKnown) {
                    tag = BuiltinTypes.isString(mkt) ? 1 : 0;
                } else if (atKnown) {
                    tag = BuiltinTypes.isString(mat) ? 1 : 0;
                }
                if (tag >= 0) {
                    sb.append("    li t0, ").append(tag).append("\n");
                    sb.append("    sw t0, 40(a0)\n");
                }
            }
            sb.append("    call ").append(mn).append("\n");
            if (!Type.isVoid(kc.returnType())) other.pushRiscv(sb, "a0");
            return;
        }

        // construtor: obj (dup) + args
        if (kc.kind() == KofCallKind.CONSTRUCTOR && "<init>".equals(mn)) {
            int argCount = kc.parameterTypes().size();
            for (int i = argCount - 1; i >= 0; i--) {
                sb.append("    pop ").append(crossArgReg(i + 1)).append("\n");
            }
            sb.append("    pop a0\n");
            sb.append("    call ").append(resolveCalleeNameRiscv(kc)).append("\n");
            return;
        }

        // dispatch virtual (INSTANCE/INTERFACE em classe de usuário)
        if ((kc.kind() == KofCallKind.INSTANCE || kc.kind() == KofCallKind.INTERFACE)
                && kc.ownerType() instanceof Type.ClassType ct && !BuiltinTypes.isString(ct)) {
            int argCount = kc.parameterTypes().size();
            int vtableIdx = nb.findVirtualMethodIndex(ct.name(), mn, kc.parameterTypes());
            if (vtableIdx >= 0) {
                for (int i = argCount - 1; i >= 0; i--) {
                    sb.append("    pop ").append(crossArgReg(i + 1)).append("\n");
                }
                sb.append("    pop a0\n");
                sb.append("    ld t0, 8(a0)\n");
                sb.append("    addi t0, t0, ").append(vtableIdx * 8).append("\n");
                sb.append("    ld t0, 0(t0)\n");
                sb.append("    jalr t0\n");
                if (!Type.isVoid(kc.returnType())) other.pushRiscv(sb, "a0");
                return;
            }
        }

        // chamada direta (FUNCTION/STATIC de usuário — args em a0..aN, sem receiver)
        int argCount = kc.parameterTypes().size();
        for (int i = argCount - 1; i >= 0; i--) {
            sb.append("    pop ").append(crossArgReg(i)).append("\n");
        }
        sb.append("    call ").append(resolveCalleeNameRiscv(kc)).append("\n");
        if (!Type.isVoid(kc.returnType())) other.pushRiscv(sb, "a0");
    }

    String crossArgReg(int i) {
        String[] regs = {"a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7"};
        return i < regs.length ? regs[i] : "a7";
    }

    String resolveCalleeNameRiscv(KofCall kc) {
        String mn = kc.methodName();
        if (mn.startsWith("kof_map_") || mn.startsWith("kof_set_") || mn.startsWith("kof_list_")) return mn;
        if (kc.kind() == KofCallKind.FUNCTION) {
            String key = NativeSymbolMangling.fnKey(NativeSymbolMangling.internalOwner(kc.ownerType()), mn, kc.parameterTypes(), nb.allClassesMap);
            return nb.functionMangleMap.getOrDefault(key, nb.sanitizeName(mn));
        }
        if (kc.kind() == KofCallKind.CONSTRUCTOR && kc.ownerType() instanceof Type.ClassType ct) {
            return nb.sanitizeName(ct.name()) + "_" + nb.sanitizeName("<init>") + "_" + kc.parameterTypes().size();
        }
        if (kc.ownerType() instanceof Type.ClassType ct) {
            String key = ct.name() + "." + mn;
            return nb.functionMangleMap.getOrDefault(key, nb.sanitizeName(ct.name()) + "_" + nb.sanitizeName(mn));
        }
        return nb.sanitizeName(mn);
    }

}
