package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #321 — `interface extends <classe>` aceito no compile e estourava
 * IncompatibleClassChangeError no load (a JVM grava a classe como
 * super_class da interface). Interfaces so podem estender interfaces
 * (SEM064). A face de classe (`class extends <interface>`) e #302 (PR
 * outrolane) — nao tocada aqui.
 */
class InterfaceExtendsClassTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String src) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, src);
        return driver.compile(source, tempDir.resolve("out"), Target.JVM);
    }

    @Test
    void interfaceExtendingClassIsRejectedWithSEM064(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                class Base { Int v() { return 1 } }
                interface J extends Base { }
                main() { println("x") }
                """);
        assertFalse(result.success(),
                "interface extending a CLASS writes it as super_class → IncompatibleClassChangeError; must not compile");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM064".equals(d.code())
                                && d.message().contains("cannot extend class 'Base'")
                                && d.message().contains("'J'")),
                "must report SEM064 naming both: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void interfaceExtendingInterfaceStillCompiles(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                interface A { }
                interface B extends A { Int v() }
                class Impl implements B { Int v() { return 5 } }
                main() { println(Impl().v()) }
                """);
        assertTrue(result.success(),
                "interface extends interface is legal — must keep compiling: "
                        + result.diagnostics().getDiagnostics());
    }

    @Test
    void forwardReferencedParentInterfaceIsNotFalsePositive(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                interface B extends A { Int v() }
                interface A { }
                class Impl implements B { Int v() { return 5 } }
                main() { println(Impl().v()) }
                """);
        assertTrue(result.success(),
                "A declared AFTER B — pre-declare runs before analysis, the check must see A as interface: "
                        + result.diagnostics().getDiagnostics());
    }
}
