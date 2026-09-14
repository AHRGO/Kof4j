package dev.kof.compiler.js;

/** kof-runtime.mjs — web server (WEB001) + timers cooperativos. */
public final class JsRuntimeUiWeb {
    private JsRuntimeUiWeb() {
    }

    static final String UI_WEB_RUNTIME = """
            // ── Web runtime (WEB001) — GraalJS HttpServer com handler invoke
            // Handler lambda tem metodo invoke(); usamos Value para interop.
            const kofWebApps = new Map();
            let kofWebPort = 8080;
            let kofWebServer = null;
            let kofWebRunning = false;
            // WEB001-T1: fila criada LAZY no primeiro kofWebListen — o slice
            // ui-web é incluído por fallback em programas SEM web e a criação
            // top-level (Java.type) quebraria a execução desses programas.
            let kofWebPending = null;
            // WEB001-T1 (13/09): request corrente p/ os helpers de contexto
            // Kof (param/query/header/body/method/path). GraalJS executa o
            // handler numa thread por dispatch — o HttpServer default roda
            // o handler sequencialmente no dispatcher thread, e o handler
            // Kof roda sincrono dentro dele: contexto corrente e seguro.
            let kofWebRequest = null;

            // ── WEB001-T1: encoder UTF-8 puro JS (interop de INSTÂNCIA Java
            // host não expõe métodos nesta build — statics e Java.to ok) ──
            function kofWebBytesToUtf8(raw) {
                if (raw == null || raw.length === 0) return "";
                const out = [];
                for (let i = 0; i < raw.length; i++) {
                    const b = raw[i] & 0xFF;
                    out.push(b);
                }
                let result = "";
                let i = 0;
                while (i < out.length) {
                    const b = out[i];
                    let cp = 0, len = 0;
                    if (b < 0x80) { cp = b; len = 1; }
                    else if ((b & 0xE0) === 0xC0) { cp = b & 0x1F; len = 2; }
                    else if ((b & 0xF0) === 0xE0) { cp = b & 0x0F; len = 3; }
                    else { cp = b & 0x07; len = 4; }
                    for (let k = 1; k < len && i + k < out.length; k++) cp = (cp << 6) | (out[i + k] & 0x3F);
                    i += len;
                    result += String.fromCodePoint(cp);
                }
                return result;
            }

            function kofWebUtf8Bytes(text) {
                const out = [];
                for (let i = 0; i < text.length; i++) {
                    let c = text.charCodeAt(i);
                    if (c >= 0xD800 && c <= 0xDBFF && i + 1 < text.length) {
                        const c2 = text.charCodeAt(i + 1);
                        if (c2 >= 0xDC00 && c2 <= 0xDFFF) { c = 0x10000 + ((c - 0xD800) << 10) + (c2 - 0xDC00); i++; }
                    }
                    if (c < 0x80) out.push(c);
                    else if (c < 0x800) { out.push(0xC0 | (c >> 6), 0x80 | (c & 63)); }
                    else if (c < 0x10000) { out.push(0xE0 | (c >> 12), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63)); }
                    else { out.push(0xF0 | (c >> 18), 0x80 | ((c >> 12) & 63), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63)); }
                }
                return Java.to(out, "byte[]");
            }
            function kofWebHandleRequest(exchange) {
                const path = exchange.getRequestURI().getPath();
                const method = exchange.getRequestMethod();
                // WEB001-T1 (13/09): match em TODOS os apps registrados — rotas
                // vivem em app.handlers (chave "METHOD:path"), idem JVM dispatch.
                let handler = null;
                let matched = null;
                for (const [, app] of kofWebApps) {
                    if (handler) break;
                    handler = app.handlers.get(method + ":" + path);
                    if (handler) break;
                    for (const [key, h] of app.handlers) {
                        const sep = key.indexOf(":");
                        const m = key.slice(0, sep);
                        const r = key.slice(sep + 1);
                        if (m !== method || !r.includes(":")) continue;
                        const rSegs = r.split("/"), pSegs = path.split("/");
                        if (rSegs.length !== pSegs.length) continue;
                        const params = new Map();
                        let ok = true;
                        for (let i = 0; i < rSegs.length; i++) {
                            if (rSegs[i].startsWith(":")) params.set(rSegs[i].slice(1), decodeURIComponent(pSegs[i]));
                            else if (rSegs[i] !== pSegs[i]) { ok = false; break; }
                        }
                        if (ok) { handler = h; matched = params; break; }
                    }
                }
                if (!handler) {
                    exchange.sendResponseHeaders(404, 0);
                    exchange.getResponseBody().close();
                    return;
                }
                try {
                    // WEB001-T1 (13/09): body = stream do request lido inteiro
                    // e decodificado UTF-8 (métodos de instância em objetos
                    // host RECEBIDOS funcionam; a criação new JString() dentro
                    // do JS não expõe membros nesta build — por isso o decoder
                    // puro JS kofWebUtf8Bytes para a resposta).
                    let body = null;
                    {
                        const is = exchange.getRequestBody();
                        const raw = is.readAllBytes();
                        body = kofWebBytesToUtf8(raw);
                        is.close();
                    }
                    kofWebRequest = {
                        method: method,
                        path: path,
                        query: exchange.getRequestURI().getQuery(),
                        headers: exchange.getRequestHeaders(),
                        body: body,
                        paramsMap: matched || new Map()
                    };
                    const ctx = {
                        request: {
                            method: method,
                            path: path,
                            query: exchange.getRequestURI().getQuery(),
                            headers: exchange.getRequestHeaders()
                        },
                        body: body,
                        response: {
                            status: function(code, text) {
                                const bytes = text == null ? [] : kofWebUtf8Bytes(text);
                                exchange.sendResponseHeaders(code, bytes.length);
                                const os = exchange.getResponseBody();
                                os.write(bytes);
                                os.close();
                            },
                            header: function(name, value) {
                                exchange.getResponseHeaders().set(name, value);
                            }
                        }
                    };
                    // WEB001-T1 (13/09): idem JVM (JvmRuntimeWebDispatch) — o
                    // RETORNO do handler é o body 200; null/undefined → 404.
                    const result = (typeof handler.invoke === 'function') ? handler.invoke(ctx)
                                 : (typeof handler === 'function' ? handler(ctx) : undefined);
                    if (result === null || result === undefined) {
                        const nf = '{"error": "not found"}';
                        exchange.sendResponseHeaders(404, nf.length);
                        const os0 = exchange.getResponseBody();
                        os0.write(kofWebUtf8Bytes(nf));
                        os0.close();
                    } else if (typeof result === 'object' && result !== null && typeof result.status === 'function') {
                        // ctx de resposta já escrito pelo handler (response.status)
                    } else {
                        const text = String(result);
                        const bytes = kofWebUtf8Bytes(text);
                        exchange.sendResponseHeaders(200, bytes.length);
                        const os1 = exchange.getResponseBody();
                        os1.write(bytes);
                        os1.close();
                    }
                } catch(e) {
                    console.log("kofWeb handler error: " + e);
                    const msg = String(e);
                    exchange.sendResponseHeaders(500, msg.length);
                    const os = exchange.getResponseBody();
                    os.write(kofWebUtf8Bytes(msg));
                    os.close();
                } finally {
                    kofWebRequest = null;
                }
            }

            // ── WEB001-T1: helpers de contexto (idem JVM kof_web_*) ──
            export function kofWebParam(name) {
                if (!kofWebRequest) return "";
                const v = kofWebRequest.paramsMap.get(String(name));
                return v === undefined ? "" : v;
            }
            export function kofWebQuery(name) {
                if (!kofWebRequest || !kofWebRequest.query) return null;
                for (const kv of String(kofWebRequest.query).split("&")) {
                    const eq = kv.indexOf("=");
                    if (eq > 0 && kv.slice(0, eq) === String(name))
                        return decodeURIComponent(kv.slice(eq + 1));
                }
                return null;
            }
            export function kofWebHeader(name) {
                if (!kofWebRequest) return null;
                const v = kofWebRequest.headers.getFirst(String(name));
                return v === undefined || v === null ? null : String(v);
            }
            export function kofWebBody() {
                return kofWebRequest && kofWebRequest.body != null ? String(kofWebRequest.body) : "";
            }
            export function kofWebMethod() {
                return kofWebRequest ? kofWebRequest.method : "";
            }
            export function kofWebPath() {
                return kofWebRequest ? kofWebRequest.path : "";
            }
            export function kofWebStatus(code, text) {
                if (kofWebRequest && kofWebRequest.response) {
                    kofWebRequest.response.status(code, text);
                }
                return text == null ? "" : String(text);
            }
            export function kofWebHeaderSet(name, value) {
                if (kofWebRequest && kofWebRequest.response) {
                    kofWebRequest.response.header(String(name), String(value));
                }
                return value == null ? "" : String(value);
            }
            export function kofWebAppNew() {
                const app = {
                    handlers: new Map(),
                    _register: function(method, path, handler) {
                        this.handlers.set(method + ":" + path, handler);
                    }
                };
                const id = "app_" + kofWebApps.size;
                kofWebApps.set(id, app);
                return id;
            }
            export function kofWebRoute(appId, method, path, handler) {
                const app = kofWebApps.get(appId);
                if (!app) throw new Error("Invalid app handle: " + appId);
                app._register(method, path, handler);
                return 0;
            }
            export function kofWebListen(appId, port) {
                const app = kofWebApps.get(appId);
                if (!app) throw new Error("Invalid app handle: " + appId);
                if (kofWebPending === null) {
                    kofWebPending = new (Java.type('java.util.concurrent.LinkedBlockingQueue'))();
                }
                kofWebPort = port | 0 || 8080;
                const HttpServer = Java.type('com.sun.net.httpserver.HttpServer');
                const InetSocketAddress = Java.type('java.net.InetSocketAddress');
                kofWebServer = HttpServer.create(new InetSocketAddress(kofWebPort), 0);
                for (const [key, handler] of app.handlers) {
                    const [method, path] = key.split(":");
                    // WEB001-T1 (13/09): Context GraalJS é thread-confined — o callback do
            // HttpServer roda na thread do dispatcher e NÃO pode executar JS.
            // O dispatcher só ENFILEIRA o exchange (Java host-side); kofWebListen
            // desfile e processa cada request na main thread (event-loop).
                            // WEB001-T1: handler 100% Java (KofJsWebQueue) — callback JS rodaria
                // na thread do dispatcher e o Context GraalJS é thread-confined.
                // HttpServer não tem matching de :params — registra o PREFIXO
                // estático (ex.: "/u/:id" → "/u/"); match real no kofWebHandleRequest.
                const QueueHandler = Java.type('dev.kof.runtime.KofJsWebQueue');
                let ctxPath = path;
                if (ctxPath.includes(":")) {
                    ctxPath = ctxPath.slice(0, ctxPath.indexOf(":"));
                    if (!ctxPath.endsWith("/")) ctxPath = ctxPath + "/";
                }
                kofWebServer.createContext(ctxPath, new QueueHandler(kofWebPending));
                }
                kofWebServer.setExecutor(null);
                kofWebServer.start();
                // WEB001-T1 (13/09): idem JVM — listen é BLOQUEANTE. O contexto
                // GraalJS é thread-confined: requests processados AQUI (main
                // thread) a partir da fila que o dispatcher enche.
                kofWebRunning = true;
                const Thread_ = Java.type('java.lang.Thread');
                while (kofWebRunning) {
                    let ex = null;
                    try {
                        ex = kofWebPending.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (ie) {
                        continue;
                    }
                    if (ex !== null) kofWebHandleRequest(ex);
                }
                return 0;
            }

            export function kofEnumValueOf(values, name) {
                if (values != null && name != null) {
                    for (const v of values) {
                        if (v === name) return v;
                    }
                }
                return null;
            }

            export function kofNow() {
                return Date.now();
            }

            export function kofTimeNow() {
                return Date.now();
            }

            // ── kof.time (STDLIB S7e) — hoje/formato UTC (D-STDLIB) ──────
            // D1: UTC-only (getUTC* — Date.now() é epoch UTC). D4: invalidade
            // => "". D5: isToday = igualdade com a data UTC de now(). Serial =
            // MESMO epochDay do kofTimeEpochDay acima (addDays/diffDays).
            export function kofTimeTodayIso() {
                const ed = Math.floor(Date.now() / 86400000);
                const c = kofTimeCivilFromEpochDay(ed);
                return kofTimePut4(c[0]) + "-" + kofTimePut2(c[1]) + "-" + kofTimePut2(c[2]);
            }
            export function kofTimeFormatDateIso(year, month, day) {
                if (year < 1 || year > 9999 || month < 1 || month > 12) return "";
                if (day < 1 || day > kofTimeDaysInMonth(year, month)) return "";
                return kofTimePut4(year) + "-" + kofTimePut2(month) + "-" + kofTimePut2(day);
            }
            export function kofTimeIsToday(year, month, day) {
                if (year < 1 || year > 9999 || month < 1 || month > 12) return false;
                if (day < 1 || day > kofTimeDaysInMonth(year, month)) return false;
                return kofTimeFormatDateIso(year, month, day) === kofTimeTodayIso();
            }
            function kofTimeCivilFromEpochDay(z) {
                z += 719468;
                const era = Math.floor(z / 146097);
                const doe = z - era * 146097;
                const yoe = Math.floor((doe - Math.floor(doe / 1460) + Math.floor(doe / 36524) - Math.floor(doe / 146096)) / 365);
                const y = yoe + era * 400;
                const doy = doe - (365 * yoe + Math.floor(yoe / 4) - Math.floor(yoe / 100));
                const mp = Math.floor((5 * doy + 2) / 153);
                const d = doy - Math.floor((153 * mp + 2) / 5) + 1;
                const m = mp < 10 ? mp + 3 : mp - 9;
                return [m <= 2 ? y + 1 : y, m, d];
            }
            function kofTimePut4(v) {
                return String(Math.floor(v / 1000) % 10)
                    + String(Math.floor(v / 100) % 10)
                    + String(Math.floor(v / 10) % 10)
                    + String(v % 10);
            }
            function kofTimePut2(v) {
                return String(Math.floor(v / 10) % 10) + String(v % 10);
            }

            // ── kof.time (STDLIB S7f) — hoursBetween (D3) ─────────────────
            // floor simétrico (truncado a zero, como daysBetween); datas
            // inválidas/hora fora de 0..23 => 0 (paridade wedge).
            export function kofTimeHoursBetween(y1, m1, d1, h1, y2, m2, d2, h2) {
                if (y1 < 1 || y1 > 9999 || m1 < 1 || m1 > 12) return 0;
                if (y2 < 1 || y2 > 9999 || m2 < 1 || m2 > 12) return 0;
                if (d1 < 1 || d1 > kofTimeDaysInMonth(y1, m1)) return 0;
                if (d2 < 1 || d2 > kofTimeDaysInMonth(y2, m2)) return 0;
                if (h1 < 0 || h1 > 23 || h2 < 0 || h2 > 23) return 0;
                const hours1 = kofTimeEpochDay(y1, m1, d1) * 24 + h1;
                const hours2 = kofTimeEpochDay(y2, m2, d2) * 24 + h2;
                const diff = hours2 - hours1;
                return (diff < -2147483648 || diff > 2147483647) ? 0 : diff;
            }

            // ── kof.time (STDLIB S7g) — parseDateIso (D4) ─────────────────
            // "YYYY-MM-DD" estrito (10 chars, hífens 4/7, dígitos, data
            // válida) -> serial daysFromEpoch; inválido => 0. MESMO serial de
            // hoursBetween (epochDay*24+h) — recomposição fecha.
            export function kofTimeParseDateIso(iso) {
                if (typeof iso !== "string" || iso.length !== 10) return 0;
                if (iso.charAt(4) !== "-" || iso.charAt(7) !== "-") return 0;
                for (let i = 0; i < 10; i++) {
                    if (i === 4 || i === 7) continue;
                    const c = iso.charAt(i);
                    if (c < "0" || c > "9") return 0;
                }
                const y = parseInt(iso.substring(0, 4), 10);
                const m = parseInt(iso.substring(5, 7), 10);
                const d = parseInt(iso.substring(8, 10), 10);
                if (y < 1 || y > 9999 || m < 1 || m > 12) return 0;
                if (d < 1 || d > kofTimeDaysInMonth(y, m)) return 0;
                return kofTimeEpochDay(y, m, d);
            }

            // ── kof.time (STDLIB S7h) — tzOffsetSeconds (D1) ──────────────
            // Fuso do HOST: Date.getTimezoneOffset() = minutos A OESTE do
            // UTC (São Paulo = +180) => segundos leste+ = -min*60 (paridade
            // JVM ZoneOffset.systemDefault().getTotalSeconds()).
            export function kofTimeTzOffsetSeconds() {
                return -(new Date().getTimezoneOffset()) * 60;
            }

            // ── kof.time (STDLIB S7-wedge) — calendário civil ─────────────
            export function kofTimeIsLeapYear(year) {
                if (year < 1) return 0;
                return (year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)) ? 1 : 0;
            }
            export function kofTimeDaysInMonth(year, month) {
                if (year < 1 || month < 1 || month > 12) return 0;
                const dim = [31,28,31,30,31,30,31,31,30,31,30,31];
                if (month === 2 && kofTimeIsLeapYear(year)) return 29;
                return dim[month - 1];
            }
            function kofTimeEpochDay(year, month, day) {
                const y = year - (month <= 2 ? 1 : 0);
                const era = Math.floor(y / 400);
                const yoe = y - era * 400;
                const mp = month + (month > 2 ? -3 : 9);
                const doy = Math.floor((153 * mp + 2) / 5) + day - 1;
                const doe = yoe * 365 + Math.floor(yoe / 4) - Math.floor(yoe / 100) + doy;
                return era * 146097 + doe - 719468;
            }
            function kofTimeValidDate(y, m, d) {
                if (y < 1 || y > 9999 || m < 1 || m > 12) return false;
                return d >= 1 && d <= kofTimeDaysInMonth(y, m);
            }
            export function kofTimeDayOfWeek(year, month, day) {
                if (!kofTimeValidDate(year, month, day)) return 0;
                const ed = kofTimeEpochDay(year, month, day);
                return ((ed % 7) + 7 + 3) % 7 + 1;   // floorMod(ed+3, 7) + 1
            }
            export function kofTimeIsWeekend(year, month, day) {
                return kofTimeDayOfWeek(year, month, day) >= 6 ? 1 : 0;
            }
            export function kofTimeDaysBetween(y1, m1, d1, y2, m2, d2) {
                if (!kofTimeValidDate(y1, m1, d1) || !kofTimeValidDate(y2, m2, d2)) return 0;
                return kofTimeEpochDay(y2, m2, d2) - kofTimeEpochDay(y1, m1, d1);
            }
            // STDLIB S7b — data ISO (String) add/diff. MESMO algoritmo civil
            // do wedge (época de Hinnant + inversa), SEM Date (evita DST e o
            // parse de ano 2-dígitos) => paridade byte-idêntica JVM/JS/Native.
            // Inválido => "" (add) / 0 (diff) — política "invalid => 0".
            function kofTimeCivilFromEpoch(ed) {
                const floor = (a, b) => Math.floor(a / b);
                const z = ed + 719468;
                const era = z >= 0 ? floor(z, 146097) : floor(z - 146096, 146097);
                const doe = z - era * 146097;                              // [0, 146096]
                const yoe = floor(doe - floor(doe, 1460) + floor(doe, 36524) - floor(doe, 146096), 365);
                const y = yoe + era * 400;
                const doy = doe - (365 * yoe + floor(yoe, 4) - floor(yoe, 100)); // [0, 365]
                const mp = floor(5 * doy + 2, 153);
                const d = doy - floor(153 * mp + 2, 5) + 1;
                const m = mp + (mp < 10 ? 3 : -9);
                return { y: y + (m <= 2 ? 1 : 0), m: m, d: d };
            }
            function kofTimeParseIso(s) {
                if (typeof s !== "string" || s.length !== 10) return null;
                if (s.charCodeAt(4) !== 45 || s.charCodeAt(7) !== 45) return null;
                const y = parseInt(s.slice(0, 4), 10);
                const m = parseInt(s.slice(5, 7), 10);
                const d = parseInt(s.slice(8, 10), 10);
                if (isNaN(y) || isNaN(m) || isNaN(d) || !kofTimeValidDate(y, m, d)) return null;
                return { y: y, m: m, d: d };
            }
            function kofTimePad2(n) { return (n < 10 ? "0" : "") + n; }
            function kofTimePad4(n) {
                let s = "" + n;
                while (s.length < 4) s = "0" + s;
                return s;
            }
            export function kofTimeAddDays(iso, days) {
                const a = kofTimeParseIso(iso);
                if (!a) return "";
                const r = kofTimeCivilFromEpoch(kofTimeEpochDay(a.y, a.m, a.d) + days);
                if (r.y < 1 || r.y > 9999) return "";
                return kofTimePad4(r.y) + "-" + kofTimePad2(r.m) + "-" + kofTimePad2(r.d);
            }
            export function kofTimeDiffDays(iso1, iso2) {
                const a = kofTimeParseIso(iso1);
                const b = kofTimeParseIso(iso2);
                if (!a || !b) return 0;
                return kofTimeEpochDay(b.y, b.m, b.d) - kofTimeEpochDay(a.y, a.m, a.d);
            }

            export function kofTimeSleep(ms) {
                const end = Date.now() + ms;
                // bombeia a fila cooperativa de timers durante o wait (GraalJS
                // single-thread: sem isso, time.interval nunca dispara)
                while (Date.now() < end) {
                    kofTimePump();
                }
                kofTimePump();
            }

            // ── Cooperative timers (TIME001 fechado): GraalJS não tem
            // event loop nativo nem setInterval, então os jobs vivem numa
            // fila bombeada por kofTimeSleep (que já bloqueia). Em browser/
            // Node, onde setInterval existe, os timers disparam assíncronos.
            const kofTimeJobs = new Map();
            const kofTimeSeq = { value: 0 };
            function kofTimeRunJob(fn) {
                if (typeof fn.invoke === 'function') fn.invoke();
                else if (typeof fn === 'function') fn();
            }
            export function kofTimeInterval(ms, fn) {
                if (typeof setInterval === 'function') {
                    return "n" + String(setInterval(() => kofTimeRunJob(fn), ms));
                }
                const id = "c" + (++kofTimeSeq.value);
                kofTimeJobs.set(id, { ms: ms, run: () => kofTimeRunJob(fn), next: Date.now() + ms });
                return id;
            }
            function kofTimePump() {
                const now = Date.now();
                for (const [id, job] of kofTimeJobs) {
                    if (now >= job.next) {
                        job.next = now + job.ms;
                        job.run();
                    }
                }
            }
            export function kofTimeCancel(id) {
                const key = String(id);
                if (key.charAt(0) === "n") {
                    if (typeof clearInterval === 'function') clearInterval(Number(key.substring(1)));
                    return;
                }
                kofTimeJobs.delete(key);
            }

            """;

}
