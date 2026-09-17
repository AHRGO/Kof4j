package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #400 — `class IntToString implements Converter<Int, String>` then
 * `val cv: Converter<Int, String> = c` was rejected with SEM021 even though
 * the assignment is statically valid. Root cause: `parseTypeRef` stores the
 * implements entry WITH the type arguments ("Converter<Int, String>") while
 * the nominal BFS (SG-009) compared it against the bare name "Converter" —
 * the interface was never found. Fix: the BFS erases the declaration-site
 * `<...>` before comparing (same erasure face the JDK uses; precedent
 * MemberResolver:356). The fine-grained type-ARGUMENT check between distinct
 * instantiations is SG-013/#401's own queue — this unit only turns the false
 * rejection into the correct acceptance.
 */
class GenericInterfaceAssignabilityTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return driver.compile(source, tempDir.resolve("out-" + name + t), t);
    }

    private static final String VERBATIM = """
            interface Converter<A, B> {
                convert(input: A): B
            }
            class IntToString implements Converter<Int, String> {
                convert(input: Int): String { return input.toString() }
            }
            main() {
                val c: IntToString = IntToString()
                println(c.convert(42))
                val cv: Converter<Int, String> = c
                println(cv.convert(99))
            }
            """;

    @Test
    void verbatimAssignToGenericInterfaceCompiles(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "V", VERBATIM, Target.JVM);
        assertTrue(r.success(), "#400 verbatim: valid implements must assign (old code: SEM021 false positive): "
                + r.diagnostics().getDiagnostics());
        // SCOPED HONESTLY: the runtime dispatch through `cv.convert(99)` still
        // dies with NoSuchMethodError (Converter.convert(Object) bridge never
        // emitted) — measured 17/09: `42` then `NoSuchMethodError:
        // 'java.lang.Object Converter.convert(java.lang.Object)'`. That is
        // §271 / Cluster A (erasure+bridge modeling in the 4 emitters, rule 6),
        // NOT this unit. Do not assert run until §271 lands — asserting the
        // crash would document the bug as expected (freeze rule 4).
    }

    @Test
    void inheritedGenericInterfaceThroughSuperclass(@TempDir Path tempDir) throws Exception {
        // BFS leg 2: Sub has no direct implements — the ANCESTOR's generic
        // entry must still resolve (superClass + interfaces both carry <...>).
        CompilationResult r = compile(tempDir, "H", """
                interface Runner<T> {
                    run(item: T): Int
                }
                class Base implements Runner<Int> {
                    run(item: Int): Int { return item + 1 }
                }
                class Sub extends Base {
                }
                main() {
                    var s = Sub()
                    var rv: Runner<Int> = s
                    println(rv.run(41))
                }
                """, Target.JVM);
        assertTrue(r.success(), "implements inherited through superclass must assign: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void unrelatedClassStillRejectedSem021(@TempDir Path tempDir) throws Exception {
        // control (SG-009 must NOT loosen): no implements anywhere.
        CompilationResult r = compile(tempDir, "N", """
                interface Runner<T> {
                    run(item: T): Int
                }
                class Unrelated {
                    run(item: Int): Int { return item }
                }
                main() {
                    var u = Unrelated()
                    var rv: Runner<Int> = u
                    println(rv.run(1))
                }
                """, Target.JVM);
        assertFalse(r.success(), "a class that never implements the interface stays rejected");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("SEM021"),
                "must still be SEM021: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void nonGenericImplementsPathUnchanged(@TempDir Path tempDir) throws Exception {
        // control (freeze rule 2): the plain (no type args) nominal path.
        CompilationResult r = compile(tempDir, "P", """
                interface Speak {
                    speak(): String
                }
                class Dog implements Speak {
                    speak(): String { return "woof" }
                }
                main() {
                    var d = Dog()
                    var s: Speak = d
                    println(s.speak())
                }
                """, Target.JVM);
        assertTrue(r.success(), "plain interface implements keeps working: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void acceptanceIsUniversalAcrossTargets(@TempDir Path tempDir) throws Exception {
        // rule 5: the fix is in the shared semantic pass — the verdict gate is
        // one for all targets (NATIVE positive codegen rides the full suite;
        // precedent ReduceSeedArityTest only pins negatives across the three).
        for (Target t : new Target[]{Target.JVM, Target.JS}) {
            CompilationResult r = compile(tempDir, "U", VERBATIM, t);
            assertTrue(r.success(), t + ": #400 verbatim must compile on every target: "
                    + r.diagnostics().getDiagnostics());
        }
    }
}
