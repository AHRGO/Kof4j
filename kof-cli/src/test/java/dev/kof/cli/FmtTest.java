package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * `kof fmt` nao repassa args ao programa (diferente de run/serve), entao uma
 * flag desconhecida nao pode ser ignorada em silencio (R6).
 */
class FmtTest {

    private static int run(String... args) {
        PrintStream realOut = System.out;
        PrintStream realErr = System.err;
        System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        try {
            return Fmt.run(args);
        } finally {
            System.setOut(realOut);
            System.setErr(realErr);
        }
    }

    @Test
    void unknownFlagIsRejected(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("Main.kf");
        Files.writeString(f, "main(){println(\"hi\")}\n");

        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream realErr = System.err;
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        int ec;
        try {
            ec = Fmt.run(new String[] { "fmt", f.toString(), "--bogus" });
        } finally {
            System.setErr(realErr);
        }
        assertEquals(1, ec, "flag desconhecida deve ser recusada (R6)");
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("unknown flag"),
                err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void writeFlagStillFormats(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("Main.kf");
        Files.writeString(f, "main(){println(\"hi\")}\n");
        assertEquals(0, run("fmt", f.toString(), "-w"));
        String out = Files.readString(f);
        assertTrue(out.contains("main()"), "deve formatar com -w: " + out);
    }
}
