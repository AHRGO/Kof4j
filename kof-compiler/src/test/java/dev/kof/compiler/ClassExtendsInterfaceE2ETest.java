package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #302 — `class Dog extends Animal` where Animal is an INTERFACE:
 * the lowering emitted the interface as super_class_index and the JVM
 * rejected the class at LOAD time (IncompatibleClassChangeError), so a
 * program that compiles clean dies before main() runs.
 *
 * Fix (CompilerClassLowering): when the declared parent resolves to an
 * interface, relocate it into interfaces[] and emit java/lang/Object as
 * the real super_class_index.
 */
class ClassExtendsInterfaceE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    @Test
    void extendsInterfaceLoadsAndDispatchesPolymorphically(@TempDir Path tempDir) throws IOException {
        // exact #302 repro: extends on an interface compiles AND loads;
        // dispatch through an interface-typed variable resolves per-class.
        Path src = tempDir.resolve("animal.kf");
        Files.writeString(src, """
                interface Animal {
                    String sound()
                }
                class Dog extends Animal {
                    String sound() { return "woof" }
                }
                class Cat extends Animal {
                    String sound() { return "meow" }
                }
                main() {
                    var a = new Dog()
                    var b = new Cat()
                    Animal x = a
                    Animal y = b
                    println(x.sound())
                    println(y.sound())
                }
                """);
        Path out = tempDir.resolve("animal-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed (#302 extends interface): " + r.diagnostics().getDiagnostics());
        assertEquals("woof\nmeow", runJvm(out));
    }

    @Test
    void extendsInterfacePlusImplementsKeepsBothInterfaces(@TempDir Path tempDir) throws IOException {
        // edge: the relocation path runs WITH a non-empty interfaces[] —
        // both the extends'd interface and the implements'd one must survive.
        Path src = tempDir.resolve("both.kf");
        Files.writeString(src, """
                interface Named {
                    String name()
                }
                interface Animal {
                    String sound()
                }
                class Dog extends Animal implements Named {
                    String sound() { return "woof" }
                    String name() { return "Rex" }
                }
                main() {
                    Dog d = new Dog()
                    println(d.sound() + " " + d.name())
                }
                """);
        Path out = tempDir.resolve("both-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed (#302 extends+implements): " + r.diagnostics().getDiagnostics());
        assertEquals("woof Rex", runJvm(out));
    }

    @Test
    void extendsConcreteClassIsUnaffected(@TempDir Path tempDir) throws IOException {
        // regression guard: the new detection must NOT touch the normal
        // class-extends-class path (super stays the class, not Object).
        Path src = tempDir.resolve("chain.kf");
        Files.writeString(src, """
                class Base {
                    String who() { return "base" }
                }
                class Sub extends Base {
                    String who() { return "sub:" + super.who() }
                }
                main() {
                    var s = new Sub()
                    println(s.who())
                }
                """);
        Path out = tempDir.resolve("chain-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed (class-extends-class): " + r.diagnostics().getDiagnostics());
        assertEquals("sub:base", runJvm(out));
    }
}
