package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 8.5 (plano universal): os exemplos de `training/idioms/stdlib.md` sao
 * compilandos aqui ANTES de virarem doc (sintaxe nao confirmada nao entra no
 * corpus). `facesPerTarget` mede a matriz honesta JVM/SCRIPT/JS/NATIVE de
 * time/cache/log/config/process/net; se um dispatcher mudar a aridade, a
 * secao do doc quebra aqui primeiro.
 */
class StdlibIdiomsCompileTest {

    private final CompilerDriver driver = new CompilerDriver();

    private void probe(String src) throws Exception {
        Path source = Files.createTempFile("probe", ".kf");
        Files.writeString(source, src);
        Path out = Files.createTempDirectory("probeout");
        var r = driver.compile(source, out, Target.JVM);
        assertTrue(r.success(), "DIAG: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void timeDates() throws Exception {
        probe("""
main() {
    val iso = time.todayIso()
    val next = time.addDays(iso, 7)
    val gap = time.diffDays("2026-01-01", "2026-09-19")
    val fmt = time.formatDateIso(2026, 9, 19)
    val leap = time.isLeapYear(2026)
    val dim = time.daysInMonth(2026, 2)
    val dow = time.dayOfWeek(2026, 9, 19)
    val wk = time.isWeekend(2026, 9, 19)
    val bt = time.daysBetween(2026, 1, 1, 2026, 9, 19)
    val td = time.isToday(2026, 9, 19)
    val off = time.tzOffsetSeconds()
    val hrs = time.hoursBetween(2026, 9, 19, 0, 2026, 9, 19, 12)
    val parsed = time.parseDateIso("2026-09-19")
    println("" + fmt + gap + next + leap + dim + dow + wk + bt + td + off + hrs + parsed)
}
""");
    }

    @Test
    void intervalAndSleep() throws Exception {
        probe("""
main() {
    val id = time.interval(1000, () -> println("tick"))
    time.cancel(id)
    time.sleep(10)
    val t = time.now()
    println(t)
}
""");
    }

    @Test
    void processRunSpawn() throws Exception {
        probe("""
main() {
    val r = process.run("echo", "hi", "there")
    println(r.stdout)
    println(r.exitCode)
    val h = process.spawn("echo", "bg")
}
""");
    }

    @Test
    void processRunNoArgsAndExit() throws Exception {
        probe("""
main() {
    val r = process.run("pwd")
    if (r.exitCode != 0) {
        process.exit(1)
    }
    println(r.stdout)
}
""");
    }

    @Test
    void netFull() throws Exception {
        probe("""
main() {
    val u = "https://api.example.com:8080/v1/items?page=2#top"
    val s = net.scheme(u)
    val h = net.host(u)
    val p = net.port(u)
    val pa = net.path(u)
    val q = net.query(u)
    val f = net.fragment(u)
    val enc = net.queryEncode("a b&c")
    val dec = net.queryDecode("a%20b%26c")
    println(s + h + p + pa + q + f + enc + dec)
}
""");
    }

    @Test
    void cacheAndLogAndConfigBasics() throws Exception {
        probe("""
main() {
    cache.set("k", "v")
    cache.set("k2", "v2", 60)
    val v = cache.get("k")
    val t = cache.ttl("k2")
    cache.delete("k")
    cache.clear()
    log.debug("d")
    log.info("boot")
    log.warn("careful")
    log.error("boom")
    println(v + t)
}
""");
    }

    @Test
    void configBasics() throws Exception {
        probe("""
main() {
    val port = config.get("server.port")
    val name = config.str("app.name", "demo")
    val debug = config.bool("app.debug", false)
    val hasIt = config.has("app.name")
    val home = config.env("HOME")
    val req = config.required("db.url")
    println(port + name + debug + hasIt + home + req)
}
""");
    }
    private String probeTarget(String tag, String src, Target t) throws Exception {
        Path source = Files.createTempFile("mt", ".kf");
        Files.writeString(source, src);
        Path out = Files.createTempDirectory("mtout");
        var r = driver.compile(source, out, t);
        return tag + " " + t + " " + (r.success() ? "OK"
                : "GATE " + r.diagnostics().getDiagnostics().stream()
                        .map(d -> d.code()).filter(c -> c != null && !c.isEmpty()).findFirst().orElse("?"));
    }

    @Test
    void facesPerTargetMatrix() throws Exception {
        String srcs = """
main() {
    cache.set("k", "v")
    val v = cache.get("k")
    log.info("x")
    val c = config.get("server.port")
    val leap = time.isLeapYear(2026)
    val n = net.host("https://x.io/a")
    println(v + c + leap + n)
}
""";
        String srcp = """
main() {
    val r = process.run("echo", "hi")
    println(r.stdout)
}
""";
        String srcis = """
main() {
    val id = time.interval(1000, () -> println("tick"))
    time.cancel(id)
    val h = process.spawn("echo", "bg")
    val iso = time.todayIso()
    println(iso)
}
""";
        for (Target t : new Target[] { Target.JVM, Target.SCRIPT, Target.JS, Target.NATIVE }) {
            System.out.println("PROBE " + probeTarget("plain", srcs, t));
            System.out.println("PROBE " + probeTarget("process", srcp, t));
            System.out.println("PROBE " + probeTarget("spawn-interval", srcis, t));
            System.out.println("PROBE " + probeTarget("cache", "main() { cache.set(\"k\", \"v\"); println(cache.get(\"k\")) }\n", t));
            System.out.println("PROBE " + probeTarget("log", "main() { log.info(\"x\") }\n", t));
            System.out.println("PROBE " + probeTarget("config", "main() { println(config.get(\"a.b\")) }\n", t));
            System.out.println("PROBE " + probeTarget("time-date", "main() { println(time.isLeapYear(2026)) }\n", t));
            System.out.println("PROBE " + probeTarget("time-clock", "main() { time.sleep(5); println(time.now()) }\n", t));
            System.out.println("PROBE " + probeTarget("time-interval", "main() { val i = time.interval(1000, () -> println(\"t\")); time.cancel(i) }\n", t));
            System.out.println("PROBE " + probeTarget("net", "main() { println(net.host(\"https://x.io/a\")) }\n", t));
        }
    }
}
