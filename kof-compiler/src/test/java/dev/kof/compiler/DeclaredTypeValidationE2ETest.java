package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §251 — a declared type that resolves to no known type was never validated:
 * in a return/param it compiled silently and the class was unloadable
 * (`NoClassDefFoundError`); in a field/record component it was silently
 * ignored. Now every declaration site is validated (`DeclaredTypeChecker`,
 * predicate `MemberResolver.declaredTypeUnresolved`), with the generic
 * type-variable whitelist (`class Box&lt;T&gt; { T value }`, `T id&lt;T&gt;(T x)`),
 * bare collections, `kof.ui`/`kof.media` (§179), module classes, imports and
 * external types all exempt.
 *
 * <p>Regression guard: the legitimate forms below MUST stay accepted — they are
 * the trap the catalog warned about (a naive check breaks generics).
 */
class DeclaredTypeValidationE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path dir, String source) throws Exception {
        Path file = dir.resolve("Main-" + System.nanoTime() + ".kf");
        java.nio.file.Files.writeString(file, source);
        return driver.compile(file, dir.resolve("out-" + System.nanoTime()), Target.JVM);
    }

    private boolean hasSem011(CompilationResult r, String typeName) {
        return r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "SEM011".equals(d.code()) && d.message().contains(typeName));
    }

    // ---- faces rejeitadas (return/param/field/component/method/ctor) ----

    @Test
    void undefinedReturnTypeRejected(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                Foo make() { throw "e" }
                main() { println("ok") }
                """);
        assertFalse(r.success(), "undefined return type must be rejected: " + r.diagnostics().getDiagnostics());
        assertTrue(hasSem011(r, "Foo"), "SEM011 naming Foo: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void undefinedParamTypeRejected(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                void use(Foo f) { }
                main() { println("ok") }
                """);
        assertFalse(r.success(), "undefined param type must be rejected: " + r.diagnostics().getDiagnostics());
        assertTrue(hasSem011(r, "Foo"), "SEM011 naming Foo: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void undefinedFieldTypeRejected(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                class C { Foo f }
                main() { println("ok") }
                """);
        assertFalse(r.success(), "undefined field type must be rejected: " + r.diagnostics().getDiagnostics());
        assertTrue(hasSem011(r, "Foo"), "SEM011 naming Foo: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void undefinedRecordComponentRejected(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                record R(Foo f)
                main() { println("ok") }
                """);
        assertFalse(r.success(), "undefined record component must be rejected: " + r.diagnostics().getDiagnostics());
        assertTrue(hasSem011(r, "Foo"), "SEM011 naming Foo: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void undefinedMethodReturnRejected(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                class C { Foo get() { throw "e" } }
                main() { println("ok") }
                """);
        assertFalse(r.success(), "undefined method return must be rejected: " + r.diagnostics().getDiagnostics());
        assertTrue(hasSem011(r, "Foo"), "SEM011 naming Foo: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void undefinedConstructorParamRejected(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                class C { constructor(Foo f) { } }
                main() { println("ok") }
                """);
        assertFalse(r.success(), "undefined ctor param must be rejected: " + r.diagnostics().getDiagnostics());
        assertTrue(hasSem011(r, "Foo"), "SEM011 naming Foo: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void undefinedNestedTypeArgRejected(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                class C { List<Foo> xs }
                main() { println("ok") }
                """);
        assertFalse(r.success(), "undefined type-arg must be rejected: " + r.diagnostics().getDiagnostics());
        assertTrue(hasSem011(r, "Foo"), "SEM011 naming Foo: " + r.diagnostics().getDiagnostics());
    }

    // ---- formas legítimas (a armadilha do catálogo: genéricos) ----

    @Test
    void genericClassTypeVariableAccepted(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                class Box<T> { T value
                    T get() { return value }
                    void set(T v) { this.value = v }
                }
                main() { var b = new Box<Int>(); b.set(1); println(b.get()) }
                """);
        assertTrue(r.success(), "generic T field/return/param must compile: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void genericFunctionTypeVariableAccepted(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                T id<T>(T x) { return x }
                main() { println(id(5)) }
                """);
        assertTrue(r.success(), "generic function type-var must compile: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void multiTypeParamNestedGenericAccepted(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                class Pair<K, V> { K k; V v
                    Map<K, V> asMap() { return mapOf() }
                }
                main() { println("ok") }
                """);
        assertTrue(r.success(), "K/V + nested generic must compile: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void genericInterfaceTypeVariableAccepted(@TempDir Path tmp) throws Exception {
        // Q4 regression guard: a generic INTERFACE's type params (T) are valid
        // declared types in its methods — the §160 lane added typeParameters to
        // InterfaceDeclarationNode; the checker must whitelist them.
        CompilationResult r = compile(tmp, """
                interface Mapper<T> { T map(T input) }
                class Identity implements Mapper<String> {
                    String map(String input) { return input }
                }
                main() { println("ok") }
                """);
        assertTrue(r.success(), "generic interface type-param must compile: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void genericInterfaceMultiParamAccepted(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                interface Fn<A, B> { B apply(A a) }
                main() { println("ok") }
                """);
        assertTrue(r.success(), "generic interface A/B must compile: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void moduleClassAndInterfaceTypesAccepted(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                interface Shape { Int area() }
                class Sq extends Shape { Int s
                    Int area() { return s * s }
                }
                class Holder { Shape shape; Sq sq }
                main() { println("ok") }
                """);
        assertTrue(r.success(), "module class/interface types must compile: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void bareAndNestedCollectionTypesAccepted(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                class C { List<Int> xs; Map<String, Int> m; Set<String> s; List y }
                main() { println("ok") }
                """);
        assertTrue(r.success(), "collection declared types must compile: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void nullableAndArrayOfModuleTypeAccepted(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                class Node<T> { Node<T>? next; T v; Int[] data }
                main() { println("ok") }
                """);
        assertTrue(r.success(), "nullable/array of module type must compile: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void jdkObjectAndStringAccepted(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                class C { Object o; String s }
                main() { println("ok") }
                """);
        assertTrue(r.success(), "Object/String must compile: " + r.diagnostics().getDiagnostics());
    }
}
