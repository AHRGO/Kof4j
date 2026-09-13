package dev.kof.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * `kof translate` — translate a subset of Java source into idiomatic Kof
 * (docs/development/TRANSLATOR.md, Fase F).
 *
 * Understands structure (classes, fields, methods, control flow) rather than
 * doing textual substitution. Static methods become top-level functions;
 * {@code System.out.println} becomes {@code println}; {@code a.equals(b)}
 * becomes {@code a == b}; {@code new X(...)} drops {@code new}.
 */
public final class Translate {

    private Translate() {
    }

    public static int run(String[] args) {
        if (args.length > 0 && "translate".equals(args[0])) {
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        if (args.length == 0) {
            System.err.println("usage: kof translate <file.java> [--output <file.kf>]");
            return 1;
        }
        Path file = Path.of(args[0]);
        String outArg = optionValue(args, "--output");
        Path outFile = outArg != null ? Path.of(outArg) : null;
        if (!Files.isRegularFile(file)) {
            System.err.println("file not found: " + file);
            return 1;
        }
        try {
            String kof = translateJava(Files.readString(file));
            if (outFile != null) {
                Files.writeString(outFile, kof);
                System.out.println("translated to " + outFile);
            } else {
                System.out.print(kof);
            }
            return 0;
        } catch (TranslateException e) {
            System.err.println("kof translate: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            System.err.println("kof translate: " + e.getMessage());
            return 1;
        }
    }

    // ── public API for tests ──────────────────────────────────────────────

    static String translateJava(String source) {
        List<Tok> toks = TranslateLexer.lex(source);
        Parser p = new Parser(toks);
        return new Emitter(p).translate();
    }

    static final class Emitter extends TranslateStatements {
        final StringBuilder out = new StringBuilder();
        final StringBuilder topFns = new StringBuilder();

        Emitter(Parser p) { super(p); }

        String translate() {
            // Skip package / imports.
            while (p.at("package")) {
                while (!p.at(";")) p.next();
                p.next(); // ;
            }
            while (p.at("import")) {
                while (!p.at(";")) p.next();
                p.next();
            }
            // Parse all top-level type declarations.
            while (!p.at(T.EOF)) {
                parseTypeDeclaration();
            }
            // Static methods were collected as top-level functions; emit them first.
            out.insert(0, topFns);
            return out.toString();
        }

        private void parseTypeDeclaration() {
            // modifiers
            while (isModifier(p.peek().text)) p.next();
            if (p.at("class")) {
                parseClass();
            } else if (p.at("interface")) {
                parseInterface();
            } else if (p.at("record")) {
                parseRecord();
            } else if (p.at("enum")) {
                parseEnum();
            } else {
                throw new TranslateException("expected class/interface/record/enum, found '" + p.peek().text + "'");
            }
        }

        private void parseEnum() {
            p.expect("enum");
            String name = p.next().text;
            List<String> constants = new ArrayList<>();
            p.expect("{");
            while (!p.at("}") && !p.at(";")) {
                constants.add(p.next().text);
                if (p.at("(")) { p.next(); while (!p.at(")")) p.next(); p.next(); }  // args ignorados (MVP)
                if (p.at("{")) skipBlock();                                          // corpo de constante ignorado
                if (p.at(",")) p.next();
            }
            if (p.at(";")) { p.next(); while (!p.at("}")) skipBlock(); }             // métodos/campos ignorados
            p.expect("}");
            out.append("enum ").append(name).append(" { ")
               .append(String.join(", ", constants)).append(" }\n");
        }

        private void parseRecord() {
            p.expect("record");
            String name = p.next().text;
            List<String> components = new ArrayList<>();
            if (p.at("(")) {
                p.next();
                while (!p.at(")")) {
                    String type = kofType(p.next().text);
                    String cname = p.next().text;
                    components.add(type + " " + cname);
                    if (p.at(",")) p.next();
                }
                p.expect(")");
            }
            if (p.at("{")) skipBlock();
            else p.expect(";");
            out.append("record ").append(name).append('(')
               .append(String.join(", ", components)).append(")\n");
        }

        private void parseClass() {
            p.expect("class");
            String name = p.next().text;
            String superCls = null;
            List<String> ifaces = new ArrayList<>();
            if (p.at("extends")) { p.next(); superCls = p.next().text; }
            if (p.at("implements")) {
                p.next();
                while (!p.at("{")) { ifaces.add(p.next().text); if (p.at(",")) p.next(); }
            }
            p.expect("{");
            out.append("class ").append(kofType(name));
            if (superCls != null) out.append(" extends ").append(kofType(superCls));
            if (!ifaces.isEmpty()) {
                out.append(" implements ");
                out.append(ifaces.stream().map(Emitter::kofType).collect(java.util.stream.Collectors.joining(", ")));
            }
            out.append(" {\n");

            while (!p.at("}")) {
                parseMember(name);
            }
            p.expect("}");
            out.append("}\n");
        }

        private void parseInterface() {
            p.expect("interface");
            String name = p.next().text;
            p.expect("{");
            out.append("interface ").append(name).append(" {\n");
            while (!p.at("}")) {
                // method signature ending in ';'
                int save = p.pos;
                boolean isStatic = false;
                while (isModifier(p.peek().text)) { if (p.at("static")) isStatic = true; p.next(); }
                String ret = parseType();
                String mname = p.next().text;
                List<String> params = parseParams();
                if (p.at("{")) { skipBlock(); }
                else p.expect(";");
                out.append("    ").append(ret).append(' ').append(mname).append('(')
                   .append(paramList(params)).append("): ").append(ret).append('\n');
            }
            p.expect("}");
            out.append("}\n");
        }

        private void parseMember(String className) {
            int save = p.pos;
            boolean isStatic = false;
            while (isModifier(p.peek().text)) { if (p.at("static")) isStatic = true; p.next(); }
            if (p.at("{")) { // static initializer block — skip
                skipBlock();
                return;
            }
            if (p.at("class") || p.at("interface") || p.at("record") || p.at("enum")) {
                // Kof não suporta tipo aninhado (SEM042). Hoisting p/ o topo
                // exige renomear referências (`Outer.Inner` → `Inner`) —
                // transformação semântica, decisão de design → revisão manual
                // (R6), nunca parse error confuso.
                throw new TranslateException(
                        "tipo aninhado (`class`/`interface`/`record`/`enum` dentro de classe) "
                        + "não tem equivalente direto em Kof (SEM042; declare no top level) — revisão manual");
            }
            String typeName = parseType();
            String memberName = p.next().text;
            if (p.at("(")) {
                // method or constructor
                List<String> params = parseParams();
                boolean isConstructor = memberName.equals(className);
                // `throws E1, E2` — Kof não declara throws (exceções são
                // Strings, sempre propagáveis) → consumir e descartar.
                if (p.at("throws")) {
                    p.next();
                    while (!p.at("{") && !p.at(";")) p.next();
                }
                if (p.at(";")) { p.next(); return; } // abstract/native signature
                List<String> body = parseBlock();
                emitMethod(isStatic, isConstructor, typeName, memberName, params, body);
            } else {
                // field: "Type name [= expr];"
                String init = "";
                if (p.at("=")) {
                    p.next();
                    init = " = " + parseExpr();
                }
                while (!p.at(";")) p.next();
                p.next();
                if (isStatic) return; // static field → skip (no top-level state in Kof)
                out.append("    ").append(kofType(typeName)).append(' ').append(memberName).append(init).append('\n');
            }
        }

        private void emitMethod(boolean isStatic, boolean isConstructor, String retType,
                                String name, List<String> params, List<String> body) {
            StringBuilder sb = isStatic ? topFns : out;
            if (isConstructor) {
                sb.append("    constructor(").append(paramList(params)).append(") {}\n");
                return;
            }
            if (isStatic && name.equals("main")) {
                // Java main(String[] args) → top-level Kof main()
                sb.append("main() {\n");
                for (String stmt : body) sb.append("    ").append(stmt).append('\n');
                sb.append("}\n");
                return;
            }
            sb.append("    ").append(kofType(retType)).append(' ').append(name)
              .append('(').append(paramList(params)).append(')');
            if (body.size() == 1 && body.get(0).startsWith("return ")) {
                String expr = body.get(0).substring("return ".length());
                sb.append(" = ").append(expr).append('\n');
            } else {
                sb.append(" {\n");
                for (String stmt : body) sb.append("        ").append(stmt).append('\n');
                sb.append("    }\n");
            }
        }

    }

    private static String optionValue(String[] args, String opt) {
        for (int i = 0; i < args.length - 1; i++) {
            if (opt.equals(args[i])) return args[i + 1];
        }
        return null;
    }
}
