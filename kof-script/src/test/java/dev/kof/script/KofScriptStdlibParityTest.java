package dev.kof.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Paridade interpretador (Target.SCRIPT) × JVM compilado da stdlib nova da
 * lane STDLIB (S10/S11/S12/S12b/S3b-ext/S7-ext). O interpretador resolve
 * kof_* por reflexão no MESMO KofRuntime gerado (KofInterpreter javadoc:
 * "paridade por construção, não por reimplementação") — este teste PROVA a
 * afirmação nos alvos recém-adicionados, onde regressão seria silenciosa
 * (R5 cross-target). Funções não-determinísticas (random) entram pela
 * FACHADA (saída formatada), não pelo valor sorteado.
 */
class KofScriptStdlibParityTest {

    private static String norm(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").trim();
    }

    private void parity(String src, String expected) throws Exception {
        Path tmp = Files.createTempDirectory("kofparity");
        try {
            Path f = tmp.resolve("P-" + System.nanoTime() + ".kf");
            Files.writeString(f, src);
            var script = KofScript.runFile(f, dev.kof.compiler.Target.SCRIPT);
            assertTrue(script.success(), "SCRIPT: " + script.stderr());
            assertEquals(expected, norm(script.stdout()), "SCRIPT stdout");
            var comp = KofScript.runFileCompiled(f, dev.kof.compiler.Target.JVM, new String[0]);
            assertTrue(comp.success(), "JVM: " + comp.stderr());
            assertEquals(expected, norm(comp.stdout()),
                    "paridade interpretado vs compilado JVM (R5)");
        } finally {
            deleteRecursively(tmp);
        }
    }

    @Test
    void uncapitalizeParity() throws Exception {
        parity("""
            main() {
                println(strings.uncapitalize("Hello World"))
                println(strings.uncapitalize("HELLO"))
                println(strings.uncapitalize("1abc"))
                println(strings.uncapitalize("") + "|")
            }
            """, "hello World\nhELLO\n1abc\n|");
    }

    @Test
    void formatDocumentsParity() throws Exception {
        parity("""
            main() {
                println(validation.formatCpf("52998224725"))
                println(validation.formatCpf("123") + "|")
                println(validation.formatCep("01310100"))
                println(validation.formatCnpj("34546401000163"))
                println(validation.formatCnpj("123") + "|")
            }
            """, "529.982.247-25\n123|\n01310-100\n34.546.401/0001-63\n123|");
    }

    // STDLIB S13a (plan-stdlib-expansion §2, P0): math.parseInt/parseLong/
    // parseDouble — fachada sobre kof_string_to_* (o interpretador resolve
    // por reflexão no MESMO KofRuntime; paridade por construção). Golden
    // igual ao KofMathTest/ConformanceMatrixTest (fonte única de verdade);
    // erro de parse lança (contrato JDK com trim). Long > 2^53 prova Long
    // real; Double via == Bool (bug 44).
    @Test
    void mathParseParity() throws Exception {
        parity("""
            main() {
                println(math.parseInt("42"))
                println(math.parseInt(" -7 "))
                println(math.parseInt("+13"))
                println(math.parseInt("0"))
                println(math.parseInt("-2147483648"))
                println(math.parseLong("9007199254740993"))
                println(math.parseLong("-9223372036854775807"))
                println(math.parseDouble("2.5") == 2.5)
                println(math.parseDouble("  -0.25 ") == -0.25)
                println(math.parseDouble("1e2") == 100.0)
                try { println(math.parseInt("abc")); println("S1") } catch (String e) { println("T1") }
                try { println(math.parseInt("12a34")); println("S2") } catch (String e) { println("T2") }
                try { println(math.parseInt("2147483648")); println("S3") } catch (String e) { println("T3") }
                try { println(math.parseInt("")); println("S4") } catch (String e) { println("T4") }
                try { println(math.parseLong("9223372036854775808")); println("S5") } catch (String e) { println("T5") }
            }
            """, "42\n-7\n13\n0\n-2147483648\n9007199254740993\n-9223372036854775807\ntrue\ntrue\ntrue\nT1\nT2\nT3\nT4\nT5");
    }

    // STDLIB S13b (plan-stdlib-expansion §2, P0): parse com default (§43 —
    // falha DEVOLVE o default, nunca lança). Paridade interpretador×JVM;
    // Linhas ""/"   " do Double INCLUÍDAS pós-§175 (vazio lançava 0.0 no
    // Native — paridade do parse base fechada).
    @Test
    void mathParseOrDefaultParity() throws Exception {
        parity("""
            main() {
                println(math.parseIntOrDefault("42", 0))
                println(math.parseIntOrDefault("abc", -1))
                println(math.parseIntOrDefault("", 7))
                println(math.parseIntOrDefault("  15  ", 0))
                println(math.parseIntOrDefault("99999999999999", 3))
                println(math.parseLongOrDefault("9007199254740993", 0))
                println(math.parseLongOrDefault("x", -5))
                println(math.parseLongOrDefault("9223372036854775808", 8))
                println(math.parseDoubleOrDefault("2.5", 0.0) == 2.5)
                println(math.parseDoubleOrDefault("nope", -0.5) == -0.5)
                println(math.parseDoubleOrDefault("1e2", 0.0) == 100.0)
                println(math.parseDoubleOrDefault("", 1.5) == 1.5)
                println(math.parseDoubleOrDefault("   ", -0.25) == -0.25)
            }
            """, "42\n-1\n7\n15\n3\n9007199254740993\n-5\n8\ntrue\ntrue\ntrue\ntrue\ntrue");
    }

    @Test
    void isUuidParity() throws Exception {
        parity("""
            main() {
                println(uuid.isUuid("550e8400-e29b-41d4-a716-446655440000"))
                println(uuid.isUuid("550E8400-E29B-41D4-A716-446655440000"))
                println(uuid.isUuid("550e8400e29b41d4a716446655440000"))
                println(uuid.isUuid("550e8400-e29b-41d4-a716-44665544000g"))
                println(uuid.isUuid(uuid.v4()))
            }
            """, "true\ntrue\nfalse\nfalse\ntrue");
    }

    @Test
    void isWeekendParity() throws Exception {
        parity("""
            main() {
                println(time.isWeekend(2026, 9, 12))
                println(time.isWeekend(2026, 9, 13))
                println(time.isWeekend(2026, 9, 9))
                println(time.isWeekend(2026, 2, 30))
                println(time.isWeekend(2024, 2, 25))
            }
            """, "true\ntrue\nfalse\nfalse\ntrue");
    }

    @Test
    void timeHoursBetweenParity() throws Exception {
        // S7f (D3): floor simétrico; datas inválidas/hora fora 0..23 => 0.
        parity("""
            main() {
                println(time.hoursBetween(2026, 1, 1, 10, 2026, 1, 2, 12))
                println(time.hoursBetween(2026, 1, 2, 12, 2026, 1, 1, 10))
                println(time.hoursBetween(2026, 1, 1, 0, 2026, 1, 1, 23))
                println(time.hoursBetween(2026, 1, 1, 23, 2026, 1, 2, 0))
                println(time.hoursBetween(2026, 1, 1, 5, 2026, 1, 1, 5))
                println(time.hoursBetween(2026, 2, 30, 5, 2026, 3, 1, 5))
                println(time.hoursBetween(2026, 1, 1, 24, 2026, 1, 2, 5))
                println(time.hoursBetween(2024, 2, 29, 1, 2024, 3, 1, 1))
            }
            """, "26\n-26\n23\n1\n0\n0\n0\n24");
    }

    @Test
    void timeTodayParity() throws Exception {
        // S7e (D-STDLIB): todayIso por FORMATO (o dia vira — nunca valor
        // literal); formatDateIso/isToday determinísticos.
        parity("""
            main() {
                var today = time.todayIso()
                println(today.length)
                println(time.formatDateIso(2026, 9, 13))
                println(time.formatDateIso(2024, 2, 29))
                println(time.formatDateIso(2023, 2, 29))
                println(time.formatDateIso(0, 1, 1))
                println(time.formatDateIso(2026, 13, 1))
                println(time.isToday(2026, 9, 12))
                println(time.isToday(2026, 2, 30))
                var parts = today.split("-")
                println(parts.size)
                println(parts[0].length)
                println(parts[1].length)
            }
            """, "10\n2026-09-13\n2024-02-29\n\n\n\nfalse\nfalse\n3\n4\n2");
    }

    @Test
    void timeParseDateIsoParity() throws Exception {
        // S7g (D4): parse estrito; inválido => 0; serial = daysFromEpoch.
        parity("""
            main() {
                println(time.parseDateIso("1970-01-01"))
                println(time.parseDateIso("2026-09-13"))
                println(time.parseDateIso("2024-02-29"))
                println(time.parseDateIso("0001-01-01"))
                println(time.parseDateIso("9999-12-31"))
                println(time.parseDateIso("2023-02-29"))
                println(time.parseDateIso("garbage"))
                println(time.parseDateIso(""))
            }
            """, "0\n20709\n19782\n-719162\n2932896\n0\n0\n0");
    }

    @Test
    void timeTzOffsetParity() throws Exception {
        // S7h (D1): fuso do HOST; SCRIPT herda o runtime JVM => MESMO valor
        // (oracle ZoneId, medição real). Contrato: mód 60 == 0, range.
        int tzNow = java.time.ZoneId.systemDefault().getRules()
                .getOffset(java.time.Instant.now()).getTotalSeconds();
        parity("""
            main() {
                var tz = time.tzOffsetSeconds()
                println(tz % 60)
                println(tz >= -43200 && tz <= 50400)
                println(tz)
            }
            """, "0\ntrue\n" + tzNow);
    }

    @Test
    void randomFacadeParity() throws Exception {
        // Não-determinístico: valida a FACHADA (formato/contrato), não o
        // valor sorteado. randomInt(1) == 0 travado; randomString(-1) == "";
        // randomBoolean() ∈ {true,false}; randomString(8, "ab") é 8 chars
        // de {a,b}.
        parity("""
            main() {
                println(random.randomInt(1))
                println(random.randomString(-1, "abc") + "|")
                println(random.randomString(0, "abc") + "|")
                println(random.randomString(4, "") + "|")
                var b = random.randomBoolean()
                println(b || !b)
                var s = random.randomString(8, "ab")
                println(s.length)
                println(s == s)
            }
            """, "0\n|\n|\n|\ntrue\n8\ntrue");
    }

    @Test
    void indentDedentParity() throws Exception {
        parity("""
            main() {
                println("---")
                println(strings.indent("a\\nb", 2))
                println(strings.indent("x", 0))
                println(strings.dedent("  a\\n    b"))
                println(strings.dedent("hello"))
            }
            """, "---\n  a\n  b\nx\na\n  b\nhello");
    }

    private static void deleteRecursively(Path dir) {
        try (var s = Files.walk(dir)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignore) {}
            });
        } catch (Exception ignore) {}
    }
}
