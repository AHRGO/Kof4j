package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (padrão REFACTOR-500 Fase 8).
 * §D-SEC (DECISIONS.md ratificado 13/09) — ChaCha20-Poly1305 (RFC 8439).
 * O JDK não expõe ChaCha20 via JCA nos providers padrão: implementação RFC
 * pura, auditável. Envelope idêntico ao AES-GCM real
 * ({@code JvmStringSecurityRuntime}): chacha20$<nonceB64>$<ct+tagB64>.
 * Sem AAD (o envelope Kof autentica só o ct, como o aesgcm faz).
 * Aritmética do Poly1305 via BigInteger (1 MAC/mensagem: auditoria >
 * microbenchmark). Comparação da tag em tempo constante
 * ({@link java.security.MessageDigest#isEqual}).
 */
public final class JvmStringChachaRuntime {

    private JvmStringChachaRuntime() {}

    static String source() {
        return """

                // ── ChaCha20-Poly1305 (D-SEC ratificado 13/09, RFC 8439) ──────
                // Envelope: chacha20$<nonceB64(12B)>$<ct+tagB64>. Chave 32B hex.
                // Nonce aleatório 12B por mensagem (nunca reusado com a mesma
                // chave). Tag comparada em tempo constante.

                private static final int[] KOF_CHACHA_SIGMA = {0x61707865, 0x3320646e, 0x79622d32, 0x6b206574};

                private static int kof_chacha_rotl(int v, int n) { return (v << n) | (v >>> (32 - n)); }

                private static int[] kof_chacha_block(int[] st) {
                    int[] x = st.clone();
                    for (int i = 0; i < 10; i++) {
                        x[0]+=x[4];  x[12]=kof_chacha_rotl(x[12]^x[0],16); x[8]+=x[12]; x[4]=kof_chacha_rotl(x[4]^x[8],12);
                        x[0]+=x[4];  x[12]=kof_chacha_rotl(x[12]^x[0],8);  x[8]+=x[12]; x[4]=kof_chacha_rotl(x[4]^x[8],7);
                        x[1]+=x[5];  x[13]=kof_chacha_rotl(x[13]^x[1],16); x[9]+=x[13]; x[5]=kof_chacha_rotl(x[5]^x[9],12);
                        x[1]+=x[5];  x[13]=kof_chacha_rotl(x[13]^x[1],8);  x[9]+=x[13]; x[5]=kof_chacha_rotl(x[5]^x[9],7);
                        x[2]+=x[6];  x[14]=kof_chacha_rotl(x[14]^x[2],16); x[10]+=x[14]; x[6]=kof_chacha_rotl(x[6]^x[10],12);
                        x[2]+=x[6];  x[14]=kof_chacha_rotl(x[14]^x[2],8);  x[10]+=x[14]; x[6]=kof_chacha_rotl(x[6]^x[10],7);
                        x[3]+=x[7];  x[15]=kof_chacha_rotl(x[15]^x[3],16); x[11]+=x[15]; x[7]=kof_chacha_rotl(x[7]^x[11],12);
                        x[3]+=x[7];  x[15]=kof_chacha_rotl(x[15]^x[3],8);  x[11]+=x[15]; x[7]=kof_chacha_rotl(x[7]^x[11],7);
                        x[0]+=x[5];  x[15]=kof_chacha_rotl(x[15]^x[0],16); x[10]+=x[15]; x[5]=kof_chacha_rotl(x[5]^x[10],12);
                        x[0]+=x[5];  x[15]=kof_chacha_rotl(x[15]^x[0],8);  x[10]+=x[15]; x[5]=kof_chacha_rotl(x[5]^x[10],7);
                        x[1]+=x[6];  x[12]=kof_chacha_rotl(x[12]^x[1],16); x[11]+=x[12]; x[6]=kof_chacha_rotl(x[6]^x[11],12);
                        x[1]+=x[6];  x[12]=kof_chacha_rotl(x[12]^x[1],8);  x[11]+=x[12]; x[6]=kof_chacha_rotl(x[6]^x[11],7);
                        x[2]+=x[7];  x[13]=kof_chacha_rotl(x[13]^x[2],16); x[8]+=x[13]; x[7]=kof_chacha_rotl(x[7]^x[8],12);
                        x[2]+=x[7];  x[13]=kof_chacha_rotl(x[13]^x[2],8);  x[8]+=x[13]; x[7]=kof_chacha_rotl(x[7]^x[8],7);
                        x[3]+=x[4];  x[14]=kof_chacha_rotl(x[14]^x[3],16); x[9]+=x[14]; x[4]=kof_chacha_rotl(x[4]^x[9],12);
                        x[3]+=x[4];  x[14]=kof_chacha_rotl(x[14]^x[3],8);  x[9]+=x[14]; x[4]=kof_chacha_rotl(x[4]^x[9],7);
                    }
                    for (int i = 0; i < 16; i++) { x[i] += st[i]; }
                    return x;
                }

                private static int[] kof_chacha_init(byte[] key, byte[] nonce, int counter) {
                    int[] st = new int[16];
                    for (int i = 0; i < 4; i++) { st[i] = KOF_CHACHA_SIGMA[i]; }
                    for (int i = 0; i < 8; i++) {
                        st[4+i] = (key[4*i] & 0xff) | (key[4*i+1] & 0xff) << 8
                                | (key[4*i+2] & 0xff) << 16 | (key[4*i+3] & 0xff) << 24;
                    }
                    st[12] = counter;
                    st[13] = (nonce[0] & 0xff) | (nonce[1] & 0xff) << 8
                            | (nonce[2] & 0xff) << 16 | (nonce[3] & 0xff) << 24;
                    st[14] = (nonce[4] & 0xff) | (nonce[5] & 0xff) << 8
                            | (nonce[6] & 0xff) << 16 | (nonce[7] & 0xff) << 24;
                    st[15] = (nonce[8] & 0xff) | (nonce[9] & 0xff) << 8
                            | (nonce[10] & 0xff) << 16 | (nonce[11] & 0xff) << 24;
                    return st;
                }

                private static byte[] kof_chacha_keystream(int n, byte[] key, byte[] nonce, int counter) {
                    byte[] out = new byte[n];
                    int pos = 0;
                    while (pos < n) {
                        int[] ks = kof_chacha_block(kof_chacha_init(key, nonce, counter++));
                        for (int i = 0; i < 64 && pos < n; i++, pos++) {
                            out[pos] = (byte) ((ks[i / 4] >>> (8 * (i % 4))) & 0xff);
                        }
                    }
                    return out;
                }

                private static void kof_chacha_xor(byte[] in, byte[] out, byte[] ks) {
                    for (int i = 0; i < in.length; i++) { out[i] = (byte) (in[i] ^ ks[i]); }
                }

                // Poly1305 (RFC 8439 §2.5): r/s lidos LITTLE-ENDIAN da otk,
                // r clamped, cada bloco de 16 com bit 2^(8n) somado,
                // tag = (acc + s) serializada little-endian. BigInteger.
                private static byte[] kof_poly1305_mac(byte[] otk, byte[] msg) {
                    java.math.BigInteger P = java.math.BigInteger.ONE.shiftLeft(130)
                            .subtract(java.math.BigInteger.valueOf(5));
                    java.math.BigInteger clamp = new java.math.BigInteger("0ffffffc0ffffffc0ffffffc0fffffff", 16);
                    java.math.BigInteger r = kof_poly_le(otk, 0, 16).and(clamp);
                    java.math.BigInteger s = kof_poly_le(otk, 16, 16);
                    java.math.BigInteger acc = java.math.BigInteger.ZERO;
                    int off = 0;
                    while (off < msg.length) {
                        int n = Math.min(16, msg.length - off);
                        java.math.BigInteger blk = kof_poly_le(msg, off, n)
                                .add(java.math.BigInteger.ONE.shiftLeft(8 * n));
                        acc = acc.add(blk).multiply(r).mod(P);
                        off += n;
                    }
                    acc = acc.add(s).mod(java.math.BigInteger.ONE.shiftLeft(128));
                    byte[] out = new byte[16];
                    for (int i = 0; i < 16; i++) {
                        out[i] = (byte) acc.and(java.math.BigInteger.valueOf(0xff)).intValue();
                        acc = acc.shiftRight(8);
                    }
                    return out;
                }

                private static java.math.BigInteger kof_poly_le(byte[] b, int off, int n) {
                    java.math.BigInteger v = java.math.BigInteger.ZERO;
                    for (int i = n - 1; i >= 0; i--) {
                        v = v.shiftLeft(8).or(java.math.BigInteger.valueOf(b[off + i] & 0xffL));
                    }
                    return v;
                }

                // mac_data (RFC 8439 §2.8, sem AAD): pad16(ct) || le64(0) || le64(len)
                private static byte[] kof_chacha_macdata(byte[] ct) {
                    byte[] mac = new byte[((ct.length + 15) / 16) * 16 + 16];
                    System.arraycopy(ct, 0, mac, 0, ct.length);
                    // bloco final = le64(aadLen=0) || le64(ctLen): o ctLen
                    // entra no byte 8 do bloco (aadLen vazio = 8 zeros antes)
                    mac[mac.length - 8] = (byte) (ct.length & 0xff);
                    mac[mac.length - 7] = (byte) ((ct.length >>> 8) & 0xff);
                    mac[mac.length - 6] = (byte) ((ct.length >>> 16) & 0xff);
                    mac[mac.length - 5] = (byte) ((ct.length >>> 24) & 0xff);
                    return mac;
                }

                public static String kof_sec_chacha20_encrypt(String plaintext, String keyHex) {
                    try {
                        byte[] key = kof_sec_fromHex(keyHex);
                        if (key.length != 32) throw new IllegalArgumentException("ChaCha20 key must be 32 bytes (64 hex chars)");
                        byte[] nonce = new byte[12];
                        KOF_SEC_RANDOM.nextBytes(nonce);
                        byte[] pt = plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        byte[] ct = new byte[pt.length];
                        kof_chacha_xor(pt, ct, kof_chacha_keystream(pt.length, key, nonce, 1));
                        byte[] otk = kof_chacha_keystream(32, key, nonce, 0);
                        byte[] tag = kof_poly1305_mac(otk, kof_chacha_macdata(ct));
                        byte[] ctTag = new byte[ct.length + 16];
                        System.arraycopy(ct, 0, ctTag, 0, ct.length);
                        System.arraycopy(tag, 0, ctTag, ct.length, 16);
                        return "chacha20$" + java.util.Base64.getEncoder().encodeToString(nonce) + "$"
                                + java.util.Base64.getEncoder().encodeToString(ctTag);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                public static String kof_sec_chacha20_decrypt(String ciphertext, String keyHex) {
                    try {
                        byte[] key = kof_sec_fromHex(keyHex);
                        if (key.length != 32) throw new IllegalArgumentException("ChaCha20 key must be 32 bytes (64 hex chars)");
                        String[] parts = ciphertext.split("\\\\$");
                        if (parts.length != 3 || !"chacha20".equals(parts[0])) throw new IllegalArgumentException("invalid ciphertext format");
                        byte[] nonce = java.util.Base64.getDecoder().decode(parts[1]);
                        if (nonce.length != 12) throw new IllegalArgumentException("invalid nonce length");
                        byte[] ctTag = java.util.Base64.getDecoder().decode(parts[2]);
                        if (ctTag.length < 16) throw new IllegalArgumentException("decryption failed: ciphertext too short");
                        int ctLen = ctTag.length - 16;
                        byte[] otk = kof_chacha_keystream(32, key, nonce, 0);
                        byte[] ct = java.util.Arrays.copyOfRange(ctTag, 0, ctLen);
                        byte[] expected = kof_poly1305_mac(otk, kof_chacha_macdata(ct));
                        if (!java.security.MessageDigest.isEqual(expected,
                                java.util.Arrays.copyOfRange(ctTag, ctLen, ctTag.length))) {
                            throw new IllegalArgumentException("decryption failed: tag mismatch");
                        }
                        byte[] pt = new byte[ctLen];
                        kof_chacha_xor(ct, pt, kof_chacha_keystream(ctLen, key, nonce, 1));
                        return new String(pt, java.nio.charset.StandardCharsets.UTF_8);
                    } catch (RuntimeException e) {
                        if (e.getMessage() != null && e.getMessage().startsWith("decryption failed")) { throw e; }
                        throw new RuntimeException("decryption failed: " + e.getMessage());
                    }
                }
""";
    }
}
