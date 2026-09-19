package dev.kof.compiler;

import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pacote virtual {@code kof.workflow} (tracker 2.1, plano
 * {@code docs/development/workflow-plan.md} §5 2.1.2). O host é escrito EM KOF
 * ({@code dev/kof/workflow-host.kf} no resource) e injetado FLAT no
 * {@code import kof.workflow} EXPLÍCITO — exatamente o mecanismo do
 * {@code kof.supervisor} (DD-OTP-01 opção A), portanto SEM o prefixo
 * {@code workflow.} do sketch §2: a superfície assinada Q1 é stdlib puro-Kof,
 * e em stdlib puro-Kof de host injetado o idioma real é o flat (supervisor é
 * plano: {@code supervisor("t")}, nunca {@code kof.supervisor.spawn}).
 *
 * <p>Sem gate de target: a execução MVP é sequencial (laço fixpoint em Kof
 * puro, sem threads/await/process), então o próprio host roda em JVM, ANDROID,
 * Script, JS e Native — honestidade {@code PROC001}/{@code CRON001}/{@code
 * ORM001} só aparece quando o CORPO do job chama essas primitivas, não na
 * camada de composição. Superfície: job/dag/after/run/Report (Q2 mínimo);
 * retry/checkpoint/deadLetter chegam no bundle 2.1.3.
 */
final class CompilerWorkflow {

    private CompilerWorkflow() {}

    static CompilationUnitNode injectHostIfNeeded(CompilerDriver driver,
                                                  CompilationUnitNode unit,
                                                  DiagnosticCollector diagnostics) {
        boolean wantsHost = false;
        for (String imp : unit.imports()) {
            String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
            if ("kof.workflow".equals(base)) { wantsHost = true; break; }
        }
        if (!wantsHost) return unit;
        // usuário definiu o próprio host: não injeta (o import vira PKG006
        // real — nome colidindo é sinal, não silêncio).
        boolean collision = unit.declarations().stream()
                .anyMatch(d -> d instanceof TypeDeclarationNode t
                        && ("KofWfJob".equals(t.name()) || "KofWfDag".equals(t.name())
                                || "KofWfReport".equals(t.name())));
        if (collision) return unit;
        try (var in = CompilerDriver.class.getResourceAsStream("/dev/kof/workflow-host.kf")) {
            if (in == null) {
                diagnostics.error("", 0, 0, 0,
                        "workflow host resource /dev/kof/workflow-host.kf missing", "PKG003");
                return null;
            }
            String hostSource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(hostSource, "workflow-host.kf", silent);
            Parser parser = new Parser(lexer.tokenize(), silent, "workflow-host.kf");
            CompilationUnitNode hostUnit = parser.parse();
            if (silent.hasErrors() || hostUnit == null) {
                for (Diagnostic d : silent.getDiagnostics()) diagnostics.report(d);
                diagnostics.error("", 0, 0, 0, "workflow host did not parse", "PKG003");
                return null;
            }
            List<String> imports = new ArrayList<>();
            for (String imp : unit.imports()) {
                String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
                if ("kof.workflow".equals(base)) continue; // virtual — resolvido aqui
                if (!imports.contains(imp)) imports.add(imp);
            }
            List<AstNode> decls = new ArrayList<>(unit.declarations());
            for (AstNode d : hostUnit.declarations()) {
                driver.declarationPackages.put(d, "");
                decls.add(d);
            }
            // fatia schedule (bundle 2.1.3): separada do host principal
            // porque o gate CRON001 do scheduler.at é estático — no NATIVE
            // entra o STUB (throw CRON001 em runtime, R6), nunca a
            // delegação (que derrubaria o host INTEIRO na recusa).
            if (!driver.target.isNative()) {
                mergeSchedHost(driver, unit, decls, diagnostics, "/dev/kof/workflow-sched-host.kf");
            } else {
                mergeSchedHost(driver, unit, decls, diagnostics, "/dev/kof/workflow-sched-host.native.kf");
            }
            return new CompilationUnitNode(unit.position(), unit.packageName(), imports, decls);
        } catch (IOException e) {
            diagnostics.error("", 0, 0, 0,
                    "workflow host could not be loaded: " + e.getMessage(), "PKG003");
            return null;
        }
    }

    private static void mergeSchedHost(CompilerDriver driver,
                                       CompilationUnitNode unit,
                                       List<AstNode> decls,
                                       DiagnosticCollector diagnostics,
                                       String resource) {
        try (var in = CompilerDriver.class.getResourceAsStream(resource)) {
            if (in == null) {
                diagnostics.error("", 0, 0, 0,
                        "workflow sched host resource " + resource + " missing", "PKG003");
                return;
            }
            String schedSource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(schedSource, "workflow-sched-host.kf", silent);
            Parser parser = new Parser(lexer.tokenize(), silent, "workflow-sched-host.kf");
            CompilationUnitNode schedUnit = parser.parse();
            if (silent.hasErrors() || schedUnit == null) {
                for (Diagnostic d : silent.getDiagnostics()) diagnostics.report(d);
                diagnostics.error("", 0, 0, 0, "workflow sched host did not parse", "PKG003");
                return;
            }
            for (AstNode d : schedUnit.declarations()) {
                driver.declarationPackages.put(d, "");
                decls.add(d);
            }
        } catch (IOException e) {
            diagnostics.error("", 0, 0, 0,
                    "workflow sched host could not be loaded: " + e.getMessage(), "PKG003");
        }
    }
}
