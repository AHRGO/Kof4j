package dev.kof.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressao de paridade help x parser: a linha de usage de cada comando deve
 * anunciar exatamente os targets que o comando aceita.
 *
 * Drift historico (corrigido aqui):
 *  - `build`/`run` omitiam `native.risc|native.arm` — aceitos por
 *    {@link KofCliSupport#parseTarget(String)} e documentados em
 *    `docs/status.md:88-89`;
 *  - `bench`/`profile` anunciavam `android`, que `BenchDiscovery.parseTarget`
 *    e `Profile.parseTarget` rejeitam com `unknown target`;
 *  - `test` anunciava `jvm|native` e omitia `js` (`docs/status.md:566`).
 */
class CliUsageTargetsTest {

    private static String mainUsage() {
        PrintStream realOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            Main.main(new String[0]);
        } finally {
            System.setOut(realOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static String lineOf(String usage, String cmd) {
        for (String l : usage.split("\\R")) {
            if (l.trim().startsWith(cmd + " ")) return l.trim();
        }
        return fail("usage sem linha para `" + cmd + "`:\n" + usage);
    }

    @Test
    void buildAndRunAdvertiseNativeRiscAndArm() {
        String usage = mainUsage();
        for (String cmd : new String[] { "build", "run" }) {
            String line = lineOf(usage, cmd);
            assertTrue(line.contains("native.risc"),
                    cmd + " deve anunciar native.risc (aceito por parseTarget): " + line);
            assertTrue(line.contains("native.arm"),
                    cmd + " deve anunciar native.arm (aceito por parseTarget): " + line);
        }
    }

    @Test
    void benchAndProfileDoNotAdvertiseUnsupportedAndroid() {
        String usage = mainUsage();
        for (String cmd : new String[] { "bench", "profile" }) {
            String line = lineOf(usage, cmd);
            assertFalse(line.contains("android"),
                    cmd + " rejeita android (unknown target) — usage nao deve anuncia-lo: " + line);
        }
    }

    @Test
    void testAdvertisesJsAndNotAndroid() {
        String line = lineOf(mainUsage(), "test");
        assertTrue(line.contains("js"), "test aceita js (status.md:566) — usage: " + line);
        assertFalse(line.contains("android"), "test nao aceita android — usage: " + line);
    }
}
