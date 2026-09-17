package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §193 (face crua do db.query, R6): a linha do `db.query` NÃO-tipado é String
 * JSON desde DB001; chamadas de acessor de coleção nela (`.get("col")`,
 * `.size`…) eram ACEITAS em compile-time e quebravam de um jeito em CADA
 * target (JVM `NoSuchMethodError: String.get`, JS roda `.get` inexistente →
 * undefined silencioso, Nativo `undefined reference` no link). Meta do
 * registro: SEM064 em compile-time apontando o caminho canônico. Frontend
 * único (`ExpressionInstanceCallLowerer`) — o MESMO erro nos 5 backends.
 */
class RawRowCollectionAccessE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tmp, String body) throws IOException {
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, "main() {\n" + body + "\n}\n");
        return driver.compile(source, tmp.resolve("out-" + System.nanoTime()), Target.JVM);
    }

    private boolean hasSem(CompilationResult r, String code, String needle) {
        return r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> code.equals(d.code()) && d.message().contains(needle));
    }

    @Test
    void getWithStringArgOnStringIsRejected(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp,
                "    var row = \"pwhash\" + 7\n"
              + "    println(row.get(\"pwhash\"))");
        assertFalse(r.success(), "String.get(col) must be rejected at compile-time, "
                + "not NoSuchMethodError at runtime: " + r.diagnostics().getDiagnostics());
        assertTrue(hasSem(r, "SEM064", "get"), "must be SEM064 naming get: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void sizeCallOnStringIsRejected(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp,
                "    var row = \"{\\\"n\\\":1}\"\n"
              + "    println(row.size())");
        assertFalse(r.success(), "String.size() must be rejected (JSON row, not a map): "
                + r.diagnostics().getDiagnostics());
        assertTrue(hasSem(r, "SEM064", "size"), "must be SEM064 naming size: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void listGetAndStringMethodsStayLegal(@TempDir Path tmp) throws IOException {
        // não-regressão: .get(INT) em List, e métodos de String/stdlib
        // continuam aceitos.
        CompilationResult r = compile(tmp,
                "    var xs = new List<Int>()\n"
              + "    xs.add(1); xs.add(2)\n"
              + "    var s = \"abc\"\n"
              + "    println(xs.get(0))\n"
              + "    println(s.length)\n"
              + "    println(s.toUpperCase())\n"
              + "    println(s.startsWith(\"a\"))");
        assertTrue(r.success(), "legal forms must not be flagged: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void rawDbQueryRowAccessProducesCompileDiagnostic(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp,
                "    var db = db.connect(\"jdbc:h2:mem:r193;DB_CLOSE_DELAY=-1\")\n"
              + "    db.execute(db, \"create table u(id int, pwhash varchar(50))\")\n"
              + "    var rows = db.query(db, \"select pwhash from u where id = ?\", 1)\n"
              + "    var rec = rows.get(0)\n"
              + "    println(rec.get(\"pwhash\"))");
        assertFalse(r.success(), "o repro do §193 (rec.get(col) na linha crua) deve "
                + "falhar em compile-time com diagnóstico, não em runtime: "
                + r.diagnostics().getDiagnostics());
        assertTrue(hasSem(r, "SEM064", "get"), "SEM064 apontando get: "
                + r.diagnostics().getDiagnostics());
    }
}
