package dev.kof.compiler.nat;
import dev.kof.compiler.IRMethod;

import dev.kof.compiler.IRClass;
import dev.kof.compiler.Type;
import java.util.ArrayList;
import java.util.List;

/** F3: metadados de classe (vtable virtual methods, string data). */
final class NativeClassMeta {
    private NativeClassMeta() {}

    static List<String> collectVirtualMethods(NativeBackend nb, IRClass clazz) {
        List<String> methods = new ArrayList<>();
        List<String> methodNames = new ArrayList<>();
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        String current = clazz.superName();
        while (current != null && !current.isEmpty() && !"java/lang/Object".equals(current)) {
            IRClass superClazz = nb.allClassesMap.get(current);
            if (superClazz == null) break;
            for (IRMethod m : superClazz.methods()) {
                if (!"<init>".equals(m.name()) && !"<clinit>".equals(m.name())
                        && !m.name().startsWith("kof_")) {
                    if (!methodNames.contains(m.name())) {
                        methodNames.add(m.name());
                        methods.add(nb.sanitizeName(superClazz.name()) + "_" + nb.sanitizeName(m.name()));
                    }
                }
            }
            for (String iface : superClazz.interfaces()) {
                if (!visited.contains(iface)) {
                    visited.add(iface);
                    queue.add(iface);
                }
            }
            current = superClazz.superName();
        }
        while (!queue.isEmpty()) {
            String ifaceName = queue.poll();
            IRClass ifaceClazz = nb.allClassesMap.get(ifaceName);
            if (ifaceClazz == null) continue;
            for (IRMethod m : ifaceClazz.methods()) {
                if (!"<init>".equals(m.name()) && !"<clinit>".equals(m.name())
                        && !m.name().startsWith("kof_")) {
                    if (!methodNames.contains(m.name())) {
                        methodNames.add(m.name());
                        methods.add(nb.sanitizeName(ifaceClazz.name()) + "_" + nb.sanitizeName(m.name()));
                    }
                }
            }
            for (String iface : ifaceClazz.interfaces()) {
                if (!visited.contains(iface)) {
                    visited.add(iface);
                    queue.add(iface);
                }
            }
        }
        for (IRMethod m : clazz.methods()) {
            if (!"<init>".equals(m.name()) && !"<clinit>".equals(m.name())
                    && !m.name().startsWith("kof_")) {
                // SG-011B: fnSymbol == sanitize+"_"+name p/ classes reais (vtable
                // idêntica); só o recipiente Main leva sufixo de assinatura — o
                // MESMO da .globl, então cada slot referencia um símbolo definido.
                // §131 (10a): método sobrecarregado (2+ defs do nome) ganha slot
                // PRÓPRIO por assinatura (fnSymbol tageia) — antes o 2º def
                // sobrescrevia o slot (methods.set) e os 2 .globl colidiam.
                String sym = NativeSymbolMangling.fnSymbol(clazz.name(), m.name(), m.parameterTypes(), nb.allClassesMap);
                if (NativeSymbolMangling.sigMangles(clazz.name(), m.name(), nb.allClassesMap)) {
                    methodNames.add(m.name());
                    methods.add(sym);
                } else {
                    int idx = methodNames.indexOf(m.name());
                    if (idx >= 0) {
                        methods.set(idx, sym);
                    } else {
                        methodNames.add(m.name());
                        methods.add(sym);
                    }
                }
            }
        }
        return methods;
    }

    static int findVirtualMethodIndex(NativeBackend nb, String ownerTypeName, String methodName, int argCount) {
        return findVirtualMethodIndex(nb, ownerTypeName, methodName, arityTypes(argCount));
    }

    /** §131-residual (13/09): resolve o slot pelo NOME + TIPOS do call site.
     *  Só a ARIDADE não bastava — `twice(Int)`/`twice(String)` (mesma aridade,
     *  tipos diferentes) resolviam ambas para o 1º slot: o Native chamava o
     *  método errado (SIGSEGV ao passar String p/ parâmetro Int). JVM/Script/JS
     *  sempre estiveram corretos (dispatch por descritor/SAM). */
    static int findVirtualMethodIndex(NativeBackend nb, String ownerTypeName, String methodName, List<Type> argTypes) {
        for (IRClass clazz : nb.allClassesMap.values()) {
            if (clazz.name().equals(ownerTypeName) || clazz.name().endsWith("/" + ownerTypeName)
                    || ownerTypeName.endsWith("/" + clazz.name()) || ownerTypeName.equals(nb.sanitizeName(clazz.name()))) {
                List<String> methods = collectVirtualMethods(nb, clazz);
                String mangled = NativeSymbolMangling.fnSymbol(clazz.name(), methodName,
                        methodsForCall(clazz, methodName, argTypes), nb.allClassesMap);
                int bySig = indexOfSymbol(methods, mangled);
                if (bySig >= 0) return bySig;
                // sem casamento por tipo (arg Unknown): casa QUALQUER overload
                // do nome — melhor que -1 (sem dispatch).
                for (IRMethod m : clazz.methods()) {
                    if (m.name().equals(methodName) && !"<init>".equals(m.name()) && !"<clinit>".equals(m.name())) {
                        String m2 = NativeSymbolMangling.fnSymbol(clazz.name(), m.name(), m.parameterTypes(), nb.allClassesMap);
                        int idx = indexOfSymbol(methods, m2);
                        if (idx >= 0) return idx;
                    }
                }
                return -1;
            }
        }
        return -1;
    }

    private static List<Type> arityTypes(int argCount) {
        if (argCount < 0) return List.of();
        java.util.ArrayList<Type> l = new java.util.ArrayList<>();
        for (int i = 0; i < argCount; i++) l.add(Type.UnknownType.UNKNOWN);
        return l;
    }

    private static int indexOfSymbol(List<String> methods, String symbol) {
        for (int i = 0; i < methods.size(); i++) {
            if (methods.get(i).equals(symbol)) return i;
        }
        return -1;
    }

    /** §131-residual: paramTypes do método (clazz,name) que casa com os tipos
     *  do call site (aridade + tipo). Prefere casamento exato; sem ele, cai na
     *  1ª assinatura da aridade (arg Unknown). Lista vazia se não achar. */
    static java.util.List<Type> methodsForCall(IRClass clazz, String methodName, List<Type> argTypes) {
        IRMethod arityMatch = null;
        for (IRMethod m : clazz.methods()) {
            if (!m.name().equals(methodName)) continue;
            if (m.parameterTypes().size() != argTypes.size()) continue;
            if (arityMatch == null) arityMatch = m;
            boolean ok = true;
            for (int i = 0; i < argTypes.size(); i++) {
                Type declared = m.parameterTypes().get(i);
                Type arg = argTypes.get(i);
                if (!(declared.equals(arg) || declared.toString().equals(arg.toString()))) { ok = false; break; }
            }
            if (ok) return m.parameterTypes();
        }
        return arityMatch != null ? arityMatch.parameterTypes() : java.util.List.of();
    }

    static void emitStringData(NativeBackend nb, StringBuilder sb) {
        for (String[] entry : nb.stringLiterals) {
            String value = entry[0];
            String label = entry[1];
            String escaped = value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\t", "\\t");
            sb.append(label).append(": .asciz \"").append(escaped).append("\"\n");
        }
        sb.append(".Lnewline: .asciz \"\\n\"\n");
        sb.append(".Lkof_str_true: .asciz \"true\"\n");
        sb.append(".Lkof_str_false: .asciz \"false\"\n");
        sb.append(".balign 8\n");
        sb.append("kof_super_table:\n");
        for (IRClass clazz : nb.allClassesMap.values()) {
            if (clazz.typeId() == 0) continue;
            int superTypeId = 0;
            if (clazz.superName() != null && !clazz.superName().isEmpty()) {
                String superSimple = clazz.superName().substring(clazz.superName().lastIndexOf('/') + 1);
                for (IRClass other : nb.allClassesMap.values()) {
                    if (other.name().equals(clazz.superName()) || other.name().endsWith("/" + superSimple)
                            || superSimple.equals(nb.sanitizeName(other.name()))) {
                        superTypeId = other.typeId();
                        break;
                    }
                }
            }
            sb.append("    .long ").append(clazz.typeId()).append(", ").append(superTypeId).append("\n");
        }
        sb.append("    .long 0, 0\n");
    }

}