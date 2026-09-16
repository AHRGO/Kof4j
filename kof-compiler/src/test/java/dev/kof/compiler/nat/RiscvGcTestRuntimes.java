package dev.kof.compiler.nat;

/**
 * FLT001 (15/09): os harnesses de GC (G-1..G-4) montavam o runtime COMPLETO
 * ({@link RiscvSlices#renderRuntime()}) e o ligavam ESTATICAMENTE. Com a
 * entrada da slice B45 (double/float→string via libc snprintf/strtod) o
 * runtime completo passou a referenciar símbolos de libc — link estático
 * quebra (undefined reference a snprintf/strtod).
 *
 * <p>Aqui podamos o runtime EXATAMENTE como a produção faz
 * ({@link RiscvSlices#keepForProgramText}) antes de montar: um harness que não
 * imprime FP não carrega a B45 e segue estático/hermético (sem libc, sem
 * qemu-LD). É o caminho de produção, não um atalho.
 */
final class RiscvGcTestRuntimes {

    private RiscvGcTestRuntimes() {}

    /** Runtime podado às peças alcançáveis pelo texto do harness. */
    static String prunedFor(String harness) {
        return RiscvSlices.renderSubset(RiscvSlices.keepForProgramText(harness));
    }
}
