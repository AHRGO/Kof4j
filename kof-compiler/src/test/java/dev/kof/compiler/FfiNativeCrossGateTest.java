package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #431/§61 — gate de binding do `extern` por alvo NATIVO, SEM toolchain.
 *
 * <p>A regressão real (21/09): as fatias de struct-por-valor (3.7/3.8b) reabriram
 * o gate NATIVO com um `if (driver.target != Target.NATIVE) return false;` de
 * topo, que fecha o caminho ESCALAR do riscv64/aarch64 (shim LP64/AAPCS64 do
 * #431 fatia 2). O sintoma só aparecia onde há qemu + toolchain cross — os hosts
 * sem toolchain PULAM o {@link FfiNativeCrossE2ETest} ({@code assumeTrue}) e a
 * regressão passou silenciosa. Este teste fecha essa cegueira: exercita
 * {@link CompilerPipeline#isExternBound} direto, que é pré-codegen e independe
 * do assembler/linker.
 *
 * <p>Contrato (R6 + fatias R3): escalar binda em TODO alvo nativo; struct por
 * valor fica x86-64-only até a fatia 3 (riscv64/aarch64 = FFI001 honesto).
 */
class FfiNativeCrossGateTest {

    private static CompilerDriver driver(Target t) {
        CompilerDriver d = new CompilerDriver();
        d.target = t;
        return d;
    }

    private static ExternalFunctionNode scalarExtern() {
        List<FormalParameterNode> ps = new ArrayList<>();
        ps.add(new FormalParameterNode(null, List.of(), "Int", "x"));
        return new ExternalFunctionNode(null, "libc.so.6", "Int", "abs", ps);
    }

    private static ExternalFunctionNode structReturnExtern() {
        return new ExternalFunctionNode(null, "libc.so.6", "Point", "make", List.of());
    }

    @Test
    void scalarExternBindsOnEveryNativeTarget() {
        for (Target t : List.of(Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64)) {
            assertTrue(CompilerPipeline.isExternBound(driver(t), scalarExtern()),
                    "extern escalar (Int→Int + library()) deve bindar no " + t
                            + " — #431 fatia 2 abriu o shim cross e as fatias de struct não podem re-fechá-lo");
        }
    }

    @Test
    void scalarExternWithoutLibraryStaysGap() {
        ExternalFunctionNode noLib = new ExternalFunctionNode(null, null, "Int", "abs", List.of());
        assertFalse(CompilerPipeline.isExternBound(driver(Target.NATIVE_RISCV64), noLib),
                "extern sem library() não tem o que linkar → FFI001 (R6)");
    }

    @Test
    void structByValueReturnStaysX86Only() {
        assertFalse(CompilerPipeline.isExternBound(driver(Target.NATIVE_RISCV64), structReturnExtern()),
                "retorno de struct no riscv64 ainda é FFI001 (fatia 3)");
        assertFalse(CompilerPipeline.isExternBound(driver(Target.NATIVE_AARCH64), structReturnExtern()),
                "retorno de struct no aarch64 ainda é FFI001 (fatia 3)");
    }
}
