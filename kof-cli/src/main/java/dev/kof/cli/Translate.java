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
        /** Campos `static` da classe em parse — p/ qualificar em funções hoisted. */
        private final java.util.Set<String> staticFields = new java.util.HashSet<>();

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
            // modifiers + annotations (@Override, @SuppressWarnings(...)) —
            // Kof ignora anotações no translator (não são semântica p/ o
            // subconjunto; a anotação some junto com o `@Nome` e args).
            skipAnnotationsAndModifiers();
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

        /**
         * Consome modificadores e anotações Java (`@Override`,
         * `@SuppressWarnings("x")`, `@Deprecated(...)`). Anotações não têm
         * semântica no subconjunto traduzido → descartadas (Kof as ignora,
         * verificado 13/09).
         */
        private void skipAnnotationsAndModifiers() {
            while (true) {
                if (isModifier(p.peek().text)) { p.next(); continue; }
                if (p.at(T.AT)) {
                    p.next();                 // @
                    p.next();                 // Nome
                    while (p.at(".")) { p.next(); p.next(); }  // @a.b.C
                    if (p.at("(")) {          // args
                        int depth = 0;
                        do {
                            if (p.at("(")) depth++;
                            else if (p.at(")")) depth--;
                            p.next();
                        } while (depth > 0 && !p.at(T.EOF));
                    }
                    continue;
                }
                break;
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
            if (p.at(";")) {
                p.next();
                // Corpo do enum (métodos/campos) — Kof enum não tem corpo
                // (só constantes). Pular tokens balanceados até o `}`.
                while (!p.at("}") && !p.at(T.EOF)) {
                    if (p.at("{")) skipBlock();
                    else p.next();
                }
            }
            p.expect("}");
            out.append("enum ").append(name).append(" { ")
               .append(String.join(", ", constants)).append(" }\n");
        }

        private void parseRecord() {
            p.expect("record");            String name = p.next().text;
            String typeParams = p.at("<") ? parseTypeParams() : "";
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
            out.append("record ").append(name).append(typeParams).append('(')
               .append(String.join(", ", components)).append(")\n");
        }

        private void parseClass() {
            p.expect("class");
            String name = p.next().text;
            String typeParams = p.at("<") ? parseTypeParams() : "";
            String superCls = null;
            List<String> ifaces = new ArrayList<>();
            if (p.at("extends")) { p.next(); superCls = p.next().text; }
            if (p.at("implements")) {
                p.next();
                while (!p.at("{")) { ifaces.add(p.next().text); if (p.at(",")) p.next(); }
            }
            p.expect("{");
            // Pré-varre os campos `static` (p/ qualificar refs nas funções
            // hoisted — `X` → `Classe.X`; ver TranslateStatics).
            staticFields.clear();
            staticFields.addAll(TranslateStatics.scanFieldNames(p, p.pos));
            out.append("class ").append(kofType(name)).append(typeParams);
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
            String typeParams = p.at("<") ? parseTypeParams() : "";
            List<String> ext = new ArrayList<>();
            if (p.at("extends")) {
                // Kof interface suporta `extends A, B` (verificado 13/09).
                p.next();
                ext.add(p.next().text);
                while (p.at(",")) { p.next(); ext.add(p.next().text); }
            }
            p.expect("{");
            out.append("interface ").append(name).append(typeParams);
            if (!ext.isEmpty()) {
                out.append(" extends ").append(String.join(", ", ext));
            }
            out.append(" {\n");
            while (!p.at("}")) {
                // method signature ending in ';'
                int save = p.pos;
                boolean isStatic = false;
                while (isModifier(p.peek().text)) { if (p.at("static")) isStatic = true; p.next(); }
                String ret = parseType();
                String mname = p.next().text;
                if (!p.at("(")) {
                    // Campo/constante de interface Java (`int X = 1;` é
                    // implicitamente `static final`). Kof aceita declarar, mas
                    // não resolve o campo (`I.X` → SEM025, verificado 13/09) e
                    // não há constante top-level → revisão manual (R6).
                    throw new TranslateException(
                            "constante de interface (`Type NOME = ...` em interface) não tem "
                            + "equivalente direto em Kof (campo de interface não é resolvível, "
                            + "SEM025) — revisão manual");
                }
                List<String> params = parseParams();
                if (p.at("{")) {
                    // Método de interface COM corpo = `default` (ou `static`)
                    // method Java. Kof NÃO tem default method: o corpo é
                    // IGNORADO e o implementador falha com SEM043
                    // (re-verificado 13/09 — a nota anterior "Kof aceita corpo"
                    // era verificação falsa). Emitir o corpo seria um Kof que
                    // não compila; dropá-lo silenciosamente muda o
                    // comportamento → gap honesto (R6).
                    throw new TranslateException(
                            "método de interface com corpo (`default`/`static` method) não tem "
                            + "equivalente direto em Kof (sem default method; o implementador "
                            + "falharia com SEM043) — mova o corpo para a classe — revisão manual");
                }
                p.expect(";");
                out.append("    ").append(ret).append(' ').append(mname).append('(')
                   .append(paramList(params)).append("): ").append(ret).append('\n');
            }
            p.expect("}");
            out.append("}\n");
        }

        /**
         * Tipo genérico Java `<T>` / `<K, V>` → Kof `<T>` / `<K, V>`.
         * Bounds (`<T extends X>`) não têm equivalente direto → revisão
         * manual (R6).
         */
        private String parseTypeParams() {
            p.expect("<");
            List<String> tps = new ArrayList<>();
            while (!p.at(">") && !p.at(T.EOF)) {
                String tp = p.next().text;
                if (p.at("extends")) {
                    throw new TranslateException(
                            "type parameter com bound (`<T extends X>`) não tem equivalente "
                            + "direto em Kof — revisão manual");
                }
                tps.add(tp);
                if (p.at(",")) p.next();
            }
            p.expect(">");
            return "<" + String.join(", ", tps) + ">";
        }

        private void parseMember(String className) {
            int save = p.pos;
            boolean isStatic = false;
            while (true) {
                if (isModifier(p.peek().text)) { if (p.at("static")) isStatic = true; p.next(); continue; }
                if (p.at(T.AT)) {
                    p.next(); p.next();
                    while (p.at(".")) { p.next(); p.next(); }
                    if (p.at("(")) {
                        int depth = 0;
                        do { if (p.at("(")) depth++; else if (p.at(")")) depth--; p.next(); }
                        while (depth > 0 && !p.at(T.EOF));
                    }
                    continue;
                }
                break;
            }
            if (p.at("{")) {
                // Bloco de inicialização. `static {}`: skip (Kof não tem estado
                // top-level; consistente com campo estático skipado). Bloco de
                // INSTÂNCIA `{ ... }` (não-static): roda antes do construtor e
                // tem efeito — dropá-lo SILENCIOSAMENTE muda o comportamento
                // (bug latente Q4 13/09) → gap honesto R6.
                if (!isStatic) {
                    throw new TranslateException(
                            "bloco de inicialização de instância `{ ... }` (não-static) roda antes "
                            + "do construtor — sem equivalente direto em Kof; mova o corpo para o "
                            + "`constructor(...)` — revisão manual");
                }
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
            // Construtor Java: `[mods] ClassName ( params ) { body }` — sem tipo
            // de retorno. Precisa ser detectado ANTES de parseType, senão o nome
            // da classe vira "tipo" e o `(` vira "nome do membro" → o ramo de
            // campo escaneia até o `;` (que não existe) e trava no EOF
            // (bug latente achado 13/09: loop infinito em `kof translate`).
            if (p.peek().text.equals(className) && p.peek(1).text.equals("(")) {
                p.next(); // nome da classe
                List<String> params = parseParams();
                if (p.at("throws")) {
                    p.next();
                    while (!p.at("{") && !p.at(";") && !p.at(T.EOF)) p.next();
                }
                if (p.at(";")) { p.next(); return; }
                List<String> body = parseBlock();
                emitConstructor(params, body);
                return;
            }
            String typeParams = "";
            if (p.at("<")) {
                typeParams = parseTypeParams();
            }
            String typeName = parseType();
            String memberName = p.next().text;
            if (p.at("(")) {
                // method
                List<String> params = parseParams();
                // `throws E1, E2` — Kof não declara throws (exceções são
                // Strings, sempre propagáveis) → consumir e descartar.
                if (p.at("throws")) {
                    p.next();
                    while (!p.at("{") && !p.at(";") && !p.at(T.EOF)) p.next();
                }
                if (p.at(";")) { p.next(); return; } // abstract/native signature
                List<String> body = parseBlock();
                emitMethod(isStatic, typeName, className, memberName, typeParams, params, body);
            } else {
                // field: "Type name [= expr];"
                String init = "";
                if (p.at("=")) {
                    p.next();
                    if (p.at("{")) {
                        // Array initializer em CAMPO `int[] xs = {1,2,3}` — mesmo
                        // gap honesto do local (TranslateStatements); sem o guard
                        // o output saía TRUNCADO (`Int[] xs = {`) = Kof inválido
                        // (bug latente achado 13/09 no probe Q4).
                        throw new TranslateException(
                                "array initializer `{...}` não tem equivalente direto em Kof "
                                + "(use `new Int[n]` + atribuições ou `listOf(...)`) — revisão manual");
                    }
                    init = " = " + parseExpr();
                }
                while (!p.at(";") && !p.at(T.EOF)) p.next();
                if (p.at(";")) p.next();
                // Campo estático Java → `static` em Kof (Kof suporta campo
                // estático de classe — verificado 13/09: `static Int X = 5` +
                // `A.X` compila e roda). Antes era SKIPADO silenciosamente →
                // referência virava `Undefined variable or type: 'X'`
                // (SEM011) = Kof inválido (bug latente Q4).
                out.append("    ").append(isStatic ? "static " : "")
                   .append(kofType(typeName)).append(' ').append(memberName).append(init).append('\n');
            }
        }

        private void emitConstructor(List<String> params, List<String> body) {
            out.append("    constructor(").append(paramList(params)).append(") {\n");
            for (String stmt : body) out.append("        ").append(stmt).append('\n');
            out.append("    }\n");
        }

        private void emitMethod(boolean isStatic, String retType, String owner,
                                String name, String typeParams, List<String> params, List<String> body) {
            StringBuilder sb = isStatic ? topFns : out;
            // Função hoisted: refs a campo estático ficam fora de escopo →
            // qualifica `X` → `Owner.X` (o Kof aceita `Classe.campo`).
            java.util.Set<String> shadowed = new java.util.HashSet<>();
            for (String prm : params) {
                int sp = prm.lastIndexOf(' ');
                shadowed.add(sp >= 0 ? prm.substring(sp + 1) : prm);
            }
            List<String> emitBody = new ArrayList<>(body.size());
            for (String stmt : body) {
                emitBody.add(isStatic
                        ? TranslateStatics.qualify(stmt, owner, staticFields, shadowed)
                        : stmt);
            }
            if (isStatic && name.equals("main")) {
                // Java main(String[] args) → top-level Kof main()
                sb.append("main() {\n");
                for (String stmt : emitBody) sb.append("    ").append(stmt).append('\n');
                sb.append("}\n");
                return;
            }
            sb.append("    ").append(kofType(retType)).append(' ').append(name)
              .append(typeParams).append('(').append(paramList(params)).append(')');
            if (emitBody.size() == 1 && emitBody.get(0).startsWith("return ")) {
                String expr = emitBody.get(0).substring("return ".length());
                sb.append(" = ").append(expr).append('\n');
            } else {
                sb.append(" {\n");
                for (String stmt : emitBody) sb.append("        ").append(stmt).append('\n');
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
