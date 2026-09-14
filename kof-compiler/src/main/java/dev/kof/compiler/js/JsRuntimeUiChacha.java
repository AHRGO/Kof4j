package dev.kof.compiler.js;

/**
 * Fragmento do runtime JS (padrão REFACTOR-500 Fase 8): ChaCha20-Poly1305
 * (D-SEC ratificado 13/09, RFC 8439) — separado de {@link JsRuntimeUiCrypto}
 * para respeitar o gate ≤500 linhas. Concatenado no slice "crypto"
 * (JsRuntimeSlices) — ver UI_CRYPTO_RUNTIME.
 */
public final class JsRuntimeUiChacha {
    private JsRuntimeUiChacha() {
    }

    static final String UI_CHACHA_RUNTIME = """
            // ── ChaCha20-Poly1305 (D-SEC ratificado 13/09, RFC 8439) ──────
            // Puro JS (o WebCrypto não tem ChaCha20). Envelope idêntico ao
            // JVM/Native: chacha20$<nonceB64(12B)>$<ct+tagB64>. r/s lidos
            // LITTLE-ENDIAN, r clamped, bit 2^(8n) por bloco, tag LE.
            // Validado byte a byte contra node:crypto (ChaChaT/FinalT).

            const KOF_CHACHA_SIGMA = [0x61707865, 0x3320646e, 0x79622d32, 0x6b206574];

            function kofChachaRotl(v, n) { return ((v << n) | (v >>> (32 - n))) | 0; }

            function kofChachaBlock(st) {
                const x = st.slice();
                for (let i = 0; i < 10; i++) {
                    x[0]=x[0]+x[4]|0;  x[12]=kofChachaRotl(x[12]^x[0],16);
                    x[8]=x[8]+x[12]|0; x[4]=kofChachaRotl(x[4]^x[8],12);
                    x[0]=x[0]+x[4]|0;  x[12]=kofChachaRotl(x[12]^x[0],8);
                    x[8]=x[8]+x[12]|0; x[4]=kofChachaRotl(x[4]^x[8],7);
                    x[1]=x[1]+x[5]|0;  x[13]=kofChachaRotl(x[13]^x[1],16);
                    x[9]=x[9]+x[13]|0; x[5]=kofChachaRotl(x[5]^x[9],12);
                    x[1]=x[1]+x[5]|0;  x[13]=kofChachaRotl(x[13]^x[1],8);
                    x[9]=x[9]+x[13]|0; x[5]=kofChachaRotl(x[5]^x[9],7);
                    x[2]=x[2]+x[6]|0;  x[14]=kofChachaRotl(x[14]^x[2],16);
                    x[10]=x[10]+x[14]|0; x[6]=kofChachaRotl(x[6]^x[10],12);
                    x[2]=x[2]+x[6]|0;  x[14]=kofChachaRotl(x[14]^x[2],8);
                    x[10]=x[10]+x[14]|0; x[6]=kofChachaRotl(x[6]^x[10],7);
                    x[3]=x[3]+x[7]|0;  x[15]=kofChachaRotl(x[15]^x[3],16);
                    x[11]=x[11]+x[15]|0; x[7]=kofChachaRotl(x[7]^x[11],12);
                    x[3]=x[3]+x[7]|0;  x[15]=kofChachaRotl(x[15]^x[3],8);
                    x[11]=x[11]+x[15]|0; x[7]=kofChachaRotl(x[7]^x[11],7);
                    x[0]=x[0]+x[5]|0;  x[15]=kofChachaRotl(x[15]^x[0],16);
                    x[10]=x[10]+x[15]|0; x[5]=kofChachaRotl(x[5]^x[10],12);
                    x[0]=x[0]+x[5]|0;  x[15]=kofChachaRotl(x[15]^x[0],8);
                    x[10]=x[10]+x[15]|0; x[5]=kofChachaRotl(x[5]^x[10],7);
                    x[1]=x[1]+x[6]|0;  x[12]=kofChachaRotl(x[12]^x[1],16);
                    x[11]=x[11]+x[12]|0; x[6]=kofChachaRotl(x[6]^x[11],12);
                    x[1]=x[1]+x[6]|0;  x[12]=kofChachaRotl(x[12]^x[1],8);
                    x[11]=x[11]+x[12]|0; x[6]=kofChachaRotl(x[6]^x[11],7);
                    x[2]=x[2]+x[7]|0;  x[13]=kofChachaRotl(x[13]^x[2],16);
                    x[8]=x[8]+x[13]|0; x[7]=kofChachaRotl(x[7]^x[8],12);
                    x[2]=x[2]+x[7]|0;  x[13]=kofChachaRotl(x[13]^x[2],8);
                    x[8]=x[8]+x[13]|0; x[7]=kofChachaRotl(x[7]^x[8],7);
                    x[3]=x[3]+x[4]|0;  x[14]=kofChachaRotl(x[14]^x[3],16);
                    x[9]=x[9]+x[14]|0; x[4]=kofChachaRotl(x[4]^x[9],12);
                    x[3]=x[3]+x[4]|0;  x[14]=kofChachaRotl(x[14]^x[3],8);
                    x[9]=x[9]+x[14]|0; x[4]=kofChachaRotl(x[4]^x[9],7);
                }
                for (let i = 0; i < 16; i++) x[i] = (x[i] + st[i]) | 0;
                return x;
            }

            function kofChachaInit(key, nonce, counter) {
                const st = KOF_CHACHA_SIGMA.slice();
                for (let i = 0; i < 8; i++) {
                    st[4+i] = (key[4*i] | key[4*i+1] << 8 | key[4*i+2] << 16 | key[4*i+3] << 24) | 0;
                }
                st[12] = counter | 0;
                st[13] = (nonce[0] | nonce[1] << 8 | nonce[2] << 16 | nonce[3] << 24) | 0;
                st[14] = (nonce[4] | nonce[5] << 8 | nonce[6] << 16 | nonce[7] << 24) | 0;
                st[15] = (nonce[8] | nonce[9] << 8 | nonce[10] << 16 | nonce[11] << 24) | 0;
                return st;
            }

            function kofChachaKeystream(n, key, nonce, counter) {
                const out = new Uint8Array(n);
                let pos = 0;
                while (pos < n) {
                    const ks = kofChachaBlock(kofChachaInit(key, nonce, counter++));
                    for (let i = 0; i < 64 && pos < n; i++, pos++) {
                        out[pos] = (ks[i / 4 | 0] >>> (8 * (i % 4))) & 0xff;
                    }
                }
                return out;
            }

            function kofPolyLe(b, off, n) {
                let v = 0n;
                for (let i = n - 1; i >= 0; i--) v = (v << 8n) | BigInt(b[off + i]);
                return v;
            }

            function kofPoly1305Mac(otk, msg) {
                const P = (1n << 130n) - 5n;
                const clamp = BigInt("0x0ffffffc0ffffffc0ffffffc0fffffff");
                const r = kofPolyLe(otk, 0, 16) & clamp;
                const s = kofPolyLe(otk, 16, 16);
                let acc = 0n;
                let off = 0;
                while (off < msg.length) {
                    const n = Math.min(16, msg.length - off);
                    const blk = kofPolyLe(msg, off, n) + (1n << BigInt(8 * n));
                    acc = ((acc + blk) * r) % P;
                    off += n;
                }
                acc = (acc + s) % (1n << 128n);
                const out = new Uint8Array(16);
                for (let i = 0; i < 16; i++) { out[i] = Number(acc & 0xffn); acc >>= 8n; }
                return out;
            }

            function kofChachaMacdata(ct) {
                const mac = new Uint8Array(((ct.length + 15) / 16 | 0) * 16 + 16);
                mac.set(ct);
                const len = ct.length;
                // bloco final = le64(aadLen=0) || le64(ctLen) — ctLen no byte 8
                mac[mac.length - 8] = len & 0xff;
                mac[mac.length - 7] = (len >>> 8) & 0xff;
                mac[mac.length - 6] = (len >>> 16) & 0xff;
                mac[mac.length - 5] = (len >>> 24) & 0xff;
                return mac;
            }

            export function kofSecChacha20Encrypt(plaintext, keyHex) {
                const key = kofSecHexToBytes(keyHex);
                if (key.length !== 32) throw new Error("ChaCha20 key must be 32 bytes (64 hex chars)");
                const nonce = kofSecRandomBytes(12);
                const pt = kofSecUtf8(String(plaintext));
                const ct = new Uint8Array(pt.length);
                const ks = kofChachaKeystream(pt.length, key, nonce, 1);
                for (let i = 0; i < pt.length; i++) ct[i] = pt[i] ^ ks[i];
                const otk = kofChachaKeystream(32, key, nonce, 0);
                const tag = kofPoly1305Mac(otk, kofChachaMacdata(ct));
                const ctTag = new Uint8Array(ct.length + 16);
                ctTag.set(ct);
                ctTag.set(tag, ct.length);
                return "chacha20$" + kofSecB64Encode(nonce) + "$" + kofSecB64Encode(ctTag);
            }

            export function kofSecChacha20Decrypt(ciphertext, keyHex) {
                const key = kofSecHexToBytes(keyHex);
                if (key.length !== 32) throw new Error("ChaCha20 key must be 32 bytes (64 hex chars)");
                const parts = String(ciphertext).split("$");
                if (parts.length !== 3 || parts[0] !== "chacha20") throw new Error("invalid ciphertext format");
                const nonce = kofSecB64Decode(parts[1], true);
                if (nonce.length !== 12) throw new Error("invalid nonce length");
                const ctTag = kofSecB64Decode(parts[2], true);
                if (ctTag.length < 16) throw new Error("decryption failed: ciphertext too short");
                const ct = ctTag.subarray(0, ctTag.length - 16);
                const tag = ctTag.subarray(ctTag.length - 16);
                const otk = kofChachaKeystream(32, key, nonce, 0);
                const expected = kofPoly1305Mac(otk, kofChachaMacdata(ct));
                let diff = 0;
                for (let j = 0; j < 16; j++) diff |= expected[j] ^ tag[j];
                if (diff !== 0) throw new Error("decryption failed: tag mismatch");
                const pt = new Uint8Array(ct.length);
                const ks = kofChachaKeystream(ct.length, key, nonce, 1);
                for (let i = 0; i < ct.length; i++) pt[i] = ct[i] ^ ks[i];
                return kofSecUtf8Decode(pt);
            }

""";
}
