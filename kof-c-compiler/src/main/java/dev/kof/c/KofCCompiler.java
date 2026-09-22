package dev.kof.c;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * KofCcompiler — native-only C subset compiler.
 * Input: .c file with subset grammar.
 * Output: ELF64 executable via GAS + LD, no alvo escolhido.
 * No JVM target.
 */
public final class KofCCompiler {

    public record CompileResult(boolean success, String diagnostics, Path binary) {}

    /** Compatibilidade: sem alvo explícito, emite para o host (x86_64). */
    public static CompileResult compile(Path cFile, Path outDir) throws IOException {
        return compile(cFile, outDir, KofCTarget.X86_64);
    }

    public static CompileResult compile(Path cFile, Path outDir, KofCTarget target) throws IOException {
        String src = Files.readString(cFile);
        var lexer = new KofCLexer(src);
        List<KofCToken> toks = lexer.lex();
        var parser = new KofCParser(toks);
        var prog = parser.parseProgram();

        // nunca emitir binario a partir de uma AST lixo (R6/Q7): o parser
        // sincroniza apos o erro, mas o resultado nao e valido — reporta e para.
        if (parser.hasErrors()) {
            return new CompileResult(false,
                    String.join("\n", parser.errors()), null);
        }

        // basic validation: need main
        boolean hasMain = prog.funcs().stream().anyMatch(f -> f.name().equals("main"));
        if (!hasMain) {
            return new CompileResult(false, "missing main() function", null);
        }

        KofCEmitter emitter = switch (target) {
            case X86_64 -> new KofCEmitterX86(prog);
            case RISCV64 -> new KofCEmitterRiscv(prog);
            case AARCH64 -> new KofCEmitterAarch(prog);
        };
        String asm = emitter.emit();

        Files.createDirectories(outDir);
        Path sFile = outDir.resolve("kofc.s");
        Files.writeString(sFile, asm);

        Path oFile = outDir.resolve("kofc.o");
        Path bin = outDir.resolve(cFile.getFileName().toString().replaceFirst("\\.c$", ""));
        if (bin.toString().endsWith(".c")) bin = outDir.resolve("a.out");
        // also ensure we produce file without extension for execution
        if (!bin.getFileName().toString().contains(".")) {
            // keep as is
        } else {
            bin = outDir.resolve("kofc_bin");
        }

        // as (binário + flags do alvo)
        List<String> asCmd = new ArrayList<>(target.assembler());
        asCmd.add("-o");
        asCmd.add(oFile.toString());
        asCmd.add(sFile.toString());
        ProcessBuilder pbAs = new ProcessBuilder(asCmd);
        pbAs.redirectErrorStream(true);
        Process pAs = pbAs.start();
        String asOut = new String(pAs.getInputStream().readAllBytes());
        try { pAs.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (pAs.exitValue() != 0) {
            return new CompileResult(false, "as failed: " + asOut + "\n" + asm, null);
        }

        // ld — freestanding (`-e _start`), com o linker do alvo.
        ProcessBuilder pbLd = new ProcessBuilder(
                target.linker(), "-o", bin.toString(), "-e", "_start", oFile.toString());
        pbLd.redirectErrorStream(true);
        Process pLd = pbLd.start();
        String ldOut = new String(pLd.getInputStream().readAllBytes());
        try { pLd.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (pLd.exitValue() != 0) {
            if (target != KofCTarget.X86_64) {
                return new CompileResult(false, "ld failed: " + ldOut + "\n" + asm, null);
            }
            // fallback to gcc (só host x86_64 — cross não tem cc)
            ProcessBuilder pbGcc = new ProcessBuilder("gcc", "-nostdlib", "-o", bin.toString(), oFile.toString());
            pbGcc.redirectErrorStream(true);
            Process pGcc = pbGcc.start();
            String gccOut = new String(pGcc.getInputStream().readAllBytes());
            try { pGcc.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (pGcc.exitValue() != 0) {
                return new CompileResult(false, "ld failed: " + ldOut + "\ngcc failed: " + gccOut + "\n" + asm, null);
            }
        }
        // chmod +x
        bin.toFile().setExecutable(true);
        return new CompileResult(true, "", bin);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: KofCCompiler <file.c> [-o outDir] [--target x86_64|riscv64|aarch64]");
            System.exit(1);
        }
        Path cFile = Path.of(args[0]);
        Path outDir = Files.createTempDirectory("kofc-out");
        KofCTarget target = KofCTarget.X86_64;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("-o") && i + 1 < args.length) {
                outDir = Path.of(args[++i]);
            } else if (args[i].equals("--target") && i + 1 < args.length) {
                target = KofCTarget.parse(args[++i]);
            }
        }
        var res = compile(cFile, outDir, target);
        if (!res.success()) {
            System.err.println(res.diagnostics());
            System.exit(1);
        }
        System.out.println("built " + res.binary());
    }
}
