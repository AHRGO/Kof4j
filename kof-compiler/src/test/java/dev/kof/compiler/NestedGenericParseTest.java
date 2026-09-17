package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.*;

import dev.kof.compiler.Type.ClassType;
import dev.kof.compiler.Type.PrimitiveType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #373 — {@code Type.of()} parsed a generic's argument list by splitting on the
 * FIRST '>' and a naive comma split: any nested two-argument generic used as a
 * type argument (e.g. {@code List<Map<String,Int>>}) hit {@code substring(4, -1)}
 * → StringIndexOutOfBoundsException → COMP002 ICE (no bytecode, silent crash).
 * The parser now scans with angle/paren depth: {@code findMatchingAngle} locates
 * the matching '>' and {@code splitTopLevel} splits args only at depth 0, so the
 * inner {@code Map<String,Int>} survives intact and each argument recurses.
 */
class NestedGenericParseTest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws Exception {
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM exit code");
        return out;
    }

    @Test
    void nestedMapInsideListParsesToRealTypeArguments() {
        Type t = Type.of("List<Map<String, Int>>");
        assertInstanceOf(ClassType.class, t, "outer is a List class type");
        ClassType list = (ClassType) t;
        assertEquals("List", list.name());
        assertEquals(1, list.typeArguments().size(), "List<…> has one type argument");
        assertInstanceOf(ClassType.class, list.typeArguments().get(0), "the argument is a Map");
        ClassType map = (ClassType) list.typeArguments().get(0);
        assertEquals("Map", map.name());
        assertEquals(2, map.typeArguments().size(), "Map<…,…> keeps BOTH arguments (was lost to the naive split)");
        assertTrue(Type.isString(map.typeArguments().get(0)), "first arg is String");
        assertEquals(PrimitiveType.INT, map.typeArguments().get(1), "second arg is Int");
    }

    @Test
    void tripleNestedGenericsDoNotCrash() {
        // Q0: each of these hit substring(..,-1) / mis-split before the depth scan.
        Type deep = Type.of("Map<String, List<Map<Int, Bool>>>");
        assertInstanceOf(ClassType.class, deep);
        ClassType outer = (ClassType) deep;
        assertEquals(2, outer.typeArguments().size());
        ClassType mid = (ClassType) outer.typeArguments().get(1);
        assertEquals("List", mid.name());
        ClassType inner = (ClassType) mid.typeArguments().get(0);
        assertEquals("Map", inner.name());
        assertEquals(2, inner.typeArguments().size(), "innermost Map keeps both args");
    }

    @Test
    void nestedGenericProgramCompilesAndRuns(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    val x: List<Map<String, Int>> = listOf()
                    println(x.size())
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "must not ICE COMP002: " + result.diagnostics().getDiagnostics());
        assertEquals("0", runJvm(tempDir.resolve("out")), "empty nested-generic list has size 0");
    }
}
