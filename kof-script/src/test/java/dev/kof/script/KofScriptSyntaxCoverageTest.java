package dev.kof.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura sintaxe Kof × modo script (KofScript): "mesma sintaxe, só muda
 * o target" (ordem da mantenedora 18/09). Probes das construções do corpus
 * que os testes existentes (collections, records, spawn, null-safety,
 * closures, channels) NÃO exercitavam no interpretador.
 */
class KofScriptSyntaxCoverageTest {

    @Test
    void switchExpressionWithArrow() throws Exception {
        var r = KofScript.eval("""
                var op = "GetSession"
                var kind = switch (op) {
                    case "GetSession" -> "session"
                    case "GetAccess" -> "access"
                    default -> "other"
                }
                println(kind)
                """);
        assertTrue(r.success(), "switch-expr stderr: " + r.stderr());
        assertEquals("session", r.stdout().trim());
    }

    @Test
    void switchCaseDestructuringOnRecord() throws Exception {
        var r = KofScript.eval("""
                record Point(Int x, Int y)
                var p = Point(3, 4)
                var d = switch (p) {
                    case Point(var x, var y) -> x + "," + y
                    default -> "no"
                }
                println(d)
                """);
        assertTrue(r.success(), "case destructuring stderr: " + r.stderr());
        assertEquals("3,4", r.stdout().trim());
    }

    @Test
    void topLevelOverload() throws Exception {
        var r = KofScript.eval("""
                Int g(Int x) { return x * 2 }
                Int g(Int x, Int y) { return x + y }
                println(g(5))
                println(g(5, 7))
                """);
        assertTrue(r.success(), "top-level overload stderr: " + r.stderr());
        assertEquals("10\n12", r.stdout().trim());
    }

    @Test
    void trailingReturnTypeAndExpressionBody() throws Exception {
        var r = KofScript.eval("""
                despedida(): String { return "tchau" }
                Bool positivo(Int x) = x > 0
                println(despedida())
                println(positivo(3))
                println(positivo(-1))
                """);
        assertTrue(r.success(), "trailing/expr-body stderr: " + r.stderr());
        assertEquals("tchau\ntrue\nfalse", r.stdout().trim());
    }

    @Test
    void mutableClassWithConstructorAndThis() throws Exception {
        var r = KofScript.eval("""
                class User {
                    String name
                    Int age
                    public constructor(String name, Int age) {
                        this.name = name
                        this.age = age
                    }
                    String greeting() { return "Hello " + name }
                }
                var u = User("Mel", 26)
                u.age = 27
                println(u.greeting())
                println(u.age)
                """);
        assertTrue(r.success(), "mutable class stderr: " + r.stderr());
        assertEquals("Hello Mel\n27", r.stdout().trim());
    }

    @Test
    void whileAndForInAndHigherOrder() throws Exception {
        var r = KofScript.eval("""
                var items = listOf(1, 2, 3, 4)
                var doubled = items.map((n: Int) -> n * 2)
                var evens = items.filter((n: Int) -> n % 2 == 0)
                var total = items.reduce((a: Int, b: Int) -> a + b, 0)
                var i = 0
                while (i < 2) { i = i + 1 }
                for (var d in doubled) { println(d) }
                println(evens.size + "/" + total + "/" + i)
                """);
        assertTrue(r.success(), "while/for-in/HOF stderr: " + r.stderr());
        assertEquals("2\n4\n6\n8\n2/10/2", r.stdout().trim());
    }

    @Test
    void tryCatchThrowStringAndNestedFn() throws Exception {
        var r = KofScript.eval("""
                String find(String key) {
                    if (key == "ok") { return "sim" }
                    return null
                }
                void failha() {
                    try {
                        throw "not found: " + "zz"
                    } catch (String e) {
                        println("caught: " + e)
                    } finally {
                        println("cleanup")
                    }
                }
                failha()
                var v: String? = find("ok")
                if (v != null) { println(v) }
                """);
        assertTrue(r.success(), "try/catch + nested stderr: " + r.stderr());
        assertEquals("caught: not found: zz\ncleanup\nsim", r.stdout().trim());
    }

    @Test
    void globalCollectionsInferCorrectFieldTypes() throws Exception {
        var r = KofScript.eval("""
                record Point(Int x, Int y)
                var items = listOf(1, 2, 3, 4)
                var doubled = items.map((n: Int) -> n * 2)
                var s = setOf("a", "b")
                var m = mapOf("x", 1)
                var p = Point(10, 20)
                println(doubled.size)
                println(s.contains("a"))
                println(m.get("x"))
                println(p)
                """);
        assertEquals(0, r.exitCode(), "globais listOf/setOf/mapOf/Point stderr: " + r.stderr());
        assertEquals("4\ntrue\n1\nPoint[x=10, y=20]", r.stdout().trim());
    }

    @Test
    void recordToStringMatchesJvmFormat() throws Exception {
        var r = KofScript.eval("""
                record Point(Int x, Int y)
                println(Point(10, 20))
                """);
        assertTrue(r.success(), "record toString stderr: " + r.stderr());
        assertTrue(r.stdout().trim().startsWith("Point["),
                "record toString esperado estilo JVM, obtido: " + r.stdout().trim());
    }
}
