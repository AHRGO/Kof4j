package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #213 — interface default method body silently dropped: compiled as
 * `abstract` (no Code attribute) and SEM043 false-positive on the implementing
 * class that relies on the inherited default.
 *
 * Root cause (now fixed):
 *  1. CompilerClassLowering.lowerMethodInner flagged every interface method
 *     ABSTRACT, dropping the body's Code attribute.
 *  2. SymbolTableBuilder.defineInterfaceMembers gave the MethodSymbol flag 0
 *     (no scope/params for the body) → the unqualified call `greet(name)` was
 *     resolved as a hoisted function (invokestatic Default/Main.greet) and the
 *     String receiver was untyped (empty owner) → IncompatibleClassChangeError /
 *     ClassFormatError at runtime.
 *  3. SemanticAnalyzer.checkInterfaceImplementation demanded the default method
 *     from every implementor (SEM043 false positive).
 *
 * Proof (JVM bytecode, javap -p Greeter.class):
 *   public default java.lang.String greetLoud(java.lang.String);
 *     aload_0; aload_1; invokeinterface Greeter.greet; invokevirtual toUpperCase
 */
class InterfaceDefaultMethodE2ETest {

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
    void happyPathDefaultMethodDispatchOnImplementor(@TempDir Path tempDir) throws IOException {
        // happy path: implementor inherits the default; the unqualified call
        // inside the default resolves to the interface receiver (invokeinterface),
        // and the String receiver is typed (javap: java/lang/String.toUpperCase).
        Path src = tempDir.resolve("def.kf");
        Files.writeString(src, """
                interface Greeter {
                    String greet(String name)
                    String greetLoud(String name) { return greet(name).toUpperCase() }
                }
                class SimpleGreeter implements Greeter {
                    String greet(String name) { return "Hello " + name }
                }
                main() {
                    var g = new SimpleGreeter()
                    println(g.greetLoud("Alice"))
                    println(g.greet("Bob"))
                }
                """);
        Path out = tempDir.resolve("def-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed (#213 default): " + r.diagnostics().getDiagnostics());
        assertEquals("HELLO ALICE\nHello Bob", runJvm(out));
    }

    @Test
    void defaultMethodOverriddenInImplementorWins(@TempDir Path tempDir) throws IOException {
        // edge: implementor that DOES override the default wins (virtual
        // dispatch must prefer the concrete override over the inherited default).
        Path src = tempDir.resolve("over.kf");
        Files.writeString(src, """
                interface Greeter {
                    String greet(String name)
                    String greetLoud(String name) { return greet(name).toUpperCase() }
                }
                class LoudGreeter implements Greeter {
                    String greet(String name) { return "hi " + name }
                    String greetLoud(String name) { return greet(name) + "!!" }
                }
                main() {
                    var g = new LoudGreeter()
                    println(g.greetLoud("x"))
                }
                """);
        Path out = tempDir.resolve("over-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed (#213 override): " + r.diagnostics().getDiagnostics());
        assertEquals("hi x!!", runJvm(out));
    }

    @Test
    void defaultMethodOnSecondInterfaceNoFalseSem043(@TempDir Path tempDir) throws IOException {
        // self-check / multi-interface: default on the SECOND interface must
        // not trigger SEM043 (issue's "additional variant").
        Path src = tempDir.resolve("two.kf");
        Files.writeString(src, """
                interface A {
                    Int compute()
                    String describe() { return "result=" + compute() }
                }
                interface B { void run() }
                class Impl implements A, B {
                    Int compute() { return 42 }
                    void run() { println(describe()) }
                }
                main() {
                    var i = new Impl()
                    i.run()
                }
                """);
        Path out = tempDir.resolve("two-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed (#213 two-iface): " + r.diagnostics().getDiagnostics());
        assertEquals("result=42", runJvm(out));
    }

    @Test
    void defaultMethodReturningPrimitiveUsedInArithmetic(@TempDir Path tempDir) throws IOException {
        // numeric edge: default returning Int used in arithmetic — the receiver
        // typing of the default body must not leave an unbox/checkcast hole.
        Path src = tempDir.resolve("arith.kf");
        Files.writeString(src, """
                interface Counter {
                    Int base()
                    Int doubled() { return base() * 2 }
                }
                class MyCounter implements Counter {
                    Int base() { return 5 }
                }
                main() {
                    var c = new MyCounter()
                    println(c.doubled())
                    println(c.doubled() + 1)
                }
                """);
        Path out = tempDir.resolve("arith-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed (#213 primitive default): " + r.diagnostics().getDiagnostics());
        assertEquals("10\n11", runJvm(out));
    }

    @Test
    void staticInterfaceMethodStillCompilesAndRuns(@TempDir Path tempDir) throws IOException {
        // regression guard: static interface method (non-default, #230) must
        // keep working — the new abstract-only-when-no-body logic must not
        // break the STATIC/INTERFACE handling.
        Path src = tempDir.resolve("stat.kf");
        Files.writeString(src, """
                interface Util {
                    static String shout(String s) { return s.toUpperCase() }
                    String greet(String n)
                }
                class UImpl implements Util {
                    String greet(String n) { return "hey " + n }
                }
                main() {
                    println(Util.shout("zv"))
                    var u = new UImpl()
                    println(u.greet("mel"))
                }
                """);
        Path out = tempDir.resolve("stat-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed (#213 static iface): " + r.diagnostics().getDiagnostics());
        assertEquals("ZV\nhey mel", runJvm(out));
    }
}
