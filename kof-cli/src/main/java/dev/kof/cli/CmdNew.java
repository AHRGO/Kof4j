package dev.kof.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * D-APP I1 (DECISIONS.md §D-APP, ratificado 13/09): {@code kof new} —
 * esqueletos de projeto por tipo, nascendo compiláveis.
 *
 * <pre>
 *   kof new &lt;dir&gt;                     → monólito (igual a init)
 *   kof new &lt;dir&gt; --type backend      → API web (src/Main.kf + kof.toml [backend])
 *   kof new &lt;dir&gt; --type frontend     → KofJS (src/web/Index.kf + kof.toml [frontend])
 *   kof new &lt;dir&gt; --type full-stack   → backend + frontend + estático (D-SPRING F11)
 * </pre>
 *
 * APP003 (R6, gap honesto): tipo desconhecido ou kof.toml já existente →
 * diagnóstico explícito e exit != 0 — nunca sobrescreve, nunca silencia.
 * O monólito sem flags mantém o comportamento do {@code kof init}
 * (retrocompatibilidade); {@code init} continua válido.
 */
public final class CmdNew {

    private CmdNew() {}

    public static int run(String[] args) {
        String dirName = null;
        String type = "mono";
        boolean first = true;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (first) {
                // convenção do Main: o array vem completo, args[0] = "new"
                first = false;
                if (a.equals("new")) continue;
            }
            if (a.startsWith("--type=")) {
                type = a.substring("--type=".length());
            } else if (a.equals("--type") && i + 1 < args.length) {
                type = args[++i];
            } else if (a.startsWith("-")) {
                System.err.println("kof new: unknown flag '" + a + "' [APP003]"
                        + "\n  types: mono | backend | frontend | full-stack");
                return 1;
            } else if (dirName == null) {
                dirName = a;
            } else {
                System.err.println("kof new: argumento extra '" + a + "' [APP003]");
                return 1;
            }
        }
        if (dirName == null) {
            System.err.println("usage: kof new <dir> [--type mono|backend|frontend|full-stack]");
            return 1;
        }
        Path dir = Path.of(dirName);
        Path manifest = dir.resolve("kof.toml");
        try {
            if (Files.exists(manifest)) {
                System.err.println("kof new: " + manifest + " already exists (project already initialized) [APP003]");
                return 1;
            }
            Files.createDirectories(dir);
            switch (type) {
                case "mono" -> writeMono(dir);
                case "backend" -> writeBackend(dir);
                case "frontend" -> writeFrontend(dir);
                case "full-stack" -> writeFullStack(dir);
                default -> {
                    System.err.println("kof new: tipo '" + type + "' does not exist [APP003]"
                            + "\n  types: mono | backend | frontend | full-stack");
                    return 1;
                }
            }
            System.out.println("created " + type + " at " + dir.toAbsolutePath().normalize()
                    + "\nnext steps:\n  kof build " + dirName);
            return 0;
        } catch (IOException e) {
            System.err.println("kof new: " + e.getMessage() + " [APP003]");
            return 1;
        }
    }

    private static void writeMono(Path dir) throws IOException {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.createDirectories(dir.resolve("tests"));
        Files.writeString(dir.resolve("kof.toml"), """
                [project]
                name = "%s"
                """.formatted(dir.getFileName()));
        Files.writeString(src.resolve("Main.kf"), """
                // Projeto Kof — rode com: kof run src/Main.kf
                main() {
                    println("Hello, Kof!")
                }
                """);
        Files.writeString(dir.resolve("tests/smoke.kf"), """
                main() {
                    assert(1 + 1 == 2)
                    println("smoke ok")
                }
                """);
        gitignore(dir);
    }

    private static void writeBackend(Path dir) throws IOException {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(dir.resolve("kof.toml"), """
                [project]
                name = "%s"

                [backend]
                target = "jvm"

                [server]
                port = 8080
                """.formatted(dir.getFileName()));
        // Mesmo padrão do exemplo canônico examples/fullstack/src/Main.kf —
        // nasce com a rota de healthcheck e os serveDir condicionais (o build
        // injeta KOF_WEB_OUT/KOF_STATIC_OUT quando houver frontend/estático).
        Files.writeString(src.resolve("Main.kf"), """
                main() {
                    var app = web.app()
                    app.get("/api/ping") {
                        return "{\\"pong\\": true}"
                    }
                    app.listen(config.int("server.port", 8080))
                }
                """);
        Files.createDirectories(dir.resolve("tests"));
        Files.writeString(dir.resolve("tests/smoke.kf"), """
                main() {
                    assert(1 + 1 == 2)
                    println("smoke ok")
                }
                """);
        gitignore(dir);
    }

    private static void writeFrontend(Path dir) throws IOException {
        Files.createDirectories(dir.resolve("src/web"));
        Files.writeString(dir.resolve("kof.toml"), """
                [project]
                name = "%s"

                [frontend]
                target = "kofjs"
                """.formatted(dir.getFileName()));
        Files.writeString(dir.resolve("src/web/Index.kf"), """
                main() {
                    println("hello from the frontend")
                }
                """);
        gitignore(dir);
    }

    private static void writeFullStack(Path dir) throws IOException {
        Path src = dir.resolve("src");
        Files.createDirectories(src.resolve("web"));
        Files.createDirectories(src.resolve("static"));
        Files.writeString(dir.resolve("kof.toml"), """
                [project]
                name = "%s"

                [backend]
                target = "jvm"

                [frontend]
                target = "kofjs"

                [server]
                port = 8080
                """.formatted(dir.getFileName()));
        Files.writeString(src.resolve("Main.kf"), """
                main() {
                    var app = web.app()
                    app.get("/api/ping") {
                        return "{\\"pong\\": true}"
                    }
                    var webOut = config.env("KOF_WEB_OUT")
                    if (webOut != null) {
                        app.serveDir("/", webOut)
                    }
                    var staticOut = config.env("KOF_STATIC_OUT")
                    if (staticOut != null) {
                        app.serveDir("/static", staticOut)
                    }
                    app.listen(config.int("server.port", 8080))
                }
                """);
        Files.writeString(src.resolve("web/Index.kf"), """
                main() {
                    println("hello from the frontend")
                }
                """);
        Files.writeString(src.resolve("static/app.css"), "/* estilos do app */\n");
        gitignore(dir);
    }

    private static void gitignore(Path dir) throws IOException {
        Files.writeString(dir.resolve(".gitignore"), "target/\n*.log\n");
    }
}
