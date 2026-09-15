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
 * opção B) o handler chain do Native x86 é PER-THREAD (TLS) e o trampolim do
 * spawn instala handler próprio: o {@code throw} de um worker marca o handle
 * como excepcional e {@code await}/{@code selectAny} relançam no consumidor —
 * x86 entrega o núcleo. riscv/aarch seguem {@code OTP001} (clone cru sem TLS)
 * e JS {@code OTP002} (§132 event-loop single-thread: task spawned de dentro
 * de outra task não dispara). Nesses casos o diagnóstico é claro — NUNCA
 * fallback silencioso. JVM/ANDROID (JvmBackend), Script (interpretador) e
 * Native x86 entregam o núcleo.
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
        // §129 (DECISIONS §2, opção B) FECHADO no x86: o handler chain é TLS
        // (per-thread) e o trampolim do spawn instala handler próprio — um
        // throw em worker marca o handle como excepcional e await/selectAny
        // relança. riscv/aarch seguem OTP001 (clone cru sem TLS + selectAny
        // ausente/CONC001).
        if (driver.target == Target.NATIVE_RISCV64 || driver.target == Target.NATIVE_AARCH64) {
            diagnostics.error(driver.currentSourceName, 0, 0, 0,
                    "kof.supervisor no target " + driver.target + ": o laço de "
                            + "supervisao usa 'try { await } catch' sobre tasks que "
                            + "falham, e no cross riscv/aarch o throw em task "
                            + "longjmpa no handler chain GLOBAL da thread main "
                            + "(crash/hang — known-bugs §129, x86 corrigido). "
                            + "Nucleo OTP disponivel em JVM, Script e Native x86.",
                    "OTP001");
            return null;
        }
        if (driver.target == Target.JS) {
            diagnostics.error(driver.currentSourceName, 0, 0, 0,
                    "kof.supervisor no target js: o backend JS roda num event-loop "
                            + "single-thread e uma task spawnada de dentro de outra "
                            + "task nao e agendada sem ceder (o worker nunca roda — "
                            + "known-bugs §132). Nucleo OTP disponivel em JVM e "
                            + "Script (kof run --target script).",
                    "OTP002");
            return null;
        }
        try (var in = CompilerDriver.class.getResourceAsStream("/dev/kof/supervisor-host.kf")) {
            if (in == null) {
                diagnostics.error("", 0, 0, 0,
                        "supervisor host resource /dev/kof/supervisor-host.kf ausente", "PKG003");
                return null;
            }
            String hostSource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(hostSource, "supervisor-host.kf", silent);
            Parser parser = new Parser(lexer.tokenize(), silent, "supervisor-host.kf");
            CompilationUnitNode hostUnit = parser.parse();
            if (silent.hasErrors() || hostUnit == null) {
                for (Diagnostic d : silent.getDiagnostics()) diagnostics.report(d);
                diagnostics.error("", 0, 0, 0, "supervisor host nao parseou", "PKG003");
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
