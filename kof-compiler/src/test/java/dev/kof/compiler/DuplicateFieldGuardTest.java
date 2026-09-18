package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #294 — `static Int n` + `Int n` in one class passed every check and the
 * backend emitted TWO fields named "n" with the same descriptor: the class
 * died at LOAD with `ClassFormatError: Duplicate field name "n" with
 * signature "I"` (R6: silent broken output). The guard now rejects it at
 * compile time (SEM076, positional, shared across the 4 targets), mirroring
 * the #264 SEM061 school for methods: the JVM FIELD namespace is
 * (name, descriptor) — staticness does NOT separate it, different
 * descriptors DO coexist (control below).
 */
class DuplicateFieldGuardTest {

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return new CompilerDriver().compile(source, tempDir.resolve("out-" + name + t), t);
    }

    @Test
    void staticInstanceSameNameSameDescriptorIsSEM076(@TempDir Path tempDir) throws Exception {
        // #294 verbatim — pre-guard this compiled and died at load
        // (ClassFormatError "n" "I"); now it fails LOUDLY, with position.
        CompilationResult r = compile(tempDir, "A", """
                class Dup {
                    static Int n = 1
                    Int n = 2
                }
                main() { val d = Dup(); println(d.n); println(Dup.n) }
                """, Target.JVM);
        assertFalse(r.success(), "#294 verbatim must be rejected");
        var ds = r.diagnostics().getDiagnostics();
        assertTrue(ds.stream().anyMatch(d -> d.code().equals("SEM076")),
                "must carry SEM076: " + ds);
        assertTrue(ds.stream().anyMatch(d -> d.code().equals("SEM076") && d.line() > 0),
                "diagnostic must be positional, not :0:0: " + ds);
    }

    @Test
    void twoStaticsSameNameAlsoRejected(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "B", """
                class Two {
                    static Int k = 1
                    static Long other = 0
                    static Int k = 2
                }
                main() { println(Two.k) }
                """, Target.JVM);
        assertFalse(r.success(), "same-name same-descriptor statics are a JVM duplicate field: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void sameNameEvenDifferentDescriptorRejectedAsGarbageFace(@TempDir Path tempDir) throws Exception {
        // §289 CLOSED BY THIS GUARD (measured 18/09): the "JVM-legal" pair
        // `static Int a` + `Long a` produced GARBAGE ON ALL FOUR TARGETS —
        // JVM VerifyError at load, JS ICE `unknown local slot 0`, script
        // printed the CLASS TYPE toString, native returned a pointer-like
        // number (3530822633505786184) for Ok.a. Nothing worked anywhere, so
        // the javac-style rule (ONE field per NAME, any type) forbids the
        // shape loudly instead of shipping four broken emissions — and dies
        // with the resolver clobber it existed to paper over.
        CompilationResult r = compile(tempDir, "C", """
                class Ok {
                    static Int a = 1
                    Long a = 2
                }
                main() { println(Ok.a) }
                """, Target.JVM);
        assertFalse(r.success(), "same-name field pair must be rejected (garbage on all targets)");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM076") && d.line() > 0),
                "SEM076 positional: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void hierarchyShadowingUntouched(@TempDir Path tempDir) throws Exception {
        // control: Base.v + Child.v (different classes) is legal shadowing —
        // the guard runs per member list, never across the hierarchy.
        CompilationResult r = compile(tempDir, "D", """
                class Base { Int v = 1 }
                class Child extends Base { String v = "shadow" }
                main() { val c = Child(); println(c.v) }
                """, Target.JVM);
        assertTrue(r.success(), "cross-class shadowing must compile: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void guardDiagnosticSharedAcrossAllTargets(@TempDir Path tempDir) throws Exception {
        // SEM code comes from the shared analyzer — all 4 backends reject
        // identically (the verbatim repro of #294, four targets).
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, "E" + t, """
                    class Dup {
                        static Int n = 1
                        Int n = 2
                    }
                    main() { val d = Dup(); println(d.n); println(Dup.n) }
                    """, t);
            assertFalse(r.success(), t + " must reject the duplicate field");
            assertTrue(r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> d.code().equals("SEM076")), t + ": " + r.diagnostics().getDiagnostics());
        }
    }
}
