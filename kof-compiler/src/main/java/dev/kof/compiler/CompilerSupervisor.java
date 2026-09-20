package dev.kof.compiler;

import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pacote virtual {@code kof.supervisor} (issue #83, OTP núcleo). O host é
 * escrito EM KOF ({@code dev/kof/supervisor-host.kf} no resource), tal como o
 * {@code android-host}: quem o compila é o próprio compilador. O gatilho é o
 * {@code import kof.supervisor} EXPLÍCITO — livre de falso-positivo (não é
 * heurística de texto). Ao importar: injeta as declarações flat (mesmo
 * mecanismo do android-host) e remove o import virtual (senão o
 * {@link CompilerImports#expandKofImports} reclamar PKG006 — não há diretório).
 *
 * <p>Paridade honesta (regra 6 / R6): o núcleo observa falha de worker via
 * {@code try { await h } catch} no laço por filho. Desde §129 (DECISIONS §2,
 * opção B) o handler chain do Native é PER-THREAD e o trampolim do spawn
 * instala handler próprio: o {@code throw} de um worker marca o handle como
 * excepcional e {@code await}/{@code selectAny} relançam no consumidor. No x86
 * a cadeia é TLS local-exec; em riscv/aarch (port 19/09) é uma tabela por-TID
 * {@code kof_exc_slots} — o TLS-via-{@code tp} foi provado ABI-inseguro (quebra
 * a TLS da libc; ver known-bugs §129 adendo 19/09). Com isso o núcleo é
 * entregue nos 4 targets. JS entrega desde 18/09 (§132 resolvido:
 * {@code time.sleep} é ponto de await cooperativo; o gate {@code OTP002} foi
 * levantado). JVM/ANDROID (JvmBackend), Script (interpretador), Native x86,
 * riscv64, aarch64 e JS entregam o núcleo.
 */
final class CompilerSupervisor {

    private CompilerSupervisor() {}

    static CompilationUnitNode injectHostIfNeeded(CompilerDriver driver,
                                                  CompilationUnitNode unit,
                                                  DiagnosticCollector diagnostics) {
        boolean wantsHost = false;
        for (String imp : unit.imports()) {
            String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
            if ("kof.supervisor".equals(base)) { wantsHost = true; break; }
        }
        if (!wantsHost) return unit;
        // usuário definiu o próprio Supervisor/KofWorker: não injeta (deixa o
        // import virar PKG006 real — o nome colidindo é sinal, não silêncio).
        boolean collision = unit.declarations().stream()
                .anyMatch(d -> d instanceof TypeDeclarationNode t
                        && ("Supervisor".equals(t.name()) || "KofWorker".equals(t.name())
                                || "KofWorkerFactory".equals(t.name())));
        if (collision) return unit;
        // §129 (DECISIONS §2, opção B) FECHADO nos 4 targets: o handler chain é
        // per-thread (TLS local-exec no x86; tabela por-TID kof_exc_slots em
        // riscv/aarch, port 19/09) e o trampolim do spawn instala handler
        // próprio — um throw em worker marca o handle como excepcional e
        // await/selectAny relançam no consumidor. Sem gate OTP001.
        try (var in = CompilerDriver.class.getResourceAsStream("/dev/kof/supervisor-host.kf")) {
            if (in == null) {
                diagnostics.error("", 0, 0, 0,
                        "supervisor host resource /dev/kof/supervisor-host.kf missing", "PKG003");
                return null;
            }
            String hostSource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(hostSource, "supervisor-host.kf", silent);
            Parser parser = new Parser(lexer.tokenize(), silent, "supervisor-host.kf");
            CompilationUnitNode hostUnit = parser.parse();
            if (silent.hasErrors() || hostUnit == null) {
                for (Diagnostic d : silent.getDiagnostics()) diagnostics.report(d);
                diagnostics.error("", 0, 0, 0, "supervisor host did not parse", "PKG003");
                return null;
            }
            List<String> imports = new ArrayList<>();
            for (String imp : unit.imports()) {
                String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
                if ("kof.supervisor".equals(base)) continue; // virtual — resolvido aqui
                if (!imports.contains(imp)) imports.add(imp);
            }
            List<AstNode> decls = new ArrayList<>(unit.declarations());
            for (AstNode d : hostUnit.declarations()) {
                driver.declarationPackages.put(d, "");
                decls.add(d);
            }
            return new CompilationUnitNode(unit.position(), unit.packageName(), imports, decls);
        } catch (IOException e) {
            diagnostics.error("", 0, 0, 0,
                    "supervisor host could not be loaded: " + e.getMessage(), "PKG003");
            return null;
        }
    }
}
