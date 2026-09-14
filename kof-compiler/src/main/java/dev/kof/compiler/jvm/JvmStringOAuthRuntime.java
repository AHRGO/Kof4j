package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof.security D-SEC camada 16 (OAuth2 resource server: JWKS + RS/ES)
 * - fragmento proprio de JvmStringRuntime. Concatenacao preserva ordem.
 */
public final class JvmStringOAuthRuntime {

    private JvmStringOAuthRuntime() {}

    static String source() {
        return """

                // ── D-SEC camada 16: OAuth2 resource server (JWKS) ─────────────

                private static volatile String KOF_OAUTH_JWKS_URL;
                private static volatile String KOF_OAUTH_ISSUER;
                private static volatile String KOF_OAUTH_AUDIENCE;
                private static volatile String KOF_OAUTH_JWKS_JSON;

                /**
                 * Configura a validacao de JWT de TERCEIRO (resource server):
                 * busca as chaves publicas em {@code jwksUrl} e exige
                 * {@code issuer}/{@code audience} (String vazia/null = nao
                 * exige). Apos configurar, {@code auth.authenticated()} passa a
                 * aceitar tokens RS/ES validados pelo JWKS.
                 */
                public static boolean kof_sec_auth_resource_server(String jwksUrl,
                        String issuer, String audience) {
                    if (jwksUrl == null || jwksUrl.isBlank()) return false;
                    KOF_OAUTH_JWKS_URL = jwksUrl;
                    KOF_OAUTH_ISSUER = issuer == null || issuer.isBlank() ? null : issuer;
                    KOF_OAUTH_AUDIENCE = audience == null || audience.isBlank() ? null : audience;
                    KOF_OAUTH_JWKS_JSON = null;
                    return true;
                }

                private static String kof_sec_oauth_jwks() {
                    String cached = KOF_OAUTH_JWKS_JSON;
                    if (cached != null) return cached;
                    String url = KOF_OAUTH_JWKS_URL;
                    if (url == null) return null;
                    try {
                        String body = kof_http_get(url);
                        KOF_OAUTH_JWKS_JSON = body;
                        return body;
                    } catch (Exception e) {
                        return null;
                    }
                }

                /**
                 * Valida um JWT de terceiro contra o resource server
                 * configurado. Devolve o payload (claims JSON) quando valido,
                 * {@code null} em qualquer falha (alg nao permitido, assinatura
                 * invalida, exp/iss/aud). Allowlist fixa RS256/384/512 e
                 * ES256/384/512 — nunca {@code none} nem HS* (confusao de
                 * algoritmo).
                 */
                public static String kof_sec_auth_resource_server_verify(String token) {
                    if (token == null || KOF_OAUTH_JWKS_URL == null) return null;
                    try {
                        String[] parts = token.split("\\\\.");
                        if (parts.length != 3) return null;
                        String headerJson = new String(kof_sec_b64urlDecode(parts[0]),
                                java.nio.charset.StandardCharsets.UTF_8);
                        Object header = kof_json_parse(headerJson);
                        if (!(header instanceof Map<?, ?> h)) return null;
                        Object algObj = h.get("alg");
                        if (!(algObj instanceof String alg)) return null;
                        String jca = kof_sec_oauth_jca(alg);
                        if (jca == null) return null;
                        String kid = h.get("kid") instanceof String k ? k : null;
                        java.security.PublicKey key = kof_sec_oauth_find_key(kid, alg);
                        if (key == null) {
                            // rotacao de chave: re-busca o JWKS uma vez.
                            KOF_OAUTH_JWKS_JSON = null;
                            key = kof_sec_oauth_find_key(kid, alg);
                        }
                        if (key == null) return null;
                        java.security.Signature sig = java.security.Signature.getInstance(jca);
                        sig.initVerify(key);
                        sig.update((parts[0] + "." + parts[1])
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        if (!sig.verify(kof_sec_b64urlDecode(parts[2]))) return null;
                        String payloadJson = new String(kof_sec_b64urlDecode(parts[1]),
                                java.nio.charset.StandardCharsets.UTF_8);
                        Object parsed = kof_json_parse(payloadJson);
                        if (!(parsed instanceof Map<?, ?> claims)) return null;
                        Object exp = claims.get("exp");
                        if (exp instanceof Number n
                                && n.longValue() * 1000 <= System.currentTimeMillis()) {
                            return null;
                        }
                        if (KOF_OAUTH_ISSUER != null) {
                            Object iss = claims.get("iss");
                            if (!(iss instanceof String s && s.equals(KOF_OAUTH_ISSUER))) return null;
                        }
                        if (KOF_OAUTH_AUDIENCE != null
                                && !kof_sec_oauth_aud_matches(claims.get("aud"), KOF_OAUTH_AUDIENCE)) {
                            return null;
                        }
                        return payloadJson;
                    } catch (Exception e) {
                        return null;
                    }
                }

                private static String kof_sec_oauth_jca(String alg) {
                    return switch (alg) {
                        case "RS256" -> "SHA256withRSA";
                        case "RS384" -> "SHA384withRSA";
                        case "RS512" -> "SHA512withRSA";
                        case "ES256" -> "SHA256withECDSA";
                        case "ES384" -> "SHA384withECDSA";
                        case "ES512" -> "SHA512withECDSA";
                        default -> null;
                    };
                }

                private static boolean kof_sec_oauth_aud_matches(Object aud, String expected) {
                    if (aud instanceof String s) return s.equals(expected);
                    if (aud instanceof java.util.List<?> list) {
                        for (Object item : list) {
                            if (item instanceof String s && s.equals(expected)) return true;
                        }
                    }
                    return false;
                }

                private static java.security.PublicKey kof_sec_oauth_find_key(String kid, String alg) {
                    String jwks = kof_sec_oauth_jwks();
                    if (jwks == null) return null;
                    try {
                        Object parsed = kof_json_parse(jwks);
                        if (!(parsed instanceof Map<?, ?> m)) return null;
                        Object keysObj = m.get("keys");
                        if (!(keysObj instanceof java.util.List<?> keys)) return null;
                        for (Object entry : keys) {
                            if (!(entry instanceof Map<?, ?> jwk)) continue;
                            if (kid != null && !kid.equals(jwk.get("kid"))) continue;
                            java.security.PublicKey key = kof_sec_oauth_key_from_jwk(jwk, alg);
                            if (key != null) return key;
                        }
                    } catch (Exception e) {
                        return null;
                    }
                    return null;
                }

                private static java.security.PublicKey kof_sec_oauth_key_from_jwk(
                        Map<?, ?> jwk, String alg) {
                    try {
                        String kty = jwk.get("kty") instanceof String s ? s : null;
                        if ("RSA".equals(kty)) {
                            if (alg.startsWith("ES")) return null;
                            byte[] n = kof_sec_b64urlDecode((String) jwk.get("n"));
                            byte[] e = kof_sec_b64urlDecode((String) jwk.get("e"));
                            java.security.spec.RSAPublicKeySpec spec =
                                    new java.security.spec.RSAPublicKeySpec(
                                            new java.math.BigInteger(1, n),
                                            new java.math.BigInteger(1, e));
                            return java.security.KeyFactory.getInstance("RSA").generatePublic(spec);
                        }
                        if ("EC".equals(kty)) {
                            if (alg.startsWith("RS")) return null;
                            String crv = jwk.get("crv") instanceof String s ? s : null;
                            String curve = switch (crv == null ? "" : crv) {
                                case "P-256" -> "secp256r1";
                                case "P-384" -> "secp384r1";
                                case "P-521" -> "secp521r1";
                                default -> null;
                            };
                            if (curve == null) return null;
                            byte[] x = kof_sec_b64urlDecode((String) jwk.get("x"));
                            byte[] y = kof_sec_b64urlDecode((String) jwk.get("y"));
                            java.security.AlgorithmParameters params =
                                    java.security.AlgorithmParameters.getInstance("EC");
                            params.init(new java.security.spec.ECGenParameterSpec(curve));
                            java.security.spec.ECParameterSpec ecSpec = params.getParameterSpec(
                                    java.security.spec.ECParameterSpec.class);
                            java.security.spec.ECPoint point = new java.security.spec.ECPoint(
                                    new java.math.BigInteger(1, x), new java.math.BigInteger(1, y));
                            return java.security.KeyFactory.getInstance("EC").generatePublic(
                                    new java.security.spec.ECPublicKeySpec(point, ecSpec));
                        }
                    } catch (Exception e) {
                        return null;
                    }
                    return null;
                }

""";
    }
}
