package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof translate — Java subset → Kof (docs/development/TRANSLATOR.md, Fase F).
 * Static main becomes a top-level main(); println/equals map to idiomatic Kof.
 */
class TranslateTest {

    @Test
    void helloWorldMainBecomesTopLevelMain(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Hello");
                    }
                }
                """);

        assertTrue(kof.contains("main()"), "should emit top-level main():\n" + kof);
        assertTrue(kof.contains("println(\"Hello\")"), "should map println:\n" + kof);
        assertFalse(kof.contains("System.out"), "must drop System.out:\n" + kof);

        assertCompiles(dir, kof, "Hello");
    }

    @Test
    void methodsAndFieldsTranslate(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class Calc {
                    int total = 0;
                    public int add(int a, int b) {
                        return a + b;
                    }
                    public boolean isPositive(int x) {
                        return x > 0;
                    }
                }
                """);

        assertTrue(kof.contains("class Calc"), "should emit class Calc:\n" + kof);
        assertTrue(kof.contains("Int total"), "field should become typed field:\n" + kof);
        assertTrue(kof.contains("Int add"), "method return type should be Int:\n" + kof);
        assertTrue(kof.contains("Int add(Int a, Int b) = a + b"), "single-return method becomes expression body:\n" + kof);
        assertTrue(kof.contains("Bool isPositive"), "boolean → Bool:\n" + kof);
        assertFalse(kof.contains("public"), "must drop public modifier:\n" + kof);

        assertCompiles(dir, kof, null);
    }

    @Test
    void equalsAndControlFlowTranslate(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class Flow {
                    public static void main(String[] args) {
                        String s = "hello";
                        if (s.equals("hello")) {
                            System.out.println("match");
                        } else {
                            System.out.println("no");
                        }
                        int i = 0;
                        while (i < 3) {
                            i = i + 1;
                        }
                        System.out.println(i);
                    }
                }
                """);

        assertTrue(kof.contains("s == \"hello\""), "equals should become ==:\n" + kof);
        assertTrue(kof.contains("while (i < 3)"), "while loop preserved:\n" + kof);
        assertTrue(kof.contains("if ("), "if preserved:\n" + kof);

        assertCompiles(dir, kof, "match\n3");
    }

    @Test
    void recordTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public record Point(int x, int y) {
                }
                """);

        assertTrue(kof.contains("record Point(Int x, Int y)"), "record Java vira record Kof:\n" + kof);
        assertFalse(kof.contains("class Point"), "não deve virar class:\n" + kof);

        assertCompiles(dir, kof, null);
    }

    @Test
    void enumTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public enum Color { RED, GREEN, BLUE }
                """);

        assertTrue(kof.contains("enum Color"), "enum Java vira enum Kof:\n" + kof);
        assertTrue(kof.contains("RED"), "constante RED:\n" + kof);
        assertTrue(kof.contains("GREEN"), "constante GREEN:\n" + kof);
        assertTrue(kof.contains("BLUE"), "constante BLUE:\n" + kof);

        assertCompiles(dir, kof, null);
    }

    @Test
    void ternaryTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class T {
                    public static int max(int a, int b) { return a > b ? a : b; }
                }
                """);

        assertTrue(kof.contains("if (a > b) a else b"),
                "ternário deve virar if-expression:\n" + kof);
    }

    @Test
    void stringLengthProperty(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class S {
                    public static int size(String s) { return s.length(); }
                }
                """);

        assertTrue(kof.contains("s.length"), "length() vira propriedade .length:\n" + kof);
        assertFalse(kof.contains("s.length()"), "não deve manter length():\n" + kof);
    }

    @Test
    void lambdaTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                interface F { int run(int n); }
                class L {
                    static int go(int n) { F f = (int x) -> x + 1; return f.run(n); }
                }
                """);

        assertTrue(kof.contains("(x: Int) -> x + 1"), "lambda tipada deve traduzir:\n" + kof);
    }

    @Test
    void boxedTypesMapToPrimitives(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                import java.util.List;
                public class G {
                    List<Integer> integers;
                    public Integer get(List<Boolean> flags) { return integers.get(0); }
                }
                """);

        assertTrue(kof.contains("List<Int> integers"), "Integer vira Int:\n" + kof);
        assertTrue(kof.contains("Int get"), "Integer retorno vira Int:\n" + kof);
        assertTrue(kof.contains("List<Bool>"), "Boolean vira Bool:\n" + kof);
    }

    @Test
    void switchStatementTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class SW {
                    public static void main(String[] args) {
                        int x = 1;
                        switch (x) {
                            case 1:
                                System.out.println("one");
                                break;
                            case 2:
                                System.out.println("two");
                                break;
                            default:
                                System.out.println("other");
                                break;
                        }
                        String s = "b";
                        switch (s) {
                            case "a", "b":
                                System.out.println("ab");
                                break;
                            default:
                                System.out.println("zz");
                                break;
                        }
                    }
                }
                """);

        assertTrue(kof.contains("switch (x) { case 1: println(\"one\") case 2: println(\"two\") default: println(\"other\") }"),
                "switch Java deve virar switch Kof statement (antes: expected ';' but found '('):\n" + kof);
        assertFalse(kof.contains("break"), "break Java é opcional em Kof (sem fallthrough) — dropar:\n" + kof);
        assertTrue(kof.contains("case \"a\": case \"b\": println(\"ab\")"),
                "labels múltiplos `case \"a\", \"b\":` viram cases separados:\n" + kof);

        assertCompiles(dir, kof, "one\nab");
    }

    @Test
    void tryCatchFinallyTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class ER {
                    static void boom(String k) {
                        throw new RuntimeException("boom " + k);
                    }
                    public static void main(String[] args) {
                        try {
                            boom("x");
                            System.out.println("no");
                        } catch (RuntimeException e) {
                            System.out.println("caught");
                        } finally {
                            System.out.println("done");
                        }
                        try {
                            System.out.println("t2");
                        } catch (IllegalStateException | IllegalArgumentException e) {
                            System.out.println("multi");
                        }
                    }
                }
                """);

        assertTrue(kof.contains("try { boom(\"x\") println(\"no\") } catch (String e) { println(\"caught\") } finally { println(\"done\") }"),
                "try/catch/finally Java deve virar try/catch(String)/finally Kof (antes: expected ';' but found '{'):\n" + kof);
        assertTrue(kof.contains("throw \"boom \" + k"),
                "`throw new RuntimeException(msg)` deve virar `throw msg` (exceções são Strings, errors.md; antes: found 'new'):\n" + kof);
        assertTrue(kof.contains("} catch (String e) { println(\"multi\") }"),
                "multi-catch `catch (A | B e)` vira catch único (Kof sem união):\n" + kof);
        assertFalse(kof.contains("RuntimeException("), "sem construtor de exceção no output:\n" + kof);

        assertCompiles(dir, kof, "caught\ndone\nt2");
    }

    @Test
    void constructorTranslatesWithBody(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class User {
                    String name;
                    int age;
                    public User(String n, int a) {
                        this.name = n;
                        this.age = a;
                    }
                    public String greet() { return "Hello " + name; }
                    public static void main(String[] args) {
                        User u = new User("Mel", 26);
                        System.out.println(u.greet());
                        System.out.println(u.age);
                    }
                }
                """);

        assertTrue(kof.contains("constructor(String n, Int a) {"),
                "construtor Java deve virar constructor Kof (antes: loop infinito/parse error):\n" + kof);
        assertTrue(kof.contains("this.name = n"),
                "corpo do construtor NÃO pode ser descartado (era bug latente `{}`):\n" + kof);
        assertTrue(kof.contains("var u = User(\"Mel\", 26)"), "new X(...) → X(...):\n" + kof);

        assertCompiles(dir, kof, "Hello Mel\n26");
    }

    @Test
    void genericsTranslate(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                class Box<T> {
                    T value;
                    public Box(T v) { this.value = v; }
                    public T get() { return value; }
                }
                class GM {
                    static <T> T id(T x) { return x; }
                    public static void main(String[] args) {
                        Box<Integer> b = new Box<Integer>(5);
                        System.out.println(b.get());
                        System.out.println(id(7));
                    }
                }
                """);

        assertTrue(kof.contains("class Box<T>"), "classe genérica Java → Kof (antes: expected class/... found 'T'):\n" + kof);
        assertTrue(kof.contains("T id<T>(T x)"), "método genérico `<T> T id` → `T id<T>`:\n" + kof);
        assertTrue(kof.contains("var b = Box(5)"),
                "`new Box<Integer>(5)` → `Box(5)` (Kof infere o tipo):\n" + kof);

        assertCompiles(dir, kof, "5\n7");
    }

    @Test
    void varargsAndNestedTypeAreHonestGaps() {
        TranslateException varargs = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class V {
                            static int sum(int... xs) { return xs.length; }
                        }
                        """));
        assertTrue(varargs.getMessage().contains("varargs") && varargs.getMessage().contains("revisão manual"),
                "varargs `T...` sem equivalente Kof → gap explícito (R6), foi: " + varargs.getMessage());

        TranslateException nested = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class N {
                            static class Inner { int x = 1; }
                        }
                        """));
        assertTrue(nested.getMessage().contains("tipo aninhado") && nested.getMessage().contains("revisão manual"),
                "tipo aninhado sem equivalente Kof (SEM042) → gap explícito (R6), foi: " + nested.getMessage());

        TranslateException twr = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class T {
                            static void f() {
                                try (java.io.StringReader r = new java.io.StringReader("x")) {
                                    System.out.println(r.read());
                                }
                            }
                        }
                        """));
        assertTrue(twr.getMessage().contains("try-with-resources") && twr.getMessage().contains("revisão manual"),
                "try-with-resources sem equivalente Kof → gap explícito (R6), foi: " + twr.getMessage());

        TranslateException fqn = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class Q {
                            static Object make() { return new java.util.ArrayList<String>(); }
                        }
                        """));
        assertTrue(fqn.getMessage().contains("tipo qualificado") && fqn.getMessage().contains("revisão manual"),
                "tipo qualificado não resolvido → gap explícito (R6), foi: " + fqn.getMessage());
    }

    @Test
    void throwsClauseIsDropped(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class TH {
                    static void fail() throws java.io.IOException {
                        throw new RuntimeException("boom");
                    }
                    public static void main(String[] args) {
                        try {
                            fail();
                        } catch (RuntimeException e) {
                            System.out.println("caught");
                        }
                    }
                }
                """);

        assertTrue(kof.contains("void fail() {"),
                "`throws IOException` deve ser descartado (Kof não declara throws; antes: expected '{' but found 'throws'):\n" + kof);
        assertFalse(kof.contains("throws"), "sem cláusula throws no output:\n" + kof);
        assertTrue(kof.contains("throw \"boom\""), "throw new Exc(msg) → throw msg:\n" + kof);

        assertCompiles(dir, kof, "caught");
    }

    @Test
    void castAndInstanceofTranslate(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class CI {
                    public static void main(String[] args) {
                        Object o = "x";
                        String s = (String) o;
                        System.out.println(s);
                        if (o instanceof String) {
                            System.out.println("is-str");
                        }
                        double d = 3.5;
                        int n = (int) d;
                        System.out.println(n);
                    }
                }
                """);

        assertTrue(kof.contains("var s = o as String"),
                "`(String) o` deve virar `o as String` (antes: expected ';' but found 'o'):\n" + kof);
        assertTrue(kof.contains("o instanceof String"),
                "instanceof preservado (Kof tem nativo; antes: expected ')' but found 'instanceof'):\n" + kof);
        assertTrue(kof.contains("var n = d as Int"),
                "cast primitivo `(int) d` → `d as Int`:\n" + kof);

        assertCompiles(dir, kof, "x\nis-str\n3");
    }

    @Test
    void arrayDeclarationTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class AR {
                    public static void main(String[] args) {
                        int[] xs = new int[3];
                        xs[0] = 10;
                        for (int i = 0; i < xs.length; i++) {
                            System.out.println(xs[i]);
                        }
                    }
                }
                """);

        assertTrue(kof.contains("var xs = new Int[3]"),
                "new int[3] deve virar new Int[3] (bug latente: saía `new Int[]]`, PARSE041):\n" + kof);
        assertFalse(kof.contains("[]]"), "não pode sobrar `[]]` do size consumido errado:\n" + kof);
        assertTrue(kof.contains("for (var i = 0; i < xs.length; i++)"),
                "for C-style preservado (Kof suporta):\n" + kof);

        assertCompiles(dir, kof, "10\n0\n0");
    }

    @Test
    void arrayInitializerIsHonestGap() {
        TranslateException e = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class AI {
                            public static void main(String[] args) {
                                int[] xs = {1, 2, 3};
                                System.out.println(xs[0]);
                            }
                        }
                        """));
        assertTrue(e.getMessage().contains("revisão manual"),
                "array initializer `{...}` sem equivalente Kof → gap explícito (R6), foi: " + e.getMessage());
    }

    @Test
    void doWhileTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class DW {
                    public static void main(String[] args) {
                        int i = 0;
                        do {
                            System.out.println(i);
                            i = i + 1;
                        } while (i < 3);
                    }
                }
                """);

        assertTrue(kof.contains("do { println(i) i = i + 1 } while (i < 3)"),
                "do-while Java deve virar do-while Kof (antes: expected ';' but found '{'):\n" + kof);
        assertFalse(kof.contains("do { {"), "sem chaves duplas:\n" + kof);

        assertCompiles(dir, kof, "0\n1\n2");
    }

    private void assertCompiles(Path dir, String kof, String expected) throws Exception {
        Path src = dir.resolve("T.kf");
        Files.writeString(src, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(src, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "translated Kof must compile:\n" + kof + "\n" + result.diagnostics().getDiagnostics());

        if (expected != null) {
            ProcessBuilder pb = new ProcessBuilder(
                    javaHomeJava(), "-cp", dir.resolve("out").toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, proc.waitFor(), "run exit code\n" + output);
            assertEquals(expected.trim(), output.trim(), "runtime output mismatch");
        }
    }

    private static String javaHomeJava() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }
}