package dev.kof.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DiagnosticCollector {

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    void report(Diagnostic d) {
        diagnostics.add(d);
    }

    public void error(String file, int line, int column, int length, String message, String code) {
        report(Diagnostic.error(file, line, column, length, message, code));
    }

    public void error(AstNode node, String message, String code) {
        SourcePosition pos = node == null ? null : node.position();
        if (pos == null) {
            report(Diagnostic.error("", 0, 0, 0, message, code));
        } else {
            report(Diagnostic.error(pos.file() == null ? "" : pos.file(), pos.line(), pos.column(),
                    pos.length(), message, code));
        }
    }

    void warning(String file, int line, int column, int length, String message, String code) {
        report(Diagnostic.warning(file, line, column, length, message, code));
    }

    public List<Diagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }

    int errorCount() {
        return (int) diagnostics.stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).count();
    }

    String formatAll() {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : diagnostics) {
            sb.append(d.format()).append('\n');
        }
        return sb.toString();
    }
}
