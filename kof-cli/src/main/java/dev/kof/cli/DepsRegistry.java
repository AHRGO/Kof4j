package dev.kof.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * 1.5.3-S2 (D2-A, D-POLL-19) — o lado PULL do registry: `kof deps` consome
 * releases publicadas por `kof deploy --publish` no GitHub Releases
 * (host oficial do MVP). Uma linha `owner/repo[@ver]` no {@code kofdeps}
 * resolve para o tar.gz da release {@code <repo>-<ver>} (ou a mais recente
 * sem @ver), extrai o jar, CONFERE o {@code SHA256SUMS} embutido no pacote
 * e instala em {@code <cache>/kof/<owner>/<repo>/<ver>/}. Nada é silencioso
 * (R6): os códigos {@code REG00x} marcam cada face não-atendida.
 */
final class DepsRegistry {

    private DepsRegistry() {}

    static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15)).build();

    static String apiBase() {
        String o = System.getenv("KOF_REGISTRY_API");
        return (o == null || o.isBlank()) ? "https://api.github.com" : o;
    }

    /** `owner/repo[@ver]` — o formato kof do registry (distinto do `g:a:v` Maven). */
    static boolean isRegistrySpec(String s) {
        return s != null && s.matches("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+(@[A-Za-z0-9._+-]+)?");
    }

    /** Valida e normaliza a forma kof; null se não é `owner/repo[@ver]`. */
    static String normalize(String spec) {
        if (!isRegistrySpec(spec)) return null;
        return spec.trim();
    }

    static String ownerOf(String spec) { return spec.substring(0, spec.indexOf('/')); }

    static String repoOf(String spec) {
        String r = spec.substring(spec.indexOf('/') + 1);
        int at = r.indexOf('@');
        return at < 0 ? r : r.substring(0, at);
    }

    /** null = "latest". */
    static String versionOf(String spec) {
        int at = spec.indexOf('@');
        return at < 0 ? null : spec.substring(at + 1);
    }

    /** Seam de teste: {@code kof.deps.home} aponta o cache p/ fora do $HOME. */
    static Path kofCacheRoot() {
        return Deps.cacheDir().resolve("kof");
    }

    static Path jarPath(String owner, String repo, String version) {
        return kofCacheRoot().resolve(owner).resolve(repo).resolve(version)
                .resolve(repo + "-" + version + ".jar");
    }

    /**
     * Resolve a spec kof para o jar no cache (idempotente — segunda chamada
     * não toca na rede). Lança IOException com código REG00x honesto.
     */
    static Path fetch(String spec) throws IOException {
        String owner = ownerOf(spec);
        String repo = repoOf(spec);
        String ver = versionOf(spec);
        if (ver != null) {
            Path cached = jarPath(owner, repo, ver);
            if (Files.exists(cached)) return cached;
        }
        String base = apiBase();
        String releaseUrl = ver == null
                ? base + "/repos/" + owner + "/" + repo + "/releases/latest"
                : base + "/repos/" + owner + "/" + repo + "/releases/tags/" + repo + "-" + ver;
        HttpResponse<String> res = get(releaseUrl, String.class);
        if (res.statusCode() == 404) {
            throw new IOException("REG001: release not found on registry: " + owner + "/" + repo
                    + (ver == null ? " (latest)" : " tag " + repo + "-" + ver)
                    + " (private repo without GITHUB_TOKEN? unpublished?)");
        }
        require2xx(res.statusCode(), res.body(), "read release " + owner + "/" + repo);
        String json = res.body();
        String tag = DeployPublish.jsonField(json, "tag_name");
        String version = ver != null ? ver : stripTagPrefix(tag, repo);
        if (version == null || version.isEmpty()) {
            throw new IOException("REG001: release has no parsable version tag: " + tag);
        }
        Path cached = jarPath(owner, repo, version);
        if (Files.exists(cached)) return cached;   // latest aponta p/ já instalado
        Asset asset = pickTarball(json, repo, version);
        if (asset == null) {
            throw new IOException("REG002: release " + tag + " has no .tar.gz asset (publish a"
                    + " `kof deploy --publish` release first)");
        }
        Path tmpDir = Files.createTempDirectory("kofdep-reg");
        HttpResponse<Path> dl = null;
        try {
            dl = get(asset.downloadUrl(), Path.class);
            if (dl.statusCode() != 200) {
                throw new IOException("REG002: asset download failed: HTTP " + dl.statusCode()
                        + " " + asset.downloadUrl());
            }
            extractTarGz(dl.body(), tmpDir);
            Path sums = tmpDir.resolve("SHA256SUMS");
            if (!Files.exists(sums)) {
                throw new IOException("REG004: package has no SHA256SUMS (integrity is not"
                        + " optional) — refusing to install " + owner + "/" + repo);
            }
            Path jar = findJar(tmpDir);
            if (jar == null) {
                throw new IOException("REG003: package has no .jar (non-JVM face?) — "
                        + owner + "/" + repo + "@" + version);
            }
            verifyChecksum(sums, jar);
            Files.createDirectories(cached.getParent());
            Files.move(jar, cached, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (dl != null) Files.deleteIfExists(dl.body());
            deleteTree(tmpDir);
        }
        System.out.println("baixado " + owner + "/" + repo + "@" + version);
        return cached;
    }

    // ---- JSON mínimo (assets[] do GitHub; mesmo leitor single-source) ----

    private record Asset(String name, String downloadUrl) {}

    /** O tar.gz da face: `<repo>-<ver>.tar.gz` exato; senão o primeiro .tar.gz. */
    private static Asset pickTarball(String json, String repo, String version) {
        int key = json.indexOf("\"assets\"");
        if (key < 0) return null;
        int open = json.indexOf('[', key);
        if (open < 0) return null;
        List<Asset> assets = new ArrayList<>();
        int depth = 0;
        for (int i = open; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') { depth--; if (depth == 0) break; }
            else if (c == '{') {
                int end = json.indexOf('}', i);
                if (end < 0) break;
                String obj = json.substring(i, end + 1);
                String name = DeployPublish.jsonField(obj, "name");
                String url = DeployPublish.jsonField(obj, "download_url");
                if (name != null && url != null) assets.add(new Asset(name, url));
                i = end;
            }
        }
        String exact = repo + "-" + version + ".tar.gz";
        for (Asset a : assets) if (a.name().equals(exact)) return a;
        String multi = repo + "-" + version + "-jvm.tar.gz";
        for (Asset a : assets) if (a.name().equals(multi)) return a;
        for (Asset a : assets) if (a.name().endsWith(".tar.gz")) return a;
        return null;
    }

    private static String stripTagPrefix(String tag, String repo) {
        if (tag == null) return null;
        String p = repo + "-";
        return tag.startsWith(p) ? tag.substring(p.length()) : tag;
    }

    // ---- tar.gz (leitor simétrico ao CmdDeploy.writeTarGz — ustar mínimo) ----

    private static void extractTarGz(Path tgz, Path dir) throws IOException {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(tgz))) {
            byte[] header = new byte[512];
            while (true) {
                if (!readFully(in, header)) break;
                boolean blank = true;
                for (byte b : header) if (b != 0) { blank = false; break; }
                if (blank) break;
                String name = cstr(header, 0, 100);
                long size = octal(header, 124, 12);
                char type = (char) (header[156] & 0xff);
                long blocks = (size + 511) / 512;
                if (type == '0' || type == 0) {
                    byte[] data = readN(in, (int) size);
                    Path out = dir.resolve(name).normalize();
                    if (!out.startsWith(dir)) {
                        throw new IOException("REG004: unsafe path in package: " + name);
                    }
                    Files.createDirectories(out.getParent());
                    Files.write(out, data);
                } else {
                    in.skipNBytes(blocks * 512);
                }
                long rest = blocks * 512 - size;
                if (rest > 0) in.skipNBytes(rest);
            }
        }
    }

    private static Path findJar(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .sorted().findFirst().orElse(null);
        }
    }

    /** Linha do jar em SHA256SUMS (formato `coreutils`: `<hex>  <nome>`). */
    private static void verifyChecksum(Path sumsFile, Path jar) throws IOException {
        String base = jar.getFileName().toString();
        for (String line : Files.readAllLines(sumsFile, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\s+", 2);
            if (parts.length != 2) continue;
            String name = parts[1].trim().replaceFirst("^\\*?", "");
            int slash = name.lastIndexOf('/');
            if (slash >= 0) name = name.substring(slash + 1);
            if (!name.equals(base)) continue;
            String actual = CmdDeploy.sha256Hex(jar);
            if (!actual.equalsIgnoreCase(parts[0].trim())) {
                throw new IOException("REG002: SHA256 mismatch for " + base + " (package is"
                        + " corrupt or was tampered after publish — expected " + parts[0].trim()
                        + ", got " + actual + ")");
            }
            return;
        }
        throw new IOException("REG004: " + base + " not listed in SHA256SUMS (integrity"
                + " unverifiable) — refusing to install");
    }

    // ---- http/dirs helpers ----

    @SuppressWarnings("unchecked")
    private static <B> HttpResponse<B> get(String url, Class<B> kind) throws IOException {
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/vnd.github+json");
        String token = System.getenv("GH_TOKEN");
        if (token == null || token.isBlank()) token = System.getenv("GITHUB_TOKEN");
        if (token != null && !token.isBlank()) rb.header("Authorization", "Bearer " + token);
        try {
            HttpResponse.BodyHandler<B> handler = (HttpResponse.BodyHandler<B>)
                    (kind == Path.class ? HttpResponse.BodyHandlers.ofFile(
                            Files.createTempFile("kofdep-dl", ".bin"))
                            : HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return HTTP.send(rb.build(), handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("registry fetch interrupted", e);
        } catch (java.net.ConnectException e) {
            throw new IOException("cannot reach the registry endpoint: " + e.getMessage(), e);
        }
    }

    private static void require2xx(int status, String body, String what) throws IOException {
        if (status / 100 != 2) {
            String s = body == null ? "" : body.replace("\n", " ");
            throw new IOException("registry " + what + " failed: HTTP " + status
                    + (s.length() > 160 ? s.substring(0, 160) + "…" : " " + s));
        }
    }

    private static boolean readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int r = in.read(buf, off, buf.length - off);
            if (r < 0) return off == 0 ? false : throwEof();
            off += r;
        }
        return true;
    }

    private static boolean throwEof() throws IOException {
        throw new IOException("REG002: truncated tar.gz (bad download)");
    }

    private static byte[] readN(InputStream in, int n) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(n);
        int off = 0;
        byte[] chunk = new byte[Math.min(n, 8192)];
        while (off < n) {
            int r = in.read(chunk, 0, Math.min(chunk.length, n - off));
            if (r < 0) throw new IOException("REG002: truncated tar entry");
            bos.write(chunk, 0, r);
            off += r;
        }
        return bos.toByteArray();
    }

    private static String cstr(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    private static long octal(byte[] b, int off, int len) {
        long v = 0;
        for (int i = off; i < off + len; i++) {
            byte c = b[i];
            if (c == 0 || c == ' ') { if (v > 0) break; else continue; }
            if (c < '0' || c > '7') continue;
            v = v * 8 + (c - '0');
        }
        return v;
    }

    private static void deleteTree(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
