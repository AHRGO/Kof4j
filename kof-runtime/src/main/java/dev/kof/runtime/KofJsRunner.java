package dev.kof.runtime;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * KofJsRunner — executes the JavaScript emitted by the KofJS backend inside
 * the Kof process itself, using the embedded GraalJS engine.
 *
 * The KofJS target has no dependency on Node.js or any external JavaScript
 * runtime: the generated .mjs modules are standard ES2022+ ECMAScript modules,
 * and `kof run --target=js` (and the E2E suite) run them here, in-process.
 *
 * Platform operations (filesystem, stdout, stdin) are exposed to the module
 * through the `kof_platform` global, implemented in Java. The generated module
 * only talks to the platform-neutral kof-runtime.mjs; the platform layer
 * (kof-runtime-io.mjs) delegates to kof_platform.
 */
public final class KofJsRunner {

    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    private KofJsRunner() {}

    /**
     * Executes an ESM module file and returns the process exit code
     * (0 on success, 1 on runtime error).
     */
    public static int run(Path moduleFile) throws IOException {
        return run(moduleFile, System.out, System.in, System.err);
    }

    public static int run(Path moduleFile, OutputStream out, InputStream in,
                          OutputStream err) throws IOException {
        return run(moduleFile, out, in, err, false);
    }

    /**
     * Executes an ESM module. When {@code openWindow} is true and the program
     * created a kof.ui window (kofUiFlush was triggered), the serialized page
     * is written next to the module and opened in the system webview (the
     * platform default browser).
     */
    public static int run(Path moduleFile, OutputStream out, InputStream in,
                          OutputStream err, boolean openWindow) throws IOException {
        return run(moduleFile, out, in, err, openWindow, new String[0]);
    }

    public static int run(Path moduleFile, OutputStream out, InputStream in,
                          OutputStream err, boolean openWindow, String[] programArgs) throws IOException {
        // o contexto NÃO pode fechar antes da extração do sentinel de
        // process.exit (o guest object só é legível com o contexto vivo)
        Context context = Context.newBuilder("js")
                .allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false")
                .out(out)
                .err(err)
                .in(in)
                .build();
        try {
            exposePlatform(context, out, in, programArgs);
            Source source = Source.newBuilder("js", moduleFile.toFile())
                    .mimeType("application/javascript+module")
                    .build();
            context.eval(source);
            KofJsAsyncPump.drainActiveTasks(context);
            if (openWindow) {
                Value uiRoot = context.getBindings("js").getMember("kof__uiRootHtml");
                if (uiRoot != null && uiRoot.isString()) {
                    String html = uiRoot.asString();
                    if (html != null && !html.isEmpty()) {
                        KofJsWebview.openInWebview(moduleFile, html);
                    }
                }
            }
            return 0;
        } catch (org.graalvm.polyglot.PolyglotException pe) {
            // process.exit(code): o guest lança { __kof_exit__: code }
            if (pe.isGuestException()) {
                try {
                    Value guest = pe.getGuestObject();
                    if (guest != null && guest.hasMembers()) {
                        Value exit = guest.getMember("__kof_exit__");
                        if (exit != null && exit.isNumber()) {
                            return exit.asInt();
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            try {
                String message = pe.getMessage();
                if (message != null && !message.isBlank()) {
                    err.write((message + "\n").getBytes(StandardCharsets.UTF_8));
                    err.flush();
                }
            } catch (IOException ignored) {
            }
            return 1;
        } catch (Exception e) {
            try {
                String message = e.getMessage();
                if (message != null && !message.isBlank()) {
                    err.write((message + "\n").getBytes(StandardCharsets.UTF_8));
                    err.flush();
                }
            } catch (IOException ignored) {
            }
            return 1;
        } finally {
            context.close();
        }
    }

    /**
     * Executes the module and returns the serialized kof.ui window HTML
     * (or null when the program created no window). Used by tests.
     */
    public static String runCaptureHtml(Path moduleFile, OutputStream out, InputStream in,
                                        OutputStream err) throws IOException {
        try (Context context = Context.newBuilder("js")
                .allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false")
                .out(out)
                .err(err)
                .in(in)
                .build()) {
            exposePlatform(context, out, in);
            Source source = Source.newBuilder("js", moduleFile.toFile())
                    .mimeType("application/javascript+module")
                    .build();
            context.eval(source);
            KofJsAsyncPump.drainActiveTasks(context);
            Value html = context.getBindings("js").getMember("kof__uiRootHtml");
            return html.isString() && !html.asString().isEmpty() ? html.asString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Bombeia microtasks até não haver spawn tasks ativas (kofActiveTasks).
     * GraalJS pode não drenar a fila após um único eval; sem isso spawn/async
     * terminam antes do programa sair.
     */
    // async-sleep host pump moved to KofJsAsyncPump (§132/#83-JS)
    /**
     * Exposes the kof_platform object: IO and console primitives implemented
     * in Java. The generated JavaScript never reaches for Node/browser APIs.
     */
    private static void exposePlatform(Context context, OutputStream out, InputStream in) {
        exposePlatform(context, out, in, new String[0]);
    }

    private static void exposePlatform(Context context, OutputStream out, InputStream in,
                                       String[] programArgs) {
        Value bindings = context.getBindings("js");
        java.util.Map<String, Object> platform = new java.util.LinkedHashMap<>();
        platform.put("print", (ProxyExecutable) args -> {
            for (Value arg : args) {
                try {
                    out.write(String.valueOf(arg.isNull() ? "null" : arg).getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    return 0;
                }
            }
            return 0;
        });
        platform.put("processRun", (ProxyExecutable) args -> {
            try {
                String program = args[0].asString();
                java.util.List<String> cmd = new java.util.ArrayList<>();
                cmd.add(program);
                if (args.length > 1 && !args[1].isNull() && args[1].hasArrayElements()) {
                    long n = args[1].getArraySize();
                    for (long i = 0; i < n; i++) {
                        Value v = args[1].getArrayElement(i);
                        cmd.add(v.isString() ? v.asString() : String.valueOf(v));
                    }
                }
                Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
                String outText = new String(p.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                String errText = new String(p.getErrorStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                int code = p.waitFor();
                java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("stdout", outText);
                result.put("stderr", errText);
                result.put("exitCode", code);
                return result;
            } catch (Exception e) {
                java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("stdout", "");
                result.put("stderr", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                result.put("exitCode", -1);
                return result;
            }
        });
        platform.put("args", (ProxyExecutable) args -> java.util.Arrays.asList(programArgs));
        platform.put("readLine", (ProxyExecutable) args -> readLine(in));
        platform.put("readFile", (ProxyExecutable) args -> {
            try {
                return Files.readString(Path.of(args[0].asString()));
            } catch (IOException e) {
                return null;
            }
        });
        platform.put("writeFile", (ProxyExecutable) args -> {
            try {
                Files.writeString(Path.of(args[0].asString()), args[1].asString());
                return 0;
            } catch (IOException e) {
                return -1;
            }
        });
        platform.put("fileExists", (ProxyExecutable) args -> Files.exists(Path.of(args[0].asString())) ? 1 : 0);
        platform.put("fileIsFile", (ProxyExecutable) args -> Files.isRegularFile(Path.of(args[0].asString())) ? 1 : 0);
        platform.put("fileIsDir", (ProxyExecutable) args -> Files.isDirectory(Path.of(args[0].asString())) ? 1 : 0);
        platform.put("readText", (ProxyExecutable) args -> readFileText(args));
        platform.put("writeText", (ProxyExecutable) args -> writeFileText(args, false));
        platform.put("appendText", (ProxyExecutable) args -> writeFileText(args, true));
        platform.put("readBytes", (ProxyExecutable) args -> readBytes(args));
        platform.put("writeBytes", (ProxyExecutable) args -> writeBytes(args, false));
        platform.put("appendBytes", (ProxyExecutable) args -> writeBytes(args, true));
        platform.put("delete", (ProxyExecutable) args -> {
            try {
                Files.deleteIfExists(Path.of(args[0].asString()));
                return 0;
            } catch (IOException e) {
                return -1;
            }
        });
        platform.put("fileSize", (ProxyExecutable) args -> {
            try {
                return Files.size(Path.of(args[0].asString()));
            } catch (IOException e) {
                return -1L;
            }
        });
        platform.put("fileName", (ProxyExecutable) args -> {
            Path p = Path.of(args[0].asString());
            Path name = p.getFileName();
            return name == null ? args[0].asString() : name.toString();
        });
        platform.put("pathParent", (ProxyExecutable) args -> {
            Path parent = Path.of(args[0].asString()).getParent();
            return parent == null ? null : parent.toString();
        });
        platform.put("pathFileName", (ProxyExecutable) args -> {
            Path name = Path.of(args[0].asString()).getFileName();
            return name == null ? args[0].asString() : name.toString();
        });
        platform.put("pathExtension", (ProxyExecutable) args -> {
            String name = Path.of(args[0].asString()).getFileName().toString();
            int dot = name.lastIndexOf('.');
            return dot <= 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1);
        });
        platform.put("pathNormalize", (ProxyExecutable) args -> Path.of(args[0].asString()).normalize().toString());
        platform.put("pathResolve", (ProxyExecutable) args -> Path.of(args[0].asString()).resolve(args[1].asString()).toString());
        platform.put("pathIsAbsolute", (ProxyExecutable) args -> Path.of(args[0].asString()).isAbsolute() ? 1 : 0);
        platform.put("pathToAbsolute", (ProxyExecutable) args -> Path.of(args[0].asString()).toAbsolutePath().toString());
        platform.put("dirCreate", (ProxyExecutable) args -> dirCreate(args, false));
        platform.put("dirCreateDirs", (ProxyExecutable) args -> dirCreate(args, true));
        platform.put("dirDelete", (ProxyExecutable) args -> {
            try {
                Files.deleteIfExists(Path.of(args[0].asString()));
                return 0;
            } catch (IOException e) {
                return -1;
            }
        });
        platform.put("dirList", (ProxyExecutable) args -> dirList(args));
        // kof.db no JS (DB001) — o mesmo DriverManager do caminho JVM, a
        // mesma JVM; sem driver no classpath connect lanca erro claro.
        platform.put("dbConnect", (ProxyExecutable) args -> {
            try {
                return KofJsDbBridge.connect(args[0].asString());
            } catch (Exception e) {
                throw guestError(e);
            }
        });
        platform.put("dbConnect2", (ProxyExecutable) args -> {
            try {
                return KofJsDbBridge.connect2(args[0].asString(), args[1].asString(),
                        args[2].asString());
            } catch (Exception e) {
                throw guestError(e);
            }
        });
        platform.put("dbClose", (ProxyExecutable) args -> {
            try {
                return KofJsDbBridge.close(args[0].asString());
            } catch (Exception e) {
                throw guestError(e);
            }
        });
        platform.put("dbExecute", (ProxyExecutable) args -> {
            try {
                return KofJsDbBridge.executeV(args[0].asString(), args[1].asString(),
                        listValues(args, 2));
            } catch (Exception e) {
                throw guestError(e);
            }
        });
        platform.put("dbQuery", (ProxyExecutable) args -> {
            try {
                Value cls = args[2];
                String className = cls.isNull() ? "" : cls.asString();
                // String[] (nao List) — mesmo contrato do dirList: o guest
                // acessa rows.size como list.length e rows.get(i) como
                // list[i]; java.util.List nao expoe .length ao JS.
                return KofJsDbBridge.queryV(args[0].asString(), args[1].asString(), className,
                        listValues(args, 3)).toArray(new String[0]);
            } catch (Exception e) {
                throw guestError(e);
            }
        });
        platform.put("dbTransaction", (ProxyExecutable) args -> {
            // O rollback ja ocorre dentro do bridge (re-gra do bloco); deixar
            // a excecao do bloco propagar sem embrulhar — paridade com o JVM,
            // que relanca a causa p/ o try/catch externo ver o valor.
            // O bloco `transaction { }` baixa como closure Kof = objeto com
            // membro `invoke` (convencao do runtime JS, cf. UiWeb 505-506);
            // aceita tambem funcao JS crua por robustez.
            Value fn = args[0];
            Runnable body;
            Value invoke = fn.hasMembers() ? fn.getMember("invoke") : null;
            if (invoke != null && invoke.canExecute()) {
                body = () -> invoke.execute();
            } else if (fn.canExecute()) {
                body = fn::execute;
            } else {
                throw new RuntimeException("transaction: callback is not invocable");
            }
            try {
                KofJsDbBridge.transaction(body);
            } catch (Exception e) {
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(e.getMessage() == null ? e.toString() : e.getMessage());
            }
            return 0;
        });
        // kof.orm platform bridge (ORM001, 18/09) — sobre as MESMAS conexoes do
        // kof.db; registrado em irmao dedicado p/ manter esta classe <=500.
        KofJsOrmRuntime.install(platform);
        // kof.security platform primitives (docs/stdlib/security.md §5)
        platform.put("getenv", (ProxyExecutable) args ->
                System.getenv(args[0].asString()));
        platform.put("randomBytesHex", (ProxyExecutable) args -> {
            int n = args[0].asInt();
            byte[] buf = new byte[Math.max(0, Math.min(n, 4096))];
            SECURE_RANDOM.nextBytes(buf);
            StringBuilder sb = new StringBuilder(buf.length * 2);
            for (byte b : buf) sb.append(String.format("%02x", b));
            return sb.toString();
        });
        platform.put("randomInt", (ProxyExecutable) args -> {
            int bound = args[0].asInt();
            return bound <= 0 ? 0 : SECURE_RANDOM.nextInt(bound);
        });
        platform.put("pbkdf2Hex", (ProxyExecutable) args -> {
            // Fronteira de interop: o guest JS chama com qualquer string. O
            // parseInt por par de hex estava FORA do try e derramava NFE crua
            // no contexto do programa (CodeQL uncaught-number-format-exception
            // #255). Contrato do proxy: null = falha (ja era o caminho do
            // SecretKeyFactory); hex invalido vira null, nunca stack-trace.
            String password = args[0].asString();
            String saltHex = args[1].asString();
            int iterations = args[2].asInt();
            try {
                byte[] salt = decodeHexStrict(saltHex);
                byte[] dk = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(new javax.crypto.spec.PBEKeySpec(
                                password.toCharArray(), salt, iterations, 256))
                        .getEncoded();
                StringBuilder sb = new StringBuilder(dk.length * 2);
                for (byte b : dk) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (Exception e) {
                return null;
            }
        });
        // R3 fatia 3.6 (JS FFI parity): extern no target JS baixa para
        // kofFfi/kofFfiVoid -> kof_platform.ffi/ffi_void -> este bridge host ->
        // KofJsFfiBridge (java.lang.foreign, mesmo downcall do target JVM). Os
        // args chegam como array JS; são marshalled p/ os wrappers Java que o
        // asSpreader+invoke espera por char de layout. Browser: sem host, o
        // kof_platform Proxy lança erro honesto (R7) — nunca stub silencioso.
        platform.put("ffi", (ProxyExecutable) args ->
                KofJsFfiBridge.call(args[0].asString(), args[1].asString(),
                        args[2].asString(), kofFfiArgs(args[2].asString(), args[3])));
        platform.put("ffi_void", (ProxyExecutable) args -> {
            KofJsFfiBridge.callVoid(args[0].asString(), args[1].asString(),
                    args[2].asString(), kofFfiArgs(args[2].asString(), args[3]));
            return null;
        });
        bindings.putMember("kof_platform", ProxyObject.fromMap(platform));
    }

    private static Object[] kofFfiArgs(String sig, Value jsArgs) {
        int n = sig.length() - 1;   // o 1º char é o retorno; o resto são os params
        Object[] real = new Object[n];
        for (int i = 0; i < n; i++) {
            char c = sig.charAt(i + 1);
            Value v = (jsArgs != null && jsArgs.hasArrayElements() && i < jsArgs.getArraySize())
                    ? jsArgs.getArrayElement(i) : null;
            if (v == null || v.isNull()) {
                real[i] = null;
                continue;
            }
            real[i] = switch (c) {
                case 'i' -> v.asInt();
                case 'j' -> v.asLong();
                case 'f' -> v.asFloat();
                case 'd' -> v.asDouble();
                case 'b' -> v.asBoolean();
                default -> v.asString();   // 'S'
            };
        }
        return real;
    }

    /**
     * Hex estrito: comprimento par + somente [0-9a-fA-F]. Lanca
     * NumberFormatException em entrada invalida (o contrato null do proxy
     * pbkdf2Hex cuida do resto). Testavel sem GraalJS.
     */
    static byte[] decodeHexStrict(String hex) {
        if (hex == null || (hex.length() & 1) != 0) {
            throw new NumberFormatException("hex invalido: " + hex);
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new NumberFormatException("hex invalido: " + hex);
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static String readLine(InputStream in) {
        StringBuilder sb = new StringBuilder();
        try {
            int c;
            while ((c = in.read()) != -1) {
                if (c == '\n') return sb.toString();
                sb.append((char) c);
            }
        } catch (IOException ignored) {
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static Object readFileText(Value[] args) {
        try {
            return Files.readString(Path.of(args[0].asString()));
        } catch (IOException e) {
            return null;
        }
    }

    private static Object writeFileText(Value[] args, boolean append) {
        try {
            if (append) {
                Files.writeString(Path.of(args[0].asString()), args[1].asString(),
                        StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.writeString(Path.of(args[0].asString()), args[1].asString());
            }
            return 0;
        } catch (IOException e) {
            return -1;
        }
    }

    private static Object readBytes(Value[] args) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(args[0].asString()));
            int[] out = new int[bytes.length];
            for (int i = 0; i < bytes.length; i++) out[i] = bytes[i] & 0xFF;
            return out;
        } catch (IOException e) {
            return null;
        }
    }

    private static Object writeBytes(Value[] args, boolean append) {
        try {
            byte[] bytes = new byte[(int) args[1].getArraySize()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (args[1].getArrayElement(i).asInt() & 0xFF);
            }
            Path p = Path.of(args[0].asString());
            if (append) {
                Files.write(p, bytes, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.write(p, bytes);
            }
            return 0;
        } catch (IOException e) {
            return -1;
        }
    }

    private static Object dirCreate(Value[] args, boolean recursive) {
        try {
            if (recursive) {
                Files.createDirectories(Path.of(args[0].asString()));
            } else {
                Files.createDirectory(Path.of(args[0].asString()));
            }
            return 0;
        } catch (IOException e) {
            return -1;
        }
    }

    private static Object dirList(Value[] args) {
        try (var stream = Files.list(Path.of(args[0].asString()))) {
            List<String> names = new ArrayList<>();
            stream.map(p -> p.toString()).sorted().forEach(names::add);
            return names.toArray(new String[0]);
        } catch (IOException e) {
            return null;
        }
    }

    /** O ultimo arg da chamada de dbExecute/dbQuery e a lista de binds
     *  (array guest); extrai p/ Value[] na ordem. */
    private static org.graalvm.polyglot.Value[] listValues(Value[] args, int listIndex) {
        if (args.length <= listIndex || args[listIndex] == null || !args[listIndex].hasArrayElements()) {
            return new org.graalvm.polyglot.Value[0];
        }
        Value list = args[listIndex];
        long n = list.getArraySize();
        // §258/#773 (java/comparison-with-wider-type): o cast bruto p/ int
        // truncaria ou daria wrap negativo numa lista > 2^31 (array gigante no
        // guest), sem diagnostico (R6). Bound check explicito; no-op p/ listas
        // normais. Precedente: d6eaae0c (mesma familia CodeQL).
        if (n > Integer.MAX_VALUE) {
            throw new RuntimeException("lista excede o limite da ponte JS (" + n + " > " + Integer.MAX_VALUE + ")");
        }
        int size = (int) n;
        org.graalvm.polyglot.Value[] out = new org.graalvm.polyglot.Value[size];
        for (int i = 0; i < size; i++) {
            out[i] = list.getArrayElement(i);
        }
        return out;
    }

    /** Erro da ponte vira excecao de script com mensagem limpa (o catch do
     *  programa Kof ve a causa, nao stack de proxy). */
    private static RuntimeException guestError(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) {
            msg = e.getClass().getSimpleName();
        }
        return new RuntimeException(msg);
    }
}