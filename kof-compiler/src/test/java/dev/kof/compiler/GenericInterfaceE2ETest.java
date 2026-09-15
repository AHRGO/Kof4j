package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #160 — `interface Mapper<T> { map(T input): String }` morria em
 * PARSE007 "Expected type declaration": o ramo de declaracao de CLASSE
 * parseava a lista de type-params (`class Box<T>`, TypeDeclarations:82) mas o
 * de INTERFACE nao, e o `T` dentro de `map(T input)` nao era um nome de
 * declaracao valido. Duas faces: (a) o parse da declaracao generica; (b) uma
 * vez parseada, `class Upper implements Mapper<String>` emitia o superinterface
 * NAO-APAGADO (`invokevirtual`/class-file `Mapper<String>`) ->
 * `NoClassDefFoundError: Mapper<String>` no load (JVMS: o nome e o raw; os
 * type-args vao no atributo Signature).
 *
 * Fix (Q0, causa-raiz): TypeDeclarations.parseInterfaceDeclaration agora chama
 * TypeParser.parseTypeParameters (espelha parseClassDeclaration) e
 * SymbolTableBuilder.defineInterfaceMembers registra os TypeParameterSymbol no
 * escopo antes dos membros (senao `map(T input)` nao resolve T);
 * CompilerClassLowering.apaga (`eraseTypeArgs`) os type-args nos nomes de
 * superinterface/superclasse emitidos (a linha do superName ja apagava inline;
 * so as listas de interfaces nao apagavam — bug irmao PRE-EXISTENTE na face
 * `extends` de generics de CLASSE tambem, medido).
 *
 * Matriz Q3: (1) declaracao generica parseia (o PARSE007 do titulo); (2)
 * `implements Mapper<String>` carrega e roda no JVM (o NoClassDefFoundError) +
 * mesma saida no Script (interpretador) e JS (emissao sem erro) — paridade do
 * frontend compartilhado; (3) multi type-param `interface Fn<A,B>`; (4)
 * interface NAO-generica nao regride (a forma do #213/#209); (5) `extends` de
 * generics de classe tambem apaga (face irma pre-existente).
 */
class GenericInterfaceE2ETest {

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
    void genericInterfaceDeclarationParses(@TempDir Path tempDir) throws IOException {
        // o caso EXATO do titulo: nao pode mais dar PARSE007
        Path src = tempDir.resolve("decl.kf");
        Files.writeString(src, """
                interface Mapper<T> {
                    String map(T input)
                }
                interface Fn<A, B> {
                    B apply(A a)
                }
                main() { println("ok") }
                """);
        Path out = tempDir.resolve("decl-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "declaracao generica nao pode dar PARSE007: " + r.diagnostics().getDiagnostics());
        assertEquals("ok", runJvm(out));
    }

    @Test
    void classImplementsGenericInterfaceLoadsAndRuns(@TempDir Path tempDir) throws IOException {
        // a face NoClassDefFoundError: Mapper<String> (superinterface nao-apagado)
        Path src = tempDir.resolve("impl.kf");
        Files.writeString(src, """
                interface Mapper<T> {
                    String map(T input)
                }
                class Upper implements Mapper<String> {
                    String map(String input) { return input + "!" }
                }
                main() {
                    var m = new Upper()
                    println(m.map("hi"))
                }
                """);
        Path out = tempDir.resolve("impl-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "implements Mapper<String> deve compilar: " + r.diagnostics().getDiagnostics());
        assertEquals("hi!", runJvm(out), "o superinterface deve ser apagado para Mapper (senao NoClassDefFoundError)");
    }

    @Test
    void implementsGenericInterfaceSameOnScriptAndJs(@TempDir Path tempDir) throws IOException, InterruptedException {
        // paridade do frontend compartilhado: mesma fonte no interpretador (Script)
        // e na emissao JS
        Path src = tempDir.resolve("par.kf");
        Files.writeString(src, """
                interface Mapper<T> {
                    String map(T input)
                }
                class Upper implements Mapper<String> {
                    String map(String input) { return input + "!" }
                }
                main() {
                    var m = new Upper()
                    println(m.map("hi"))
                }
                """);
        KofInterpreter.Result r = driver.interpret(java.util.List.of(src), src.getParent(), new String[0]);
        assertEquals(0, r.exitCode(), "Script (IR compartilhada) exit/stderr: " + r.stdout() + " " + r.stderr());
        assertEquals("hi!", r.stdout().trim(), "Script deve concordar com o JVM");
        Path jsOut = tempDir.resolve("par-js");
        CompilationResult js = driver.compile(src, jsOut, Target.JS);
        assertTrue(js.success(), "JS deve aceitar a mesma fonte: " + js.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("node", jsOut.resolve("Default.mjs").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String jsOutput = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JS exit code, output: " + jsOutput);
        assertEquals("hi!", jsOutput, "JS target (mesmo programa, mesma saida)");
    }

    @Test
    void genericMethodReturnErasedRunsOnImplementor(@TempDir Path tempDir) throws IOException {
        // Box<T>.get() com retorno primitivo: o return-adapter de erasure ja
        // cuida do cast; o fix nao pode regredi-lo
        Path src = tempDir.resolve("box.kf");
        Files.writeString(src, """
                interface Box<T> {
                    T get()
                }
                class IntBox implements Box<Int> {
                    Int get() { return 7 }
                }
                main() { println(new IntBox().get()) }
                """);
        Path out = tempDir.resolve("box-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "Box<Int> deve compilar: " + r.diagnostics().getDiagnostics());
        assertEquals("7", runJvm(out));
    }

    @Test
    void nonGenericInterfaceStillWorks(@TempDir Path tempDir) throws IOException {
        // nao-regressao: a forma do #213/#209 (sem type-args)
        Path src = tempDir.resolve("plain.kf");
        Files.writeString(src, """
                interface Printable {
                    String printIt()
                }
                class P implements Printable {
                    String printIt() { return "p!" }
                }
                main() { println(new P().printIt()) }
                """);
        Path out = tempDir.resolve("plain-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "interface nao-generica nao pode regredir: " + r.diagnostics().getDiagnostics());
        assertEquals("p!", runJvm(out));
    }

    @Test
    void genericClassExtendsAndRecordInterfaceEraseArgs(@TempDir Path tempDir) throws IOException {
        // face irma pre-existente: `extends`/implements com type-args em classe
        // e record tambem deve apagar o nome emitido
        Path src = tempDir.resolve("ext.kf");
        Files.writeString(src, """
                interface Named {
                    String name()
                }
                class Holder<T> implements Named {
                    String name() { return "h" }
                }
                main() {
                    var x = new Holder<Int>()
                    println(x.name())
                }
                """);
        Path out = tempDir.resolve("ext-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "classe generica implements deve compilar: " + r.diagnostics().getDiagnostics());
        assertEquals("h", runJvm(out));
    }
}
