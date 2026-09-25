package dev.kof.compiler.nat.mcu;

import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.IRModule;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofGetStatic;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofOperation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * B-4.1 (PLAN-BAREMETAL-BOOT): emissor do MCU RV32I.
 *
 * <p>Fatia mínima e honesta: compila {@code main} cujo corpo imprime literais
 * String ({@code print}/{@code println}) para a UART do {@code qemu-system-riscv32
 * -M virt} (0x10000000) e encerra pelo test device (0x100000). Não há
 * runtime: o programa é reduzido à sequência de escritas. Qualquer operação
 * fora desse subset falha com {@code NATIVE002} — nunca um artefato que finge
 * rodar (R6, Q7). É o degrau inicial do codegen 32-bit RISC-V; o motor de
 * emissão completo (registradores, chamadas, GC) vem em B-4.2+.
 */
public final class NativeMcuRiscv32 {

    private static final String UART0 = "0x10000000";
    private static final String TEST_DEV = "0x100000";

    private NativeMcuRiscv32() {}

    public static void emit(IRModule module, Path outputDir) throws IOException {
        IRClass mainCls = findMainClass(module);
        if (mainCls == null) {
            throw new IllegalStateException(
                    "NATIVE002: MCU riscv32 slice requires a main function");
        }
        IRMethod main = findMethod(mainCls, "main");
        if (main == null) {
            throw new IllegalStateException(
                    "NATIVE002: MCU riscv32 slice requires a main function");
        }

        List<String> output = collectPrints(main);

        String className = mainCls.name();
        Path asmFile = outputDir.resolve(className + ".s");
        Path ldFile = outputDir.resolve(className + ".ld");
        Path binFile = outputDir.resolve(className);
        Path objFile = outputDir.resolve(className + ".o");
        Files.createDirectories(asmFile.getParent());

        Files.writeString(asmFile, renderAsm(output), StandardCharsets.UTF_8);
        Files.writeString(ldFile, LINKER_SCRIPT, StandardCharsets.UTF_8);

        String as = System.getenv().getOrDefault("KOF_MCU_AS", "riscv64-linux-gnu-as");
        String ld = System.getenv().getOrDefault("KOF_MCU_LD", "riscv64-linux-gnu-ld");
        try {
            run(new String[]{as, "-march=rv32i", "-mabi=ilp32", "-o", objFile.toString(),
                    asmFile.toString()}, "riscv32-as");
            run(new String[]{ld, "-m", "elf32lriscv", "-T", ldFile.toString(),
                    "-o", binFile.toString(), objFile.toString()}, "riscv32-ld");
            binFile.toFile().setExecutable(true);
            System.err.println("NativeMcuRiscv32: generated riscv32 " + binFile);
        } catch (IOException e) {
            System.err.println("NativeBackend: riscv32 MCU toolchain missing (NATIVE002),"
                    + " keeping asm: " + e.getMessage());
        }
    }

    private static IRClass findMainClass(IRModule module) {
        for (IRClass c : module.classes()) {
            if (findMethod(c, "main") != null) return c;
        }
        return null;
    }

    private static IRMethod findMethod(IRClass c, String name) {
        for (IRMethod m : c.methods()) {
            if (name.equals(m.name())) return m;
        }
        return null;
    }

    /**
     * Percorre as ops de {@code main} aceitando apenas o padrão
     * {@code System.out.print/println(String literal)}; devolve o texto de
     * cada escrita (com {@code \n} nos {@code println}). Qualquer op fora do
     * subset → {@code NATIVE002}.
     */
    private static List<String> collectPrints(IRMethod main) {
        List<String> out = new ArrayList<>();
        String pending = null;
        for (IRBasicBlock bb : main.basicBlocks()) {
            for (KofOperation op : bb.operations()) {
                if (op instanceof KofGetStatic gs) {
                    if ("out".equals(gs.name())) continue;
                    throw unsupported(gs.getClass().getSimpleName());
                }
                if (op instanceof KofLoadLiteral lit) {
                    if (lit.value() instanceof String s) {
                        pending = s;
                        continue;
                    }
                    throw unsupported("literal " + lit.type());
                }
                // print/println(String) passa por String.valueOf antes do
                // println (ExpressionPrintLowerer); para um literal String é
                // um no-op — apenas mantém o pending.
                if (op instanceof KofCall vo && "valueOf".equals(vo.methodName())
                        && vo.kind() == KofCallKind.STATIC) {
                    continue;
                }
                if (op instanceof KofCall call
                        && ("print".equals(call.methodName()) || "println".equals(call.methodName()))
                        && call.kind() == KofCallKind.INSTANCE) {
                    if (pending == null) {
                        throw new IllegalStateException("NATIVE002: MCU riscv32 slice only"
                                + " supports print/println of a String literal");
                    }
                    out.add("println".equals(call.methodName()) ? pending + "\n" : pending);
                    pending = null;
                    continue;
                }
                if (op instanceof dev.kof.compiler.KofReturnVoid
                        || op instanceof dev.kof.compiler.KofReturn) {
                    continue;
                }
                throw unsupported(op.getClass().getSimpleName());
            }
        }
        return out;
    }

    private static IllegalStateException unsupported(String what) {
        return new IllegalStateException("NATIVE002: MCU riscv32 slice does not support '"
                + what + "' yet (only print/println of String literals in main)");
    }

    private static String renderAsm(List<String> output) {
        StringBuilder sb = new StringBuilder();
        sb.append(".option norvc\n");
        sb.append(".section .text\n");
        sb.append(".globl _start\n");
        sb.append("_start:\n");
        sb.append("    la sp, _stack_top\n");
        // Trap vector: an unexpected trap halts honestly instead of running off
        // to mtvec=0. The reset path itself is _start at the load base (asserted
        // in NativeMcuE2ETest#mcuResetEntryIsAtLoadBase).
        sb.append("    la t0, .Lmcu_trap\n");
        sb.append("    csrw mtvec, t0\n");
        for (int i = 0; i < output.size(); i++) {
            byte[] b = output.get(i).getBytes(StandardCharsets.UTF_8);
            sb.append("    la a0, .Lmcu_str_").append(i).append('\n');
            sb.append("    li a1, ").append(b.length).append('\n');
            sb.append("    call kof_plat_write\n");
        }
        sb.append("    li a0, 0\n");
        sb.append("    call kof_plat_exit\n");
        sb.append(".Lmcu_halt:\n");
        sb.append("    j .Lmcu_halt\n\n");
        // MCU HAL bodies (PLAN-BAREMETAL-BOOT §3): kof_plat_write(buf,len) to the
        // virt UART and kof_plat_exit(code) via the test device. sync/thread are
        // absent on the single-core MCU (CONC003), never stubbed.
        sb.append(".globl kof_plat_write\n");
        sb.append("kof_plat_write:\n");
        sb.append("    add t2, a0, a1\n");
        sb.append("    mv t0, a0\n");
        sb.append("    li t1, ").append(UART0).append('\n');
        sb.append(".Lmcu_write_loop:\n");
        sb.append("    bgeu t0, t2, .Lmcu_write_done\n");
        sb.append("    lbu a0, 0(t0)\n");
        sb.append("    sb a0, 0(t1)\n");
        sb.append("    addi t0, t0, 1\n");
        sb.append("    j .Lmcu_write_loop\n");
        sb.append(".Lmcu_write_done:\n");
        sb.append("    ret\n\n");
        sb.append(".globl kof_plat_exit\n");
        sb.append("kof_plat_exit:\n");
        sb.append("    li t1, ").append(TEST_DEV).append('\n');
        sb.append("    li t0, 0x5555\n");
        sb.append("    sw t0, 0(t1)\n");
        sb.append(".Lmcu_exit_halt:\n");
        sb.append("    j .Lmcu_exit_halt\n\n");
        sb.append(".align 2\n");
        sb.append(".Lmcu_trap:\n");
        sb.append("    j .Lmcu_trap\n\n");
        sb.append(".section .rodata\n");
        for (int i = 0; i < output.size(); i++) {
            sb.append(".Lmcu_str_").append(i).append(":\n");
            sb.append("    .ascii ").append(asmBytes(output.get(i))).append('\n');
        }
        return sb.toString();
    }

    private static String asmBytes(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        StringBuilder b = new StringBuilder("\"");
        for (byte value : bytes) {
            int c = value & 0xFF;
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '"' -> b.append("\\\"");
                default -> {
                    if (c >= 32 && c < 127) b.append((char) c);
                    else b.append(String.format("\\%03o", c));
                }
            }
        }
        return b.append('"').toString();
    }

    private static void run(String[] cmd, String what) throws IOException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out;
        try {
            out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) || p.exitValue() != 0) {
                throw new IOException(what + " failed: " + out);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(what + " interrupted", e);
        }
    }

    private static final String LINKER_SCRIPT = """
            ENTRY(_start)
            SECTIONS {
              . = 0x80000000;
              .text : { *(.text*) }
              .rodata : { *(.rodata*) }
              .data : { *(.data*) }
              .bss : { *(.bss*) *(COMMON) }
              . = ALIGN(16);
              _stack_top = . + 0x4000;
            }
            """;
}
