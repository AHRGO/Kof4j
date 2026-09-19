package dev.kof.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Toolchain do pipeline APK standalone ({@code kof build --apk} e a face
 * ANDROID do {@code kof deploy}): descoberta de build-tools, leitura do
 * class-file major e o dex/aapt/align/sign em si. Extraido de
 * {@code CmdBuild} pelo gate de 500 linhas (18/09, §299) — responsabilidade
 * propria (nome descreve o que contem, regra 7), comportamento identico.
 */
final class ApkToolchain {

    private ApkToolchain() {
    }
    /**
     * Pipeline APK standalone (#6/#7): chama os binários oficiais do SDK
     * direto — d8 → aapt2 → zip → zipalign → apksigner. Sem --keystore,
     * gera debug keystore local na primeira vez; com --keystore, assina
     * com o keystore do usuário (release signing parametrizável).
     */
    static boolean runApkPipeline(Path projDirRaw, int minSdk, int targetSdk,
                                          String keystore, String storepass,
                                          String keypass, String keyalias) {
        // CI fix (18/09): run() roda com CWD=projDir; caminhos RELATIVOS
        // duplicavam (projDir/<rel>) e o keytool morria em FileNotFoundException
        // no ubuntu (SDK presente, guard passava). Normalizar na entrada.
        final Path projDir = projDirRaw.toAbsolutePath().normalize();
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome == null || androidHome.isBlank()) {
            System.err.println("--apk: ANDROID_HOME not set; generate the project and use 'mvn verify'");
            return false;
        }
        Path bt = pickBuildTools(Path.of(androidHome));
        if (bt == null) {
            System.err.println("--apk: no usable build-tools (need aapt2+d8+zipalign+apksigner) under "
                    + Path.of(androidHome, "build-tools"));
            return false;
        }
        Path platformJar = Path.of(androidHome, "platforms", "android-" + targetSdk, "android.jar");
        if (!Files.isRegularFile(platformJar)) {
            System.err.println("--apk: platform android-" + targetSdk + " missing in "
                    + Path.of(androidHome, "platforms") + " (DEP001: install 'platforms;android-"
                    + targetSdk + "')");
            return false;
        }
        if (classMajorOf(projDir.resolve("libs").resolve("kof-app.jar")) > 61
                && !buildToolsSupportsJava21(bt)) {
            System.err.println("--apk: d8 in " + bt.getFileName() + " cannot read Java-21 bytecode"
                    + " (class major 65); install build-tools >= 35.0.0 (DEP001 environment"
                    + " condition, never a silent dex failure)");
            return false;
        }
        Path build = projDir.toAbsolutePath().normalize().resolve("target");
        Path apkDir = build.resolve("apk");
        boolean userKs = keystore != null && !keystore.isBlank();
        try {
            Files.createDirectories(apkDir);
            // debug keystore local (só quando o usuário não passou --keystore)
            Path ks = userKs ? Path.of(keystore) : build.resolve("debug.keystore");
            if (!userKs && !Files.exists(ks)) {
                run(List.of("keytool", "-genkeypair", "-keystore", ks.toString(),
                        "-alias", "androiddebugkey", "-storepass", "android",
                        "-keypass", "android", "-keyalg", "RSA", "-validity", "9999",
                        "-dname", "CN=Kof Debug,O=Kof,C=BR"), projDir);
            }
            run(List.of(bt.resolve("aapt2").toString(), "compile", "--dir",
                    projDir.resolve("src/main/res").toString(),
                    "-o", apkDir.resolve("res.zip").toString()), projDir);
            run(List.of(bt.resolve("aapt2").toString(), "link",
                    "-o", apkDir.resolve("base.apk").toString(),
                    "-I", platformJar.toString(),
                    "--manifest", projDir.resolve("src/main/AndroidManifest.xml").toString(),
                    "-A", projDir.resolve("src/main/assets").toString(),
                    "-R", apkDir.resolve("res.zip").toString()), projDir);
            run(List.of(bt.resolve("d8").toString(), "--release",
                    "--lib", platformJar.toString(), "--min-api", Integer.toString(minSdk),
                    "--output", apkDir.toString(),
                    projDir.resolve("libs/kof-app.jar").toString()), projDir);
            run(List.of("jar", "uf", apkDir.resolve("base.apk").toString(),
                    "-C", apkDir.toString(), "classes.dex"), projDir);
            run(List.of(bt.resolve("zipalign").toString(), "-f", "4",
                    apkDir.resolve("base.apk").toString(),
                    apkDir.resolve("aligned.apk").toString()), projDir);
            String sp = userKs && storepass != null ? storepass : "android";
            String kp = userKs && keypass != null ? keypass : sp;
            List<String> sign = new ArrayList<>(List.of(
                    bt.resolve("apksigner").toString(), "sign",
                    "--ks", ks.toString(), "--ks-pass", "pass:" + sp,
                    "--key-pass", "pass:" + kp));
            if (userKs && keyalias != null && !keyalias.isBlank()) {
                sign.add("--ks-key-alias");
                sign.add(keyalias);
            }
            sign.add("--out");
            sign.add(build.resolve("kof-app.apk").toString());
            sign.add(apkDir.resolve("aligned.apk").toString());
            run(sign, projDir);
            System.out.println("APK built: " + build.resolve("kof-app.apk"));
            return true;
        } catch (Exception e) {
            System.err.println("APK pipeline failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * CI fix 18/09 (red em ubuntu): o pin hardcoded "34.0.0" escolhia um d8
     * (R8 8.2.x) que NAO le class file major 65 (Java 21) — o pipeline android
     * morria no dex em todo SDK com imagens mais novas. Versoes de build-tools
     * sao pastas semver; escolher a MAIOR com as ferramentas obrigatorias.
     */
    static Path pickBuildTools(Path androidHome) {
        Path root = androidHome.resolve("build-tools");
        if (!Files.isDirectory(root)) return null;
        java.util.List<Path> cands = new java.util.ArrayList<>();
        try (var s = Files.list(root)) {
            for (Path p : s.toList()) {
                if (!Files.isDirectory(p)) continue;
                if (Files.isExecutable(p.resolve("aapt2")) && Files.isExecutable(p.resolve("d8"))
                        && Files.isExecutable(p.resolve("zipalign"))
                        && Files.isExecutable(p.resolve("apksigner"))) {
                    cands.add(p);
                }
            }
        } catch (IOException e) {
            return null;
        }
        cands.sort((a, b) -> compareToolVersions(a.getFileName().toString(),
                b.getFileName().toString()));
        return cands.isEmpty() ? null : cands.get(cands.size() - 1);
    }

    /** Comparacao semver numerica de nomes de pasta de build-tools ("35.0.0" > "34.0.2" > "9.0.0"). */
    static int compareToolVersions(String a, String b) {
        String[] pa = a.split("[.\\-]"), pb = b.split("[.\\-]");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int ia = i < pa.length ? parseIntOr0(pa[i]) : 0;
            int ib = i < pb.length ? parseIntOr0(pb[i]) : 0;
            if (ia != ib) return Integer.compare(ia, ib);
            int c = (i < pa.length ? pa[i] : "").compareTo(i < pb.length ? pb[i] : "");
            if (c != 0) return c;
        }
        return 0;
    }

    private static int parseIntOr0(String s) {
        int v = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < '0' || ch > '9') return -1;
            v = v * 10 + (ch - '0');
        }
        return v;
    }

    /** Maior "class file major version" dentro do jar (bytes 6-7 do header de cada .class). */
    static int classMajorOf(Path jar) {
        int max = 0;
        try (var z = new java.util.zip.ZipFile(jar.toFile())) {
            var entries = z.entries();
            while (entries.hasMoreElements()) {
                var e = entries.nextElement();
                if (!e.getName().endsWith(".class")) continue;
                try (var in = z.getInputStream(e)) {
                    byte[] b = new byte[8];
                    int n = 0, r;
                    while (n < 8 && (r = in.read(b, n, 8 - n)) > 0) n += r;
                    if (n == 8 && (b[0] & 0xFF) == 0xCA && (b[1] & 0xFF) == 0xFE
                            && (b[2] & 0xFF) == 0xBA && (b[3] & 0xFF) == 0xBE) {
                        max = Math.max(max, ((b[6] & 0xFF) << 8) | (b[7] & 0xFF));
                    }
                }
            }
        } catch (IOException ignored) {
            return 0;
        }
        return max;
    }

    /** build-tools >= 35 ship um d8 (R8 >= 8.3) que le major 65 (Java 21). */
    static boolean buildToolsSupportsJava21(Path btDir) {
        String v = btDir.getFileName().toString();
        int dot = v.indexOf('.');
        int major = dot < 0 ? parseIntOr0(v) : parseIntOr0(v.substring(0, dot));
        return major >= 35;
    }

    private static void run(List<String> cmd, Path cwd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(cwd.toFile()).inheritIO();
        Process proc = pb.start();
        int code = proc.waitFor();
        if (code != 0) throw new IOException("exit " + code + ": " + cmd.get(0));
    }

}
