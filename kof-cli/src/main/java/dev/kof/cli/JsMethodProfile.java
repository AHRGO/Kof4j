package dev.kof.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JS face of `kof profile --methods`: run the emitted module under the
 * Node CPU profiler (`--cpu-prof`, part of Node — no external tool) and
 * aggregate the samples by function. The `.mjs.map` source map maps the
 * sampled JavaScript line back to the `.kf` source line, so the report shows
 * the hot Kof function, the JS counterpart of the JVM's LineNumberTable.
 *
 * <p>Node internals (`node:internal/...`) and the profiler's own synthetic
 * frames are filtered; a program too short for a sample, or a host without
 * Node, is an honest note (R6), never a silent empty list.
 */
final class JsMethodProfile {

    private static final String PROFILE_NAME = "kof-methods.cpuprofile";
    private static final int SAMPLE_INTERVAL_US = 200;

    private JsMethodProfile() {
    }

    /**
     * Runs the emitted module under the Node CPU profiler and returns the method
     * report fragment. Returns {@code null} when the program could not be run at
     * all (no Node on PATH, or a non-zero exit) — the caller treats that as a hard
     * failure; a run that produced no sample is an honest note in the report.
     */
    static Map<String, Object> sample(Path entry, Path outDir, List<String> programArgs) {
        Map<String, Object> report = new LinkedHashMap<>();
        Path profileFile = outDir.resolve(PROFILE_NAME);
        List<String> command = new ArrayList<>(List.of(
                "node",
                "--cpu-prof",
                "--cpu-prof-dir=" + outDir,
                "--cpu-prof-name=" + PROFILE_NAME,
                "--cpu-prof-interval=" + SAMPLE_INTERVAL_US,
                entry.toString()));
        command.addAll(programArgs);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int ec = p.waitFor();
            if (ec != 0) {
                System.err.println("kof profile: node exited with " + ec
                        + (out.isBlank() ? "" : ":\n" + out.strip()));
                return null;
            }
        } catch (IOException e) {
            System.err.println("kof profile --methods: node was not found — the JS method profile"
                    + " uses the Node CPU profiler (--cpu-prof); install Node to use it");
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("kof profile: node was interrupted");
            return null;
        }
        parse(profileFile, entry, report);
        return report;
    }

    private static void parse(Path profileFile, Path entry, Map<String, Object> report) {
        if (!Files.isRegularFile(profileFile)) {
            report.put("methods_unavailable", "the Node CPU profiler produced no recording");
            return;
        }
        Map<Integer, String[]> framesById = new LinkedHashMap<>();
        List<?> samples;
        try {
            Object parsed = Json.parse(Files.readString(profileFile, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> root)) {
                report.put("methods_unavailable", "the CPU profile could not be parsed");
                return;
            }
            Object nodes = root.get("nodes");
            if (nodes instanceof List<?> list) {
                for (Object node : list) {
                    if (!(node instanceof Map<?, ?> n)) continue;
                    Object id = n.get("id");
                    Object frame = n.get("callFrame");
                    if (!(id instanceof Number) || !(frame instanceof Map<?, ?> cf)) continue;
                    Object functionName = cf.get("functionName");
                    Object url = cf.get("url");
                    Object lineNumber = cf.get("lineNumber");
                    framesById.put(((Number) id).intValue(), new String[] {
                            functionName == null ? "" : String.valueOf(functionName),
                            url == null ? "" : String.valueOf(url),
                            lineNumber == null ? "-1" : String.valueOf(lineNumber) });
                }
            }
            Object s = root.get("samples");
            if (!(s instanceof List<?> list)) {
                report.put("methods_unavailable", "the CPU profile carries no samples");
                return;
            }
            samples = list;
        } catch (IOException | IllegalArgumentException e) {
            report.put("methods_unavailable", "the CPU profile could not be read: " + e.getMessage());
            return;
        }

        JsSourceMap sourceMap = readSourceMap(entry);
        String entryName = entry.getFileName().toString();
        Map<String, Integer> byMethod = new LinkedHashMap<>();
        Map<String, Integer> lineByMethod = new LinkedHashMap<>();
        int total = 0;
        for (Object sample : samples) {
            if (!(sample instanceof Number id)) continue;
            String[] frame = framesById.get(id.intValue());
            if (frame == null) continue;
            String name = frame[0];
            String url = frame[1];
            if (name.isEmpty() || name.startsWith("(") || url.isEmpty() || url.startsWith("node:")) {
                continue; // synthetic frames and Node internals are not the program's cost
            }
            int line = -1;
            String method;
            if (url.endsWith(entryName)) {
                method = name;
                line = sourceMap == null ? -1 : sourceMap.toSourceLine(parseInt(frame[2]));
            } else {
                method = name + " (" + Path.of(url).getFileName() + ")";
            }
            byMethod.merge(method, 1, Integer::sum);
            lineByMethod.merge(method, line, (old, now) -> old > 0 ? old : now);
            total++;
        }
        if (total == 0) {
            report.put("methods_unavailable", "the program was too short for the Node profiler to sample");
            return;
        }
        List<Profile.MethodSample> top = byMethod.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> new Profile.MethodSample(e.getKey(), e.getValue(),
                        lineByMethod.getOrDefault(e.getKey(), -1)))
                .toList();
        report.put("samples_total", total);
        report.put("methods", top);
    }

    private static JsSourceMap readSourceMap(Path entry) {
        Path mapFile = Path.of(entry + ".map");
        if (!Files.isRegularFile(mapFile)) return null;
        try {
            return JsSourceMap.read(mapFile);
        } catch (IOException e) {
            return null;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
