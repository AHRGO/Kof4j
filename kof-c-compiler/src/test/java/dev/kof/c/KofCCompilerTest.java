package dev.kof.c;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofCCompilerTest {

    @Test
    void helloNative(@TempDir Path tmp) throws Exception {
        Path c = tmp.resolve("hello.c");
        Files.writeString(c, """
                int x;
                void main() {
                  x = 42;
                  print_arg = x;
                  print();
                }
                """);
        var res = KofCCompiler.compile(c, tmp.resolve("out"));
        assertTrue(res.success(), res.diagnostics());
        assertTrue(Files.exists(res.binary()));
        ProcessBuilder pb = new ProcessBuilder(res.binary().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        int ec = p.waitFor();
        assertEquals(0, ec);
        assertEquals("42", out.trim());
    }

    @Test
    void whileLoop(@TempDir Path tmp) throws Exception {
        Path c = tmp.resolve("w.c");
        Files.writeString(c, """
                int x;
                int y;
                void main() {
                  x = 0;
                  y = 5;
                  while(x < y) {
                    x = x + 1;
                  }
                  print_arg = x;
                  print();
                }
                """);
        var res = KofCCompiler.compile(c, tmp.resolve("out2"));
        assertTrue(res.success(), res.diagnostics());
        Process p = new ProcessBuilder(res.binary().toString()).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        assertEquals("5", out.trim());
    }

    @Test
    void ifBranch(@TempDir Path tmp) throws Exception {
        Path c = tmp.resolve("if.c");
        Files.writeString(c, """
                int x;
                int y;
                void main() {
                  x = 10;
                  y = 0;
                  if(x > 5) {
                    y = 1;
                  }
                  print_arg = y;
                  print();
                }
                """);
        var res = KofCCompiler.compile(c, tmp.resolve("out3"));
        assertTrue(res.success());
        Process p = new ProcessBuilder(res.binary().toString()).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        assertEquals("1", out.trim());
    }

    @Test
    void derefAndAddr(@TempDir Path tmp) throws Exception {
        Path c = tmp.resolve("deref.c");
        Files.writeString(c, """
                int x;
                int p;
                void main() {
                  x = 99;
                  p = &x;
                  *(int*)p = 42;
                  print_arg = x;
                  print();
                }
                """);
        var res = KofCCompiler.compile(c, tmp.resolve("out4"));
        assertTrue(res.success(), res.diagnostics());
        Process p = new ProcessBuilder(res.binary().toString()).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        assertEquals("42", out.trim());
    }

    @Test
    void binaryOps(@TempDir Path tmp) throws Exception {
        Path c = tmp.resolve("ops.c");
        Files.writeString(c, """
                int a;
                int b;
                int c;
                void main() {
                  a = 10;
                  b = 3;
                  c = a + b;
                  print_arg = c;
                  print();
                }
                """);
        var res = KofCCompiler.compile(c, tmp.resolve("out5"));
        assertTrue(res.success());
        Process p = new ProcessBuilder(res.binary().toString()).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        assertEquals("13", out.trim());
    }

    @Test
    void malformedSourceReportsDiagnosticAndEmitsNoBinary(@TempDir Path tmp) throws Exception {
        // Bug (achado pela lane CodeQL, alert #485): KofCParser.error() era um
        // stub vazio — engolia o diagnostico e o compile() emitia um binario a
        // partir de uma AST lixo em silencio (R6/Q7). Agora o parser coleta os
        // erros e compile() aborta antes de montar o binario.
        Path c = tmp.resolve("bad.c");
        Files.writeString(c, "int x;\nvoid main) { x = 1;\n"); // '(' faltando
        var res = KofCCompiler.compile(c, tmp.resolve("outbad"));
        assertFalse(res.success(), "entrada malformada deve falhar, nao binarizar AST lixo");
        assertNull(res.binary(), "nenhum binario deve ser produzido");
        assertTrue(res.diagnostics().contains("Expected"),
                "diagnostico deve dizer o que era esperado, veio: " + res.diagnostics());
    }

    @Test
    void validSourceStillCompilesWithEmptyDiagnostics(@TempDir Path tmp) throws Exception {
        // Q3 edge: o error() novo nao pode introduzir falsos-positivos — o
        // caminho feliz continua com diagnostics vazio e success=true.
        Path c = tmp.resolve("ok.c");
        Files.writeString(c, "int x;\nvoid main() {\n  x = 7;\n  print_arg = x;\n  print();\n}\n");
        var res = KofCCompiler.compile(c, tmp.resolve("outok"));
        assertTrue(res.success(), "valido nao deve reportar erro: " + res.diagnostics());
        assertEquals("", res.diagnostics().trim(), "caminho feliz nao deve gerar diagnostico");
        Process p = new ProcessBuilder(res.binary().toString()).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        assertEquals("7", out.trim());
    }
}
