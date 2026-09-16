package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

/**
 * §257 — guarda estrutural (renumbered from §253 after the 15/09 collision with the time.interval §253 of lane .18): nenhum texto de runtime do JS/Native pode ser
 * constante de compilação. Um campo {@code static final String} inicializado
 * por LITERAL (ConstantValue no bytecode) é INLINED pelo javac em todo
 * consumidor cross-class; editar o produtor sem recompilar o consumidor deixa
 * o {@code .class} do consumidor com a cópia VELHA — foi o
 * {@code KofValidationTest#validationBrJs} vermelho-falso de 15/09:
 * {@code JsRuntimeUiCrypto} ganhou {@code kofValidationIsNis}, mas
 * {@code JsRuntimeSlices.class} (fonte não alterada, não recompilada pelo
 * maven incremental) continuou carregando o texto sem a função → o runtime
 * {@code .mjs} saiu sem o export → exit 1 com output vazio, sem nenhum erro
 * no compila (R6). O fix tirou {@code final} dos campos literais (o valor
 * passa a ser resolvido em runtime via {@code getstatic} — sempre fresco);
 * este teste trava o invariante: campo de runtime String NUNCA com
 * ConstantValue.
 */
class RuntimeConstantInliningGuardTest {

    private static final Pattern RUNTIME_FIELD = Pattern.compile(
            "^(UI_[A-Z_]*RUNTIME|CORE_RUNTIME|IO_RUNTIME|STD.*_RUNTIME|.*_RUNTIME|.*_ASM(_[A-Z0-9]+)*|RISCV_STRN[0-9]*_ASM|RISCV_MAPSET_ASM)$");

    @Test
    void runtimeTextFieldsWithCompileTimeConstantValuesFailTheGuard() throws Exception {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        for (String pkg : List.of("dev.kof.compiler.js", "dev.kof.compiler.nat")) {
            ClassLoader cl = RuntimeConstantInliningGuardTest.class.getClassLoader();
            var roots = cl.getResources(pkg.replace('.', '/'));
            while (roots.hasMoreElements()) {
                URL root = roots.nextElement();
                if (!root.getProtocol().equals("file")) continue;
                Path dir = Paths.get(root.toURI());
                try (var s = Files.walk(dir)) {
                    var classes = s.filter(p -> p.getFileName().toString().endsWith(".class")).toList();
                    scanned += classes.size();
                    for (Path p : classes) {
                        if (p.getFileName().toString().contains("$")) continue;
                        try (InputStream in = Files.newInputStream(p)) {
                            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                                String owner = "?";
                                @Override
                                public void visit(int version, int access, String aname, String sig,
                                                  String superName, String[] interfaces) {
                                    owner = aname;
                                }
                                @Override
                                public FieldVisitor visitField(int access, String name, String desc,
                                                               String sig, Object value) {
                                    boolean runtimeString = desc.equals("Ljava/lang/String;")
                                            && (access & Opcodes.ACC_STATIC) != 0
                                            && RUNTIME_FIELD.matcher(name).matches();
                                    if (runtimeString && value != null) {
                                        offenders.add(owner + "." + name + "=ConstantValue(literal)");
                                    }
                                    return null;
                                }
                            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                        }
                    }
                }
            }
        }
        assertTrue(scanned >= 100,
                "guarda vira facade se a varredura não chegar no bytecode real: classes varridas=" + scanned);
        assertTrue(offenders.isEmpty(),
                "§257 — campo de texto de runtime com ConstantValue = INLINED pelo javac nos "
                        + "consumidores cross-class = vermelho-falso quando o build incremental não "
                        + "recompila o consumidor (validationBrJs 15/09: JsRuntimeSlices.class velho "
                        + "via a cópia sem kofValidationIsNis). Tire o `final` ou use `= method()`. "
                        + "Ofensores: " + offenders);
    }

    /** Sanidade da varredura: o padrão pega os nomes reais (e ignorar Modifier é intencional —
     *  `static` + desc String + nome-bate é o universo; o filtro de perigo é ConstantValue). */
    @Test
    void guardPatternMatchesTheRuntimeFields() {
        for (String n : List.of("UI_CRYPTO_RUNTIME", "CORE_RUNTIME", "IO_RUNTIME",
                "RISCV_RUNTIME_ASM_B_11", "RISCV_MAPSET_ASM", "UI_VALIDATION_RUNTIME")) {
            assertTrue(RUNTIME_FIELD.matcher(n).matches(), "padrão deveria bater: " + n);
        }
    }
}
