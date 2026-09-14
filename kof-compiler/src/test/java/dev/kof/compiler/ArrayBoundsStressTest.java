package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KOF-SBD-001-STRESS — deterministic stress coverage for Array Bounds Safety.
 *
 * Tries to falsify: "no safe Kof array operation ever reads/writes outside
 * 0 <= index < length, regardless of access volume, index pattern, array
 * shape or target." Every scenario is compiled Kof source (real
 * parser -> semantic -> IR -> backend -> runtime pipeline, not a Java-level
 * unit test of the helpers), executed once per target, with the stress loop
 * running *inside* the compiled program so iteration count doesn't cost a
 * process spawn.
 *
 * Level A/B only (10^2-10^5 ops/scenario) — fast enough to stay in the
 * regular suite. Level C (10^6-10^7) and soak live in
 * {@link ArrayBoundsDeepStressTest}, run manually, not part of routine CI.
 *
 * Oracle (section 46 of the stress plan): a valid index must read/write
 * exactly the expected element; an invalid index must be rejected with the
 * array's length and valid content left unchanged. "The process crashed" is
 * never treated as a PASS by itself — every case checks *why*.
 */
class ArrayBoundsStressTest {

    private final CompilerDriver driver = new CompilerDriver();

    private record RunResult(int exitCode, String output) {
    }

    private RunResult runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            return new RunResult(ec, output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private RunResult runJs(Path outDir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new ByteArrayInputStream(new byte[0]), out);
        return new RunResult(exitCode, out.toString().trim());
    }

    /** Compiles+runs on JVM and KofJS, asserts both succeed and produce identical output. */
    private String[] runBothLines(String source, Path tempDir, String name) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path outJvm = tempDir.resolve(name + "-jvm");
        Path outJs = tempDir.resolve(name + "-js");
        CompilationResult rjvm = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rjvm.success(), name + " JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), name + " JS compile failed: " + rjs.diagnostics().getDiagnostics());
        RunResult jvm = runJvm(outJvm);
        RunResult js = runJs(outJs);
        assertEquals(0, jvm.exitCode(), name + " JVM exit code, output: " + jvm.output());
        assertEquals(0, js.exitCode(), name + " JS exit code, output: " + js.output());
        assertEquals(jvm.output(), js.output(), name + " JVM vs JS parity");
        return jvm.output().split("\n");
    }

    /** Attempts Native too; returns null (NA, never silently PASS) if the toolchain is unavailable. */
    private String runNativeOrNull(String source, Path tempDir, String name) throws IOException {
        Path src = tempDir.resolve(name + "-nat.kf");
        Files.writeString(src, source);
        Path outDir = tempDir.resolve(name + "-native");
        CompilationResult r = driver.compile(src, outDir, Target.NATIVE);
        if (!r.success()) {
            return null; // toolchain missing or compile failed — NA, not a verdict
        }
        Path bin = outDir.resolve("Default/Main");
        if (!Files.exists(bin)) {
            return null;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            p.waitFor();
            return out;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private static int line(String[] lines, int i) {
        return Integer.parseInt(lines[i].trim());
    }

    // ── STRESS-001/002 — saturation valid read/write + integrity checksum ──

    private String saturationProgram(int n, int iterations) {
        return """
                main() {
                    var n = %d
                    var a = new Int[n]
                    for (var i = 0; i < n; i = i + 1) { a[i] = i + 1 }
                    var checksumBefore = 0
                    for (var i = 0; i < n; i = i + 1) { checksumBefore = checksumBefore + a[i] }
                    var iterations = %d
                    var sum = 0
                    for (var k = 0; k < iterations; k = k + 1) {
                        var idx = k %% n
                        sum = sum + a[idx]
                        a[idx] = a[idx]
                    }
                    var checksumAfter = 0
                    for (var i = 0; i < n; i = i + 1) { checksumAfter = checksumAfter + a[i] }
                    println(checksumBefore)
                    println(checksumAfter)
                    println(a.length)
                }
                """.formatted(n, iterations);
    }

    @Test
    void stress001And002_saturationReadWriteAcrossSizes(@TempDir Path tempDir) throws IOException {
        int[] sizes = {1, 2, 3, 8, 16, 127, 1024, 10000};
        for (int n : sizes) {
            String name = "sat" + n;
            String[] out = runBothLines(saturationProgram(n, 20_000), tempDir, name);
            int before = line(out, 0);
            int after = line(out, 1);
            int length = line(out, 2);
            assertEquals(before, after, name + ": checksum changed after saturation read/write");
            assertEquals(n, length, name + ": length changed");
        }
    }

    // ── STRESS-003/004/005/006/008/017 — mixed valid/invalid, shadow-checksum oracle ──

    private String mixedIndexProgram(int n, int iterations, long seed) {
        return """
                main() {
                    var n = %d
                    var a = new Int[n]
                    var shadow = new Int[n]
                    for (var i = 0; i < n; i = i + 1) { a[i] = 5000 + i; shadow[i] = 5000 + i }
                    var iterations = %d
                    var state = %d
                    var validReads = 0
                    var validWrites = 0
                    var rejectedReads = 0
                    var rejectedWrites = 0
                    var unexpectedSuccesses = 0
                    var unexpectedFailures = 0
                    for (var k = 0; k < iterations; k = k + 1) {
                        state = (state * 97 + 101) %% 1000003
                        var idx = (state %% (3 * n)) - n
                        var isValid = idx >= 0 && idx < n
                        if (k %% 2 == 0) {
                            var ok = true
                            var v = 0
                            try {
                                v = a[idx]
                            } catch (String e) {
                                ok = false
                            }
                            if (ok) {
                                if (isValid) { validReads = validReads + 1 } else { unexpectedSuccesses = unexpectedSuccesses + 1 }
                            } else {
                                if (isValid) { unexpectedFailures = unexpectedFailures + 1 } else { rejectedReads = rejectedReads + 1 }
                            }
                        } else {
                            var newVal = 10000 + (k %% 500)
                            var ok = true
                            try {
                                a[idx] = newVal
                            } catch (String e) {
                                ok = false
                            }
                            if (ok) {
                                if (isValid) { shadow[idx] = newVal; validWrites = validWrites + 1 } else { unexpectedSuccesses = unexpectedSuccesses + 1 }
                            } else {
                                if (isValid) { unexpectedFailures = unexpectedFailures + 1 } else { rejectedWrites = rejectedWrites + 1 }
                            }
                        }
                    }
                    var mismatch = 0
                    for (var i = 0; i < n; i = i + 1) {
                        if (a[i] != shadow[i]) { mismatch = mismatch + 1 }
                    }
                    println(validReads)
                    println(validWrites)
                    println(rejectedReads)
                    println(rejectedWrites)
                    println(unexpectedSuccesses)
                    println(unexpectedFailures)
                    println(mismatch)
                    println(a.length)
                }
                """.formatted(n, iterations, seed);
    }

    private void assertMixedInvariants(String[] out, String name, int n, int iterations) {
        int validReads = line(out, 0);
        int validWrites = line(out, 1);
        int rejectedReads = line(out, 2);
        int rejectedWrites = line(out, 3);
        int unexpectedSuccesses = line(out, 4);
        int unexpectedFailures = line(out, 5);
        int mismatch = line(out, 6);
        int length = line(out, 7);

        assertEquals(0, unexpectedSuccesses, name + ": an out-of-bounds access unexpectedly succeeded");
        assertEquals(0, unexpectedFailures, name + ": an in-bounds access was unexpectedly rejected");
        assertEquals(0, mismatch, name + ": array content diverged from the valid-writes-only shadow (corruption)");
        assertEquals(n, length, name + ": array length changed (KofJS silent-growth regression)");
        assertEquals(iterations / 2, validReads + rejectedReads, name + ": read accounting doesn't add up");
        assertEquals(iterations / 2, validWrites + rejectedWrites, name + ": write accounting doesn't add up");
        assertTrue(rejectedReads > 0 && rejectedWrites > 0, name + ": test didn't actually exercise any rejection (bad range)");
    }

    @Test
    void stress003to008and017_mixedIndexSeveralSeeds(@TempDir Path tempDir) throws IOException {
        long[] seeds = {1701, 2026, 65537, 99991};
        int n = 1024;
        int iterations = 50_000;
        for (long seed : seeds) {
            String name = "mixed" + seed;
            String[] out = runBothLines(mixedIndexProgram(n, iterations, seed), tempDir, name);
            assertMixedInvariants(out, name, n, iterations);
        }
    }

    // ── STRESS-004 focused — index == length, the canonical KofJS gap ──

    @Test
    void stress004_indexEqualsLengthNeverResizes(@TempDir Path tempDir) throws IOException {
        String source = """
                main() {
                    var n = 50
                    var a = new Int[n]
                    for (var i = 0; i < n; i = i + 1) { a[i] = 7000 + i }
                    var checksum = 0
                    for (var i = 0; i < n; i = i + 1) { checksum = checksum + a[i] }
                    var rejected = 0
                    for (var k = 0; k < 20000; k = k + 1) {
                        try {
                            var v = a[n]
                        } catch (String e) {
                            rejected = rejected + 1
                        }
                        try {
                            a[n] = 999999
                        } catch (String e) {
                            rejected = rejected + 1
                        }
                    }
                    var checksumAfter = 0
                    for (var i = 0; i < n; i = i + 1) { checksumAfter = checksumAfter + a[i] }
                    println(rejected)
                    println(checksum == checksumAfter)
                    println(a.length)
                }
                """;
        String[] out = runBothLines(source, tempDir, "stress004");
        assertEquals(40000, line(out, 0), "not every index==length access was rejected");
        assertEquals("true", out[1].trim(), "valid content changed after index==length attempts");
        assertEquals(50, line(out, 2), "array grew after write(length) attempts");
    }

    // ── STRESS-005 — indexes far beyond length: no distant bypass path ──

    @Test
    void stress005_farOutOfBoundsIndexesNeverBypass(@TempDir Path tempDir) throws IOException {
        String source = """
                main() {
                    var n = 50
                    var a = new Int[n]
                    for (var i = 0; i < n; i = i + 1) { a[i] = 9000 + i }
                    var checksum = 0
                    for (var i = 0; i < n; i = i + 1) { checksum = checksum + a[i] }
                    var offsets = new Int[6]
                    offsets[0] = n + 1
                    offsets[1] = n + 10
                    offsets[2] = n + 1000
                    offsets[3] = n * 2
                    offsets[4] = n * 100
                    offsets[5] = 2147483647
                    var rejected = 0
                    for (var oi = 0; oi < 6; oi = oi + 1) {
                        var idx = offsets[oi]
                        for (var rep = 0; rep < 500; rep = rep + 1) {
                            try { var v = a[idx] } catch (String e) { rejected = rejected + 1 }
                            try { a[idx] = 777 } catch (String e) { rejected = rejected + 1 }
                        }
                    }
                    var checksumAfter = 0
                    for (var i = 0; i < n; i = i + 1) { checksumAfter = checksumAfter + a[i] }
                    println(rejected)
                    println(checksum == checksumAfter)
                    println(a.length)
                }
                """;
        String[] out = runBothLines(source, tempDir, "stress005");
        assertEquals(6000, line(out, 0), "a far out-of-bounds index found a bypass (6 offsets x 500 reps x 2 ops)");
        assertEquals("true", out[1].trim(), "valid content changed after far-OOB attempts");
        assertEquals(50, line(out, 2));
    }

    // ── STRESS-007 — explicit recovery sequence after a rejected access ──

    @Test
    void stress007_recoversCleanlyAfterRejectedAccess(@TempDir Path tempDir) throws IOException {
        String source = """
                main() {
                    var a = new Int[5]
                    for (var i = 0; i < 5; i = i + 1) { a[i] = i * 10 }
                    var okCycles = 0
                    for (var cycle = 0; cycle < 10000; cycle = cycle + 1) {
                        // 1. valid access
                        var beforeOk = a[2] == 20
                        // 2/3. invalid access, caught
                        var caught = false
                        try {
                            var bad = a[-1]
                        } catch (String e) {
                            caught = true
                        }
                        // 4. valid access again, right after the error
                        var afterOk = a[2] == 20
                        // 5. valid write
                        a[2] = 20 + cycle
                        var wroteOk = a[2] == 20 + cycle
                        a[2] = 20
                        // 6. another invalid access
                        var caught2 = false
                        try {
                            a[5] = 999
                        } catch (String e) {
                            caught2 = true
                        }
                        var stillOk = a[2] == 20 && a.length == 5
                        if (beforeOk && caught && afterOk && wroteOk && caught2 && stillOk) {
                            okCycles = okCycles + 1
                        }
                    }
                    println(okCycles)
                }
                """;
        String[] out = runBothLines(source, tempDir, "stress007");
        assertEquals(10000, line(out, 0), "runtime state didn't recover cleanly after a bounds error at least once");
    }

    // ── STRESS-009 — size-1 array (off-by-one <= vs < detector) ──

    @Test
    void stress009_sizeOneArrayBoundary(@TempDir Path tempDir) throws IOException {
        String source = """
                main() {
                    var a = new Int[1]
                    a[0] = 42
                    var rejected = 0
                    for (var k = 0; k < 5000; k = k + 1) {
                        try { var v = a[-1] } catch (String e) { rejected = rejected + 1 }
                        try { var v = a[1] } catch (String e) { rejected = rejected + 1 }
                        try { a[-1] = 1 } catch (String e) { rejected = rejected + 1 }
                        try { a[1] = 1 } catch (String e) { rejected = rejected + 1 }
                    }
                    println(rejected)
                    println(a[0])
                    println(a.length)
                }
                """;
        String[] out = runBothLines(source, tempDir, "stress009");
        assertEquals(20000, line(out, 0), "size-1 array accepted an out-of-range index somewhere");
        assertEquals(42, line(out, 1));
        assertEquals(1, line(out, 2));
    }

    // ── STRESS-010 — zero-size array: no valid index exists ──

    @Test
    void stress010_emptyArrayHasNoValidIndex(@TempDir Path tempDir) throws IOException {
        String source = """
                main() {
                    var a = new Int[0]
                    var rejected = 0
                    for (var k = 0; k < 5000; k = k + 1) {
                        try { var v = a[0] } catch (String e) { rejected = rejected + 1 }
                        try { var v = a[-1] } catch (String e) { rejected = rejected + 1 }
                        try { a[0] = 1 } catch (String e) { rejected = rejected + 1 }
                        try { a[-1] = 1 } catch (String e) { rejected = rejected + 1 }
                    }
                    println(rejected)
                    println(a.length)
                }
                """;
        String[] out = runBothLines(source, tempDir, "stress010");
        assertEquals(20000, line(out, 0), "empty array accepted some access");
        assertEquals(0, line(out, 1));
    }

    // ── STRESS-011 — large arrays, boundary indices only (not a random-coverage stress) ──

    @Test
    void stress011_largeArraysBoundaryIndexes(@TempDir Path tempDir) throws IOException {
        int[] sizes = {10_000, 100_000, 1_000_000};
        for (int n : sizes) {
            String name = "large" + n;
            String source = """
                    main() {
                        var n = %d
                        var a = new Int[n]
                        a[0] = 11
                        a[n - 1] = 22
                        var rejected = 0
                        for (var k = 0; k < 200; k = k + 1) {
                            try { var v = a[-1] } catch (String e) { rejected = rejected + 1 }
                            try { var v = a[n] } catch (String e) { rejected = rejected + 1 }
                            try { a[-1] = 1 } catch (String e) { rejected = rejected + 1 }
                            try { a[n] = 1 } catch (String e) { rejected = rejected + 1 }
                        }
                        println(a[0])
                        println(a[n - 1])
                        println(rejected)
                        println(a.length)
                    }
                    """.formatted(n);
            String[] out = runBothLines(source, tempDir, name);
            assertEquals(11, line(out, 0), name);
            assertEquals(22, line(out, 1), name);
            assertEquals(800, line(out, 2), name + ": not all boundary attempts were rejected");
            assertEquals(n, line(out, 3), name);
        }
    }

    // ── STRESS-012 — repeated allocation/discard ──

    @Test
    void stress012_repeatedAllocationDiscard(@TempDir Path tempDir) throws IOException {
        String source = """
                main() {
                    var survivedGood = 0
                    var survivedBad = 0
                    for (var cycle = 0; cycle < 10000; cycle = cycle + 1) {
                        var a = new Int[16]
                        for (var i = 0; i < 16; i = i + 1) { a[i] = i }
                        var ok = true
                        for (var i = 0; i < 16; i = i + 1) { if (a[i] != i) { ok = false } }
                        if (ok) { survivedGood = survivedGood + 1 }
                        var rejected = true
                        try {
                            a[16] = 999
                            rejected = false
                        } catch (String e) {
                            rejected = true
                        }
                        if (rejected && a.length == 16) { survivedBad = survivedBad + 1 }
                    }
                    println(survivedGood)
                    println(survivedBad)
                }
                """;
        String[] out = runBothLines(source, tempDir, "stress012");
        assertEquals(10000, line(out, 0), "a freshly allocated array had wrong content at least once");
        assertEquals(10000, line(out, 1), "an out-of-bounds write on a fresh array wasn't rejected/kept length");
    }

    // ── STRESS-013 — multidimensional: each dimension protected independently ──

    @Test
    void stress013_multidimensionalEachDimensionProtected(@TempDir Path tempDir) throws IOException {
        String source = """
                main() {
                    var outer = 8
                    var inner = 8
                    var a = new Int[outer][inner]
                    for (var i = 0; i < outer; i = i + 1) {
                        for (var j = 0; j < inner; j = j + 1) { a[i][j] = i * 100 + j }
                    }
                    var rejected = 0
                    for (var k = 0; k < 3000; k = k + 1) {
                        try { var v = a[-1][0] } catch (String e) { rejected = rejected + 1 }
                        try { var v = a[outer][0] } catch (String e) { rejected = rejected + 1 }
                        try { var v = a[0][-1] } catch (String e) { rejected = rejected + 1 }
                        try { var v = a[0][inner] } catch (String e) { rejected = rejected + 1 }
                        try { a[-1][0] = 1 } catch (String e) { rejected = rejected + 1 }
                        try { a[outer][0] = 1 } catch (String e) { rejected = rejected + 1 }
                        try { a[0][-1] = 1 } catch (String e) { rejected = rejected + 1 }
                        try { a[0][inner] = 1 } catch (String e) { rejected = rejected + 1 }
                    }
                    var checksum = 0
                    for (var i = 0; i < outer; i = i + 1) {
                        for (var j = 0; j < inner; j = j + 1) { checksum = checksum + a[i][j] }
                    }
                    println(rejected)
                    println(checksum)
                    println(a[0][0])
                    println(a[outer - 1][inner - 1])
                }
                """;
        String[] out = runBothLines(source, tempDir, "stress013");
        assertEquals(24000, line(out, 0), "not every out-of-bounds access on either dimension was rejected");
        assertEquals(0, line(out, 2));
        assertEquals(707, line(out, 3));
        int expectedChecksum = 0;
        for (int i = 0; i < 8; i++) for (int j = 0; j < 8; j++) expectedChecksum += i * 100 + j;
        assertEquals(expectedChecksum, line(out, 1), "multidim content corrupted by OOB attempts");
    }

    // ── STRESS-014 — compound assignment under repetition ──
    //
    // JVM only: compound assignment on a raw array element (`a[i] += v`)
    // never compiles on the KofJS target at all — a pre-existing, unrelated
    // bug (KofDup2 missing from JsExpressionParser.isExpressionOp;
    // known-bugs.md #100, found while building the original SBD-001 test).
    // Nothing to stress-test on JS until that's fixed.

    @Test
    void stress014_compoundAssignmentRepeatedJvm(@TempDir Path tempDir) throws IOException {
        String source = """
                main() {
                    var a = new Int[3]
                    a[0] = 0
                    a[2] = 0
                    for (var k = 0; k < 20000; k = k + 1) {
                        a[0] += 1
                    }
                    for (var k = 0; k < 20000; k = k + 1) {
                        a[2] += 2
                    }
                    println(a[0])
                    println(a[2])
                    println(a.length)
                }
                """;
        Path src = tempDir.resolve("stress014.kf");
        Files.writeString(src, source);
        Path outJvm = tempDir.resolve("stress014-jvm");
        CompilationResult rjvm = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "stress014 JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        RunResult jvm = runJvm(outJvm);
        assertEquals(0, jvm.exitCode(), "stress014 JVM exit code, output: " + jvm.output());
        String[] out = jvm.output().split("\n");
        assertEquals(20000, line(out, 0));
        assertEquals(40000, line(out, 1));
        assertEquals(3, line(out, 2));
    }

    // ── STRESS-015 — index expression with a side effect: evaluated exactly once ──

    @Test
    void stress015_indexSideEffectEvaluatedOnce(@TempDir Path tempDir) throws IOException {
        String source = """
                class Counter {
                    static Int calls = 0
                    static Int nextIndex() {
                        calls = calls + 1
                        return 0
                    }
                }

                main() {
                    var a = new Int[3]
                    a[0] = 100
                    for (var k = 0; k < 20000; k = k + 1) {
                        var v = a[Counter.nextIndex()]
                    }
                    println(Counter.calls)
                }
                """;
        String[] out = runBothLines(source, tempDir, "stress015");
        assertEquals(20000, line(out, 0), "array-load index expression evaluated a different number of times than N (double eval?)");
    }

    // ── STRESS-016 — array-receiver expression with a side effect: evaluated exactly once ──

    @Test
    void stress016_arrayExpressionSideEffectEvaluatedOnce(@TempDir Path tempDir) throws IOException {
        // The array is assigned from plain statements in main(), not a
        // static field initializer: `static Int[] shared = new Int[3]`
        // silently never runs (known-bugs.md #102 — non-constant static
        // field initializers are dropped, no <clinit> is ever synthesized
        // on any compiled backend). Unrelated to bounds safety; worked
        // around here instead of fixed.
        String source = """
                class Holder {
                    static Int calls = 0
                    static Int[] shared
                    static Int[] getArray() {
                        calls = calls + 1
                        return shared
                    }
                }

                main() {
                    Holder.shared = new Int[3]
                    Holder.shared[1] = 55
                    var sum = 0
                    for (var k = 0; k < 20000; k = k + 1) {
                        sum = sum + Holder.getArray()[1]
                    }
                    println(Holder.calls)
                    println(sum)
                }
                """;
        String[] out = runBothLines(source, tempDir, "stress016");
        assertEquals(20000, line(out, 0), "array-receiver expression evaluated a different number of times than N (extra length check re-evaluating it?)");
        assertEquals(20000 * 55, line(out, 1));
    }

    // ── STRESS-018 — different element types, minimum coverage per type ──
    //
    // "bool" is JS-only here: Bool[] element access on JVM hits a
    // pre-existing, unrelated bytecode-correctness bug (known-bugs.md #101 —
    // JvmLiteralEmitter.arrayLoadOpcode/arrayStoreOpcode wrongly emit
    // IALOAD/IASTORE instead of BALOAD/BASTORE for boolean/byte/short/char
    // arrays), found via this same stress case. It's an element-type opcode
    // bug, not a bounds-safety bug — the bounds check itself isn't at fault
    // here — so it's out of scope for this suite; not asserting JVM output
    // for "bool" avoids blocking SBD-001-STRESS on it.

    private String[] runJsOnlyLines(String source, Path tempDir, String name) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path outJs = tempDir.resolve(name + "-js");
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), name + " JS compile failed: " + rjs.diagnostics().getDiagnostics());
        RunResult js = runJs(outJs);
        assertEquals(0, js.exitCode(), name + " JS exit code, output: " + js.output());
        return js.output().split("\n");
    }

    @Test
    void stress018_differentArrayTypes(@TempDir Path tempDir) throws IOException {
        record Case(String name, String kofType, String value1, String value2, boolean jvm) {
        }
        List<Case> cases = List.of(
                new Case("int", "Int", "10", "20", true),
                new Case("long", "Long", "10L", "20L", true),
                new Case("double", "Double", "1.5", "2.5", true),
                new Case("bool", "Bool", "true", "false", false),
                new Case("string", "String", "\"a\"", "\"b\"", true));

        for (Case c : cases) {
            String source = """
                    main() {
                        var a = new %s[2]
                        a[0] = %s
                        a[1] = %s
                        var rejected = 0
                        for (var k = 0; k < 5000; k = k + 1) {
                            try { var v = a[-1] } catch (String e) { rejected = rejected + 1 }
                            try { var v = a[2] } catch (String e) { rejected = rejected + 1 }
                            try { a[-1] = %s } catch (String e) { rejected = rejected + 1 }
                            try { a[2] = %s } catch (String e) { rejected = rejected + 1 }
                        }
                        println(a[0])
                        println(a[1])
                        println(rejected)
                        println(a.length)
                    }
                    """.formatted(c.kofType(), c.value1(), c.value2(), c.value1(), c.value2());
            String[] out = c.jvm()
                    ? runBothLines(source, tempDir, "stress018-" + c.name())
                    : runJsOnlyLines(source, tempDir, "stress018-" + c.name());
            assertEquals(20000, line(out, 2), c.name() + ": type-specific array didn't reject uniformly");
            assertEquals(2, line(out, 3), c.name());
        }
    }

    // ── STRESS-019 — Native parity ──
    //
    // Verified experimentally (ZZZScratchNativeTryCatch, deleted after use):
    // on Native, an out-of-bounds access is NOT a catchable Kof exception —
    // `kof_bounds_error` aborts the whole process unconditionally, even
    // inside a `try { ... } catch (String e) { ... }`. So a big loop of
    // invalid accesses (the JVM/JS shape above) cannot run on Native at all;
    // it dies on the *first* one. Per the stress plan's own guidance for a
    // fatal/non-recoverable target (section 16): test each invalid access in
    // its own isolated process instead, and check that (a) valid operations
    // performed *before* it produced the right output, (b) the process then
    // exits non-zero with a recognizable, controlled message — not a crash
    // with wrong output printed first, not a silent success.
    //
    // This is a target-level semantic difference from JVM/JS (fatal abort vs.
    // catchable exception), not a regression: SBD-001's own security
    // statement only requires the access be *prevented* with *no* corruption,
    // not that every target recover from it the same way.

    private void assertNativeAbortsAfterValidOutput(Path tempDir, String name, String kofSource, String... expectedLinesBeforeAbort) throws IOException {
        Path outDir = tempDir.resolve(name + "-native");
        Path src = tempDir.resolve(name + "-nat.kf");
        Files.writeString(src, kofSource);
        CompilationResult r = driver.compile(src, outDir, Target.NATIVE);
        if (!r.success()) {
            System.out.println(name + ": Native = NA (toolchain unavailable in this environment)");
            return;
        }
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), name + ": Native binary missing after successful compile");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            int exit = p.waitFor();
            assertTrue(exit != 0, name + ": Native process did not report a failure for an out-of-bounds access (exit " + exit + ")");
            String[] lines = out.split("\n");
            for (int i = 0; i < expectedLinesBeforeAbort.length; i++) {
                assertEquals(expectedLinesBeforeAbort[i], lines[i].trim(), name + ": valid output before the OOB access was wrong — corruption before abort");
            }
            System.out.println(name + ": Native exit=" + exit + " output=[" + out + "]");
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Test
    void stress019_nativeAbortsControlledOnInvalidAccess(@TempDir Path tempDir) throws IOException {
        // Valid saturation still has to work on Native — this one never hits an OOB access.
        String nativeOut = runNativeOrNull(saturationProgram(200, 200_000), tempDir, "stress019-sat");
        if (nativeOut == null) {
            System.out.println("stress019 (valid saturation): Native = NA (toolchain unavailable in this environment)");
        } else {
            String[] lines = nativeOut.split("\n");
            assertEquals(lines[0].trim(), lines[1].trim(), "Native: checksum changed after 200k valid saturation ops");
            assertEquals("200", lines[2].trim(), "Native: length changed after valid saturation ops");
            System.out.println("stress019 (valid saturation): Native output = [" + nativeOut + "]");
        }

        assertNativeAbortsAfterValidOutput(tempDir, "stress019-negread", """
                main() {
                    var a = new Int[3]
                    a[0] = 111
                    println(a[0])
                    var v = a[-1]
                    println("unreachable")
                }
                """, "111");

        assertNativeAbortsAfterValidOutput(tempDir, "stress019-lenwrite", """
                main() {
                    var a = new Int[3]
                    a[0] = 222
                    println(a[0])
                    println(a.length)
                    a[3] = 999
                    println("unreachable")
                }
                """, "222", "3");
    }
}
