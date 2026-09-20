package dev.kof.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * Minimal source-map reader for the emitted KofJS module: maps a generated
 * `.mjs` line (what the V8 CPU profiler reports) back to the `.kf` source line.
 * This is the JS counterpart of the JVM LineNumberTable — without it the
 * `node --cpu-prof` face would show generated JavaScript lines, not Kof.
 *
 * <p>Only the line channel is decoded (the profiler samples per line); the
 * mappings' VLQ segments are read in order and the first source line recorded
 * for each generated line wins. A generated line with no segment of its own
 * falls back to the nearest preceding mapped line, which is the standard
 * source-map lookup behavior.
 */
final class JsSourceMap {

    private static final String BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private final TreeMap<Integer, Integer> sourceLineByGenerated = new TreeMap<>();

    private JsSourceMap() {
    }

    static JsSourceMap read(Path mapFile) throws IOException {
        JsSourceMap map = new JsSourceMap();
        Object parsed = Json.parse(Files.readString(mapFile, StandardCharsets.UTF_8));
        if (!(parsed instanceof Map<?, ?> root)) return map;
        Object mappings = root.get("mappings");
        if (mappings instanceof String text) map.decode(text);
        return map;
    }

    /** Kof source line (1-based) for a 0-based generated line, or -1 when unknown. */
    int toSourceLine(int generatedLine0) {
        Map.Entry<Integer, Integer> floor = sourceLineByGenerated.floorEntry(generatedLine0);
        return floor == null ? -1 : floor.getValue() + 1;
    }

    private void decode(String mappings) {
        int generatedLine = 0;
        int sourceLine = 0;
        for (String group : mappings.split(";", -1)) {
            for (String segment : group.split(",")) {
                if (segment.isEmpty()) continue;
                int[] values = decodeSegment(segment);
                if (values.length >= 3) {
                    sourceLine += values[2];
                    sourceLineByGenerated.putIfAbsent(generatedLine, sourceLine);
                }
            }
            generatedLine++;
        }
    }

    private static int[] decodeSegment(String segment) {
        int[] values = new int[segment.length()];
        int count = 0;
        int shift = 0;
        int current = 0;
        for (int i = 0; i < segment.length(); i++) {
            int digit = BASE64.indexOf(segment.charAt(i));
            if (digit < 0) return new int[0];
            current |= (digit & 31) << shift;
            if ((digit & 32) != 0) {
                shift += 5;
            } else {
                int magnitude = current >> 1;
                values[count++] = (current & 1) != 0 ? -magnitude : magnitude;
                current = 0;
                shift = 0;
            }
        }
        int[] out = new int[count];
        System.arraycopy(values, 0, out, 0, count);
        return out;
    }
}
