package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;


/**
 * Lowering de MethodCallExpr SEM receiver (chamadas nuas: super/driver,
 * metodo da propria classe, construtor por nome, lambda-var e funcao de topo).
 */
public final class ExpressionBareCallLowerer {

    private ExpressionBareCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
            String owner, int localIdx, List<IRLocalVariable> locals) {
        if (("super".equals(mc.methodName()) || "driver".equals(mc.methodName()))
                && driver.semanticAnalyzer != null && owner != null && !owner.isEmpty()) {
            // super(args): construtor da superclasse (Object quando
            // a classe não tem extends). driver(args): delegação para
            // outro construtor da própria classe — o alvo executa
            // super() e os inicializadores de campo.
            boolean delegation = "driver".equals(mc.methodName());
            String targetInternal;
            if (delegation) {
                targetInternal = owner;
            } else {
                targetInternal = HierarchyResolver.findSuperClass(owner, driver.semanticAnalyzer);
                if (targetInternal == null) targetInternal = "java/lang/Object";
                targetInternal = targetInternal.replace('.', '/');
            }
            Type targetType = CompilerTypes.ownerTypeFromInternal(targetInternal, driver.semanticAnalyzer);
            SymbolTable.ClassSymbol targetCs = driver.semanticAnalyzer.getClass(
                    targetInternal.substring(targetInternal.lastIndexOf('/') + 1));
            SymbolTable.ConstructorSymbol ctor = targetCs != null
                    ? SymbolTable.constructorFor(targetCs.members(), mc.arguments().size()) : null;
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            ops.add(new KofLoadLocal(CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer), 0));
            List<Type> ctorParamTypes;
            if (ctor != null && ctor.parameterTypes().size() == mc.arguments().size()) {
                ctorParamTypes = ctor.parameterTypes();
            } else {
                if (targetCs != null && driver.currentDiagnostics != null) {
                    // classe conhecida e nenhum construtor com essa
                    // aridade — erro em compile-time
                    SourcePosition p = mc.position();
                    driver.currentDiagnostics.error(p != null ? p.file() : "",
                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                            (delegation ? "no constructor of '" : "no super constructor of '")
                                    + targetInternal.substring(targetInternal.lastIndexOf('/') + 1)
                                    + "' with " + mc.arguments().size() + " argument(s)",
                            "SEM017");
                    return localIdx;
                }
                ctorParamTypes = argTypes;
            }
            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), ctorParamTypes, ops, owner, localIdx, locals);
            ops.add(new KofCall(targetType, "<init>", ctorParamTypes, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
            return localIdx;
        }
        SymbolTable.MethodSymbol selfMethod = driver.semanticAnalyzer != null
                ? driver.semanticAnalyzer.getResolvedMethod(mc) : null;
        if (selfMethod != null && owner != null && !owner.isEmpty()
                && !"<init>".equals(selfMethod.name())
                && selfMethod.ownerClass() != null) {
            Type ownerType = CompilerTypes.ownerTypeFromInternal(selfMethod.ownerClass(), driver.semanticAnalyzer);
            // Método ESTÁTICO da própria classe chamado sem receiver (ex.:
            // `twice(21)` dentro de outra static, ou no <clinit> de um campo
            // estático): invokestatic SEM receiver — antes emitia aload_0 +
            // invokevirtual → IncompatibleClassChangeError (contexto de
            // instância) / VerifyError (contexto estático).
            if ((selfMethod.accessFlags() & AccessFlags.STATIC) != 0) {
                localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), selfMethod.parameterTypes(),
                        ops, owner, localIdx, locals);
                ops.add(new KofCall(ownerType, mc.methodName(), selfMethod.parameterTypes(),
                        selfMethod.returnType(), KofCallKind.STATIC));
                return localIdx;
            }
            ops.add(new KofLoadLocal(ownerType, 0));
            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), selfMethod.parameterTypes(),
                    ops, owner, localIdx, locals);
            // #213: chamada nua dentro de default method de interface resolve p/ a
            // PRÓPRIA interface — invokestatic/invokevirtual não valem; o JVM exige
            // invokeinterface (senão IncompatibleClassChangeError "Found interface").
            KofCallKind selfKind = KofCallKind.INSTANCE;
            // selfMethod != null so aqui SOMENTE porque o ternario acima passou por
            // driver.semanticAnalyzer != null — o re-check era dead-code (CodeQL #716,
            // confirmado pelo proprio dominance). Removido; nenhum caminho alterado.
            String selfOwner = selfMethod.ownerClass();
            if (selfOwner.contains("/")) selfOwner = selfOwner.substring(selfOwner.lastIndexOf('/') + 1);
            if (driver.semanticAnalyzer.isInterfaceType(selfOwner)) selfKind = KofCallKind.INTERFACE;
            ops.add(new KofCall(ownerType, mc.methodName(), selfMethod.parameterTypes(),
                    selfMethod.returnType(), selfKind));
            return localIdx;
        }
        SymbolTable.ClassSymbol cs = driver.semanticAnalyzer != null ? driver.semanticAnalyzer.getClass(mc.methodName()) : null;
        if (cs != null) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            SymbolTable.ConstructorSymbol ctor = null;
            SymbolTable.Symbol ctorSym = cs.members().resolve("<init>");
            if (ctorSym instanceof SymbolTable.ConstructorSymbol ctorSingle) ctor = ctorSingle;
            ops.add(new KofNewObject(cs.type(), argTypes));
            ops.add(new KofDup());
            List<Type> ctorParamTypes = (ctor != null
                    && ctor.parameterTypes().size() == mc.arguments().size())
                    ? ctor.parameterTypes() : argTypes;
            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), ctorParamTypes, ops, owner, localIdx, locals);
            ops.add(new KofCall(cs.type(), "<init>", ctorParamTypes, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        } else {
            IRLocalVariable lambdaVar = driver.findLocalVar(mc.methodName(), locals);
            if (lambdaVar != null && lambdaVar.type() instanceof Type.FunctionType lft) {
                if (lft.className() == null) {
                    // bug 8: valor de TIPO DE FUNÇÃO DECLARADO (param
                    // (s: (Int) -> Int), sem classe sintética). Todas
                    // as lambdas da assinatura implementam a interface
                    // sintética — invoca via INVOKEINTERFACE.
                    localIdx = ExpressionLowerer.emitExpression(driver, new IdentifierExpr(mc.position(), mc.methodName()),
                            ops, owner, localIdx, locals);
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), lft.parameterTypes(),
                            ops, owner, localIdx, locals);
                    Type iface = driver.lambdaInterfaceType(lft);
                    ops.add(new KofCall(iface, "invoke", argTypes, lft.returnType(),
                            KofCallKind.INTERFACE));
                } else {
                localIdx = ExpressionLowerer.emitExpression(driver, new IdentifierExpr(mc.position(), mc.methodName()),
                        ops, owner, localIdx, locals);
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), lft.parameterTypes(), ops, owner, localIdx, locals);
                Type invokeOwner = new Type.ClassType("", lft.className(), List.of());
                ops.add(new KofCall(invokeOwner, "invoke", argTypes, lft.returnType(), KofCallKind.INSTANCE));
                }
            } else {
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                Type returnType = Type.UnknownType.UNKNOWN;
                if (driver.currentUnit != null) {
                    // SG-011B: mesmo veredicto do typer (frontend único). Único
                    // candidato → caminho idêntico ao antigo; ≥2 → assinatura.
                    List<FunctionDeclarationNode> ovlFns = new ArrayList<>();
                    for (AstNode d : driver.currentUnit.declarations()) {
                        if (d instanceof FunctionDeclarationNode fn && fn.name().equals(mc.methodName())) ovlFns.add(fn);
                    }
                    FunctionDeclarationNode chosen = ovlFns.isEmpty() ? null : ovlFns.get(0);
                    if (ovlFns.size() > 1) {
                        List<TopLevelOverload.Candidate> ovlCands = new ArrayList<>();
                        for (FunctionDeclarationNode fn : ovlFns) {
                            List<Type> pt = fn.parameters().stream()
                                    .map(pp -> CompilerTypes.resolveWithTypeParams(pp.type(), fn.typeParameters(), driver.currentUnit, driver.semanticAnalyzer)).toList();
                            ovlCands.add(new TopLevelOverload.Candidate(fn, pt,
                                    TopLevelOverload.requiredArityOf(fn)));
                        }
                        TopLevelOverload.Status[] st = new TopLevelOverload.Status[1];
                        int sel = TopLevelOverload.pick(ovlCands, argTypes, st);
                        if (sel >= 0) chosen = ovlCands.get(sel).fn();
                    }
                    if (chosen != null) {
                        returnType = CompilerTypes.resolveWithTypeParams(chosen.returnType(), chosen.typeParameters(), driver.currentUnit, driver.semanticAnalyzer);
                        List<Type> fnTypes = new ArrayList<>();
                        for (var pp : chosen.parameters()) fnTypes.add(CompilerTypes.resolveWithTypeParams(pp.type(), chosen.typeParameters(), driver.currentUnit, driver.semanticAnalyzer));
                        boolean hasDefaults = chosen.parameters().stream()
                                .anyMatch(p -> p.defaultExpression() != null);
                        if (hasDefaults && mc.arguments().size() < fnTypes.size()) {
                            argTypes = fnTypes.subList(0, mc.arguments().size());
                        } else {
                            argTypes = fnTypes;
                        }
                    }
                }
                localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), argTypes, ops, owner, localIdx, locals);
                ops.add(new KofCall(CompilerTypes.mainClassType(driver.currentModule), mc.methodName(), argTypes, returnType, KofCallKind.FUNCTION));
                Type effective = ExpressionTyper.inferExprType(driver, mc, locals);
                if (returnType instanceof Type.TypeVariable && TypeMetrics.isPrimitiveType(effective)) {
                    driver.emitErasureUnbox(ops, effective);
                }
            }
        }
        return localIdx;
    }
}
