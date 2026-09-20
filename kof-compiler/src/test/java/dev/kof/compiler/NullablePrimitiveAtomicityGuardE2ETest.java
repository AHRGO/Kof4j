package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * #278 scope 3 — guarda de atomicidade da representacao de primitivo-nullable.
 *
 * O invariante do follow-up do D-NULL-INTENT: uma vez que o frontend decide
 * que um Int? carrega null real, TODAS as camadas JVM precisam concordar com
 * a representacao boxed — descritor, slot local, opcode de retorno, call
 * site e comparacao com null. A entrega parcial do §241 (descritor boxed +
 * corpo primitivo) gerou VerifyError em classe gerada; este teste trava a
 * face estrutural para que uma divergencia entre camadas falhe AQUI, antes
 * do gate de release, e nao em runtime.
 *
 * Medida dupla: (a) estrutural — o .class gerado e inspecionado com ASM
 * (mesmo padrao do RuntimeConstantInliningGuardTest/§257); (b) observavel —
 * o corpus semantico do proprio corpo da issue roda no JVM e imprime o
 * contrato true/false/7 (null real distinto de 0).
 */
class NullablePrimitiveAtomicityGuardE2ETest {

    private static final String CORPUS = """
        Int? maybe(Boolean none) {
            if (none) {
                return null
            }
            return 7
        }

        main() {
            var a = maybe(true)
            var b = maybe(false)

            println(a == null)
            println(b == null)
            println(b)
        }
        """;

    private record MethodShape(String desc, List<Integer> opcodes, List<String> calls) {}

    private static MethodShape inspect(Path classFile, String methodName) throws IOException {
        List<Integer> ops = new ArrayList<>();
        List<String> calls = new ArrayList<>();
        String[] desc = new String[1];
        try (InputStream in = Files.newInputStream(classFile)) {
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if (!name.equals(methodName)) {
                        return null;
                    }
                    desc[0] = descriptor;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitInsn(int opcode) {
                            ops.add(opcode);
                        }

                        @Override
                        public void visitVarInsn(int opcode, int var) {
                            ops.add(opcode);
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name,
                                String descriptor, boolean isInterface) {
                            calls.add(owner + "." + name + ":" + descriptor);
                        }
                    };
                }
            }, 0);
        }
        assertEquals(true, desc[0] != null, "metodo '" + methodName + "' nao existe no .class gerado");
        return new MethodShape(desc[0], ops, calls);
    }

    private Path compileCorpus(Path tempDir) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, CORPUS);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = new CompilerDriver().compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        return outDir.resolve("Default").resolve("Main.class");
    }

    @Test
    void nullableIntReturnIsBoxedInDescriptorAndAreTURN(@TempDir Path tempDir) throws IOException {
        MethodShape maybe = inspect(compileCorpus(tempDir), "maybe");
        assertTrue(maybe.desc().endsWith(")Ljava/lang/Integer;"),
                "descritor de retorno nao-boxed (face §241 viva): " + maybe.desc());
        assertTrue(maybe.opcodes().contains(Opcodes.ARETURN),
                "sem ARETURN no corpo de Int?: descritor e corpo divergem");
        assertFalse(maybe.opcodes().contains(Opcodes.IRETURN),
                "IRETURN convivendo com descritor boxed = entrega parcial");
    }

    @Test
    void callSiteAgreesWithBoxedDescriptor(@TempDir Path tempDir) throws IOException {
        MethodShape main = inspect(compileCorpus(tempDir), "main");
        boolean callSiteBoxed = main.calls().stream()
                .anyMatch(c -> c.contains(".maybe:") && c.endsWith(")Ljava/lang/Integer;"));
        assertTrue(callSiteBoxed,
                "call-site do main nao espera a referencia do descritor real: " + main.calls());
    }

    @Test
    void nullComparisonIsRealReferenceCheckNotFoldedConstant(@TempDir Path tempDir) throws IOException {
        MethodShape main = inspect(compileCorpus(tempDir), "main");
        boolean refCheck = main.opcodes().contains(Opcodes.IFNULL)
                || main.opcodes().contains(Opcodes.IFNONNULL);
        assertTrue(refCheck,
                "nenhum IFNULL/IFNONNULL no main: a comparacao com null foi dobrada "
                        + "(slot boxed otimizado como primitivo = face exata do §241)");
    }

    @Test
    void nullableLocalsNeverUsePrimitiveSlots(@TempDir Path tempDir) throws IOException {
        MethodShape main = inspect(compileCorpus(tempDir), "main");
        assertFalse(main.opcodes().contains(Opcodes.ISTORE),
                "ISTORE em metodo que so tem locais Int? — slot primitivo com descritor boxed");
        assertFalse(main.opcodes().contains(Opcodes.ILOAD),
                "ILOAD em metodo que so tem locais Int? — leitura primitiva de valor boxed");
    }

    @Test
    void observableContractMatchesTheIssuedCorpus(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, CORPUS);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = new CompilerDriver().compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        java.nio.file.Path javaHome = Path.of(System.getProperty("java.home"));
        Process p = new ProcessBuilder(javaHome.resolve("bin").resolve("java").toString(),
                "-Xverify:all", "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        try {
            assertEquals(0, p.waitFor(), "exit code do corpus (output: " + output + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        assertEquals("true\nfalse\n7", output,
                "contrato observavel do nullable-primitivo mudou (null real != 0)");
    }
}
