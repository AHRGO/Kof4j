package dev.kof.cli;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * D2-A (DECISIONS.md §D-POLL-19, 19/09) — a face `--publish` do
 * {@code kof deploy}: o host oficial de releases é o <b>GitHub Releases</b>
 * (MVP do registry: local + GitHub). Publicar = criar a release da tag
 * {@code <name>-<version>} (422 = reaproveitar a existente) e subir os
 * artefatos já empacotados (tar.gz por face + manifest do multi-deploy).
 *
 * <p>Sem token não há "meia publicação": falha honesta com a razão (R6), o
 * pacote local sob {@code --output} continua sendo a unidade publicável.
 * O endpoint é sobrescrevível ({@code KOF_PUBLISH_API}) para o fake server
 * dos testes — nunca network real nos gates.
 */
final class DeployPublish {

    private DeployPublish() {}

    static String apiBase() {
        String o = System.getenv("KOF_PUBLISH_API");
        return (o == null || o.isBlank()) ? "https://api.github.com" : o;
    }

    /** owner/repo a partir do valor de --publish; null = inferir do git remote. */
    static String parseRepo(String spec) throws IOException {
        String v = spec == null ? "" : spec.trim();
        if (v.isEmpty() || v.equals("github")) return null;
        if (v.contains("://")) {
            try {
                String path = URI.create(v).getPath();
                if (path.startsWith("/")) path = path.substring(1);
                if (path.endsWith(".git")) path = path.substring(0, path.length() - 4);
                String[] parts = path.split("/");
                if (parts.length >= 2) return parts[0] + "/" + parts[1];
            } catch (IllegalArgumentException e) {
                throw new IOException("not a valid repository URL: " + v);
            }
            throw new IOException("not a valid repository URL: " + v);
        }
        if (v.contains("@") && v.contains(":")) { // git@github.com:owner/repo.git
            String path = v.substring(v.indexOf(':') + 1);
            if (path.endsWith(".git")) path = path.substring(0, path.length() - 4);
            String[] parts = path.split("/");
            if (parts.length >= 2) return parts[0] + "/" + parts[1];
            throw new IOException("not a valid repository spec: " + v);
        }
        if (v.matches("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+")) return v;
        throw new IOException("cannot read --publish value '" + v
                + "' (use owner/repo, a GitHub URL, or bare --publish to infer from git remote)");
    }

    /** Inferência pelo git: origin do diretório atual. */
    static String repoFromGit() throws IOException {
        Process p = new ProcessBuilder("git", "remote", "get-url", "origin")
                .redirectErrorStream(true).start();
        String out;
        try (var in = p.getInputStream()) {
            out = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            out = "";
        }
        try {
            p.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (p.exitValue() != 0 || out.isEmpty()) {
            throw new IOException("cannot infer the GitHub repository from `git remote get-url origin`"
                    + " — pass --publish <owner/repo>");
        }
        return parseRepo(out);
    }

    /**
     * Publica os artefatos das faces ditas numa release única
     * {@code name-version} (tar.gz por Release + arquivo extra do multi).
     */
    static void publishAll(List<CmdDeploy.Release> faces, Path extra, String publishSpec,
                           String name, String version) throws IOException {
        String token = firstNonEmpty(System.getenv("GH_TOKEN"), System.getenv("GITHUB_TOKEN"));
        if (token == null) {
            throw new IOException("--publish needs GH_TOKEN or GITHUB_TOKEN (D-POLL-19/D2-A:"
                    + " GitHub Releases is the official host); the packaged release under"
                    + " --output is the always-local unit");
        }
        String repo = parseRepo(publishSpec);
        if (repo == null) repo = repoFromGit();
        if (repo == null) throw new IOException("no repository to publish to");
        String tag = name + "-" + version;
        String base = apiBase();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        String body = "{\"tag_name\":\"" + tag + "\",\"name\":\"" + tag + "\","
                + "\"body\":\"Built by kof deploy (D2-A).\\n\\n- artifacts: " + faces.size()
                + (extra != null ? " + deploy manifest" : "") + "\"}";
        HttpRequest.Builder create = HttpRequest.newBuilder(URI.create(base + "/repos/" + repo + "/releases"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        HttpResponse<String> res = send(http, create.build());
        String json;
        if (res.statusCode() == 422) {
            // tag já existe (idempotência): reusa a release daquela tag.
            HttpRequest get = HttpRequest.newBuilder(
                    URI.create(base + "/repos/" + repo + "/releases/tags/" + tag))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json").GET().build();
            HttpResponse<String> g = send(http, get);
            require2xx(g.statusCode(), g.body(), "read release " + tag);
            json = g.body();
        } else {
            require2xx(res.statusCode(), res.body(), "create release " + tag);
            json = res.body();
        }
        String uploadUrl = jsonField(json, "upload_url");
        String assetUrl = jsonField(json, "assets_url");
        if (uploadUrl == null && assetUrl != null) {
            uploadUrl = assetUrl; // fake servers minimalistas
        }
        if (uploadUrl == null) {
            throw new IOException("release response has no upload_url: " + snippet(json));
        }
        int curly = uploadUrl.indexOf('{');
        if (curly > 0) uploadUrl = uploadUrl.substring(0, curly);
        for (CmdDeploy.Release r : faces) {
            if (r.tgz() != null) upload(http, uploadUrl, token, r.tgz());
        }
        if (extra != null) upload(http, uploadUrl, token, extra);
        for (CmdDeploy.Release r : faces) {
            System.out.println("published " + r.target() + " → https://github.com/" + repo
                    + "/releases/tag/" + tag);
        }
    }

    private static HttpResponse<String> send(HttpClient http, HttpRequest req)
            throws IOException {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("publish interrupted", e);
        } catch (java.net.ConnectException e) {
            throw new IOException("cannot reach the publish endpoint: " + e.getMessage(), e);
        }
    }

    private static void upload(HttpClient http, String uploadUrl, String token, Path file)
            throws IOException {
        String query = "?name=" + URLEncoder.encode(file.getFileName().toString(),
                StandardCharsets.UTF_8) + "&label=" + URLEncoder.encode("deploy artifact",
                StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(uploadUrl + query))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofFile(file)).build();
        HttpResponse<String> res = send(http, req);
        require2xx(res.statusCode(), res.body(), "upload " + file.getFileName());
    }

    /** Manifesto do multi-deploy (X9 fatia 4 / 8.4): um JSON por face, estável. */
    static String manifestJson(List<CmdDeploy.Release> results) {
        StringBuilder m = new StringBuilder("[\n");
        for (int i = 0; i < results.size(); i++) {
            CmdDeploy.Release r = results.get(i);
            m.append("  {\"target\": \"").append(r.target())
             .append("\", \"status\": \"").append(r.status()).append("\"");
            if (r.artifact() != null) {
                m.append(", \"artifact\": \"").append(r.artifact())
                 .append("\", \"sha256\": \"").append(r.sha256()).append("\"");
            }
            if (r.error() != null) {
                m.append(", \"error\": \"").append(escJson(r.error())).append("\"");
            }
            m.append("}").append(i + 1 < results.size() ? ",\n" : "\n");
        }
        m.append("]\n");
        return m.toString();
    }

    static String escJson(String v) {
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private static void require2xx(int status, String body, String what) throws IOException {
        if (status / 100 != 2) {
            throw new IOException("github " + what + " failed: HTTP " + status + " " + snippet(body));
        }
    }

    private static String snippet(String s) {
        s = s == null ? "" : s.replace("\n", " ");
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }

    /** Le {@code "field": "value"} de um JSON plano (sem parser — as chaves vêm do GitHub/fake). */
    static String jsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int i = json.indexOf(key);
        if (i < 0) return null;
        i = json.indexOf(':', i + key.length());
        if (i < 0) return null;
        int q1 = json.indexOf('"', i + 1);
        if (q1 < 0) return null;
        StringBuilder v = new StringBuilder();
        for (int k = q1 + 1; k < json.length(); k++) {
            char c = json.charAt(k);
            if (c == '"' && json.charAt(k - 1) != '\\') return v.toString();
            v.append(c);
        }
        return null;
    }
}
