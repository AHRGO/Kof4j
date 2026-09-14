package dev.kof.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * §CodeQL uncaught-number-format-exception na CLI (mesma familia do fix do
 * `kof bench`): `kof serve --port <lixo>` e o framing LSP com Content-Length
 * malformado derramavam NumberFormatException crua (stack-trace no usuario /
 * morte do servidor). R6: diagnostico limpo, nunca stack-trace mudo.
 *
 * Tambem trava o AIOOBE pre-existente do bloco de usage do `kof serve` sem
 * argumentos (`args[1]` lido antes da guarda), achado ao caçar o NF do --port.
 */
class CliNumberFormatTest {

    private final PrintStream realErr = System.err;
    private final PrintStream realOut = System.out;

    @AfterEach
    void restore() {
        System.setErr(realErr);
        System.setOut(realOut);
    }

    private static String drain(ByteArrayOutputStream b) {
        String s = b.toString(StandardCharsets.UTF_8);
        b.reset();
        return s;
    }

    // ── kof serve --port <invalido> ────────────────────────────────────

    @Test
    void parsePortOptionRejectsNonNumericWithDiagnosis() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            assertNull(CmdServe.parsePortOption("abc"));
            String msg = drain(err);
            assertTrue(msg.contains("--port"), "diagnostico deve nomear a flag: " + msg);
            assertTrue(msg.contains("'abc'"), "diagnostico deve ecoar o valor: " + msg);
        } finally {
            System.setErr(realErr);
        }
    }

    @Test
    void parsePortOptionRejectsOutOfRangeAndAcceptsValid() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            assertNull(CmdServe.parsePortOption("70000"), "porta > 65535 deve rejeitar");
            assertNull(CmdServe.parsePortOption("-1"), "porta negativa deve rejeitar");
            assertTrue(drain(err).contains("0-65535"), "range esperado no diagnostico");
            assertEquals(8080, CmdServe.parsePortOption("8080"));
            assertEquals(0, CmdServe.parsePortOption("0"), "0 e porta valida (bind auto)");
            assertEquals(65535, CmdServe.parsePortOption("65535"));
            assertEquals(9093, CmdServe.parsePortOption(" 9093 "), "trim antes do parse");
        } finally {
            System.setErr(realErr);
        }
    }

    @Test
    void serveWithoutArgumentsDoesNotThrowArrayIndexOutOfBounds(@TempDir Path tmp) {
        // Bug pre-existente (achado pela lane CodeQL): o bloco de usage lia
        // args[1] ANTES de conferir args.length < 2 -> AIOOBE em `kof serve`.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            assertDoesNotThrow(() -> CmdServe.run(new String[] { "serve" }),
                    "kof serve sem arquivo deve imprimir usage, nao estourar AIOOBE");
            assertTrue(drain(err).contains("usage:") || drain(out).contains("usage:"));
            assertDoesNotThrow(() -> CmdServe.run(new String[] { "serve", "--help" }),
                    "kof serve --help deve imprimir usage sem excecao");
            assertTrue(drain(out).contains("usage:"));
        } finally {
            System.setOut(realOut);
            System.setErr(realErr);
        }
    }

    // ── LSP framing com Content-Length invalido ────────────────────────

    @Test
    void lspServerTerminatesCleanlyOnMalformedContentLength() {
        String raw = "Content-Length: not-a-number\r\n\r\n{}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LspServer server = new LspServer(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)), out);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            assertDoesNotThrow(server::run,
                    "Content-Length malformado nao pode lancar NumberFormatException");
            String msg = drain(err);
            assertTrue(msg.contains("Content-Length"), "deve reportar o header invalido (R6): " + msg);
        } finally {
            System.setErr(realErr);
        }
    }

    @Test
    void lspServerStillServesWellFormedStream() throws IOException {
        // Q3/idempotencia: o guard nao pode quebrar o caminho feliz.
        String req = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}";
        byte[] body = req.getBytes(StandardCharsets.UTF_8);
        String frame = "Content-Length: " + body.length + "\r\n\r\n" + req;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LspServer server = new LspServer(new ByteArrayInputStream(frame.getBytes(StandardCharsets.UTF_8)), out);
        server.run();
        String raw = out.toString(StandardCharsets.UTF_8);
        assertTrue(raw.contains("\"id\"") && raw.contains("capabilities"),
                "initialize deve responder normalmente: " + raw);
    }
}
