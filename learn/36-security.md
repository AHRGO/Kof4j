[English](36-security.md) | [Português](36-security.pt_BR.md)

# 36 — Security (kof.security)

> **Kof 0.4.0-beta — Sep 2026 — complete across the 3 targets (SECN00x gaps documented)**

`kof.security` is the Standard Library's security layer: passwords, cryptography,
JWT, secrets and authentication for web applications — with **secure by default**.

```kof
var hash = passwords.hash("hunter2")
println(passwords.verify("hunter2", hash))   // true
```

## Passwords

Never use `sha256(password)` to store a password. Use `passwords`:

```kof
var hash = passwords.hash("senha")            // pbkdf2$sha256$600000$salt$hash
var ok = passwords.verify("senha", hash)      // constant-time comparison
var rehash = passwords.needsRehash(hash)      // stale parameters?
```

The choice of algorithm/iterations/salt is automatic and secure. The hash
format is versioned — when the recommended parameters change,
`needsRehash` returns `true` and the application can re-hash.

## Crypto

```kof
var digest = crypto.sha256("kof")             // hex
var mac = crypto.hmacSha256(key, data)        // hex
var key = crypto.randomHex(32)                // secure 32 bytes
var ct = crypto.encryptAesGcm("segredo", key) // aesgcm$iv$ct
var pt = crypto.decryptAesGcm(ct, key)        // fails on tamper
```

Target gaps are clear compilation errors (`SECN00x`), never
silent behavior.

## JWT

```kof
var token = jwt.create("{\"sub\":\"u1\",\"roles\":[\"admin\"]}", secret)
var claims = jwt.verify(token, secret)
var claims2 = jwt.verify(token, secret, "kof", "api")   // iss + aud
```

The algorithm is fixed (HS256) — **never accepted from the token** (no
algorithm confusion). `exp`, `iss` and `aud` are validated. The default secret
comes from `KOF_JWT_SECRET` (`jwt.secret()`).

## Secrets

```kof
var apiKey = secrets.get("API_KEY")           // environment variable
var apiKey = secrets.get("API_KEY", "dev")    // with fallback
var logLine = secrets.redact(token)           // never leak secrets into logs
```

## Web auth (middleware)

```kof
var app = web.app()
auth.secret("s3cret")
app.use {
    if (!auth.authenticated()) {
        return "{\"error\":\"unauthorized\"}"
    }
    if (!auth.hasRole("admin")) {
        return "{\"error\":\"forbidden\"}"
    }
    return null
}
app.get("/admin") { return "admin area" }
app.listen(8080)
```

## Constant-time comparison

```kof
if (security.constantTimeEquals(a, b)) { ... }
```

Use it to compare tokens, hashes and secrets — never `==` on
sensitive values.

## Support by target

`kof.security` is complete across the 3 targets — Native in pure asm (no libc),
values identical to the JVM (FIPS 180-4 / RFC 2104):

| Area | JVM | Native | JS |
|------|-----|--------|----|
| passwords (PBKDF2-HMAC-SHA256, 600k) | ✅ | ✅ (asm) | ✅ (delegation to the platform) |
| sha256 / hmacSha256 | ✅ | ✅ (asm) | ✅ (pure JS) |
| sha512 | ✅ | ✅ (asm) | ✅ (pure JS) |
| aes-gcm | ✅ | ✅ (asm) | ✅ (pure JS) |
| jwt HS256 (sig/exp/iss/aud) | ✅ | ✅ (asm) | ✅ |
| secrets (env) | ✅ | ✅ (`/proc/self/environ`) | ✅ |
| constant-time / redact | ✅ | ✅ | ✅ |
| rateLimit / session / apiKey (G9) | ✅ | ✅ | ✅ |
| auth web (`auth.*`, Bearer JWT) | ✅ | ❌ | ❌ |
| csrf / cors / security headers | ✅ | ❌ | ❌ |

Remaining gaps (`SECN00x`, compile-time diagnosis): the web context of
auth/headers (JVM only — the web server is JVM, `WEB002` on Native). AES-GCM on
JS (`SECN002`) and JWT (`SECN004`) closed. Tests: `KofSecurityTest` (27; unit +
E2E across the 3 targets + adversarial). Full reference: `docs/stdlib/security.md`.
