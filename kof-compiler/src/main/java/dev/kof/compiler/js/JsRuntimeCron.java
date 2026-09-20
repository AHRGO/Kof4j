package dev.kof.compiler.js;

/**
 * kof-runtime.mjs — parser cron do `kof.scheduler.at` (CRON001).
 *
 * Fatia separada do ui-web (ratchet ≤500): o bloco ui-web tinha cruzado
 * 600 linhas. Concatenada a {@code JsRuntimeUiWeb.UI_WEB_RUNTIME} na
 * montagem do runtime (mesmo padrão do ui-web-sse).
 */
final class JsRuntimeCron {

    private JsRuntimeCron() {}

    static String cronRuntime() {
        return """
            // ── kof.scheduler.at — duração idiomática (D-SCHED-DURATION)
            // Além do cron de 5 campos, `at` aceita "30m", "1d&30m" etc.
            // (termo = dígitos + unidade; unidade ∈ { s, m, h, d, M, a };
            // composição com '&'). s/m/h/d fixos em ms; M/a avançam o
            // calendário UTC com clamp no último dia do mês alvo — mesmo
            // algoritmo do JVM (paridade byte-idêntica via golden probe).
            const KOF_DUR_UNITS = { ms: 1, s: 1000, m: 60000, h: 3600000, d: 86400000 };
            export function kofDurationParse(expr) {
                if (expr === null || expr === undefined) return null;
                const e = String(expr).trim();
                if (e.length === 0) return null;
                let fixed = 0;
                let months = 0;
                let years = 0;
                for (const raw of e.split("&")) {
                    const t = raw.trim();
                    const m = /^(\\d+)(ms|[smhdMa])$/.exec(t);
                    if (m === null) return null;
                    const n = Number(m[1]);
                    if (!(n > 0)) return null;
                    const u = m[2];
                    if (u === "M") months += n;
                    else if (u === "a") years += n;
                    else {
                        fixed += n * KOF_DUR_UNITS[u];
                        if (!(fixed < 9007199254740993)) return null;
                    }
                }
                return { fixed: fixed, months: months, years: years };
            }
            function kofDurationDim(y, m1) {
                if (m1 === 2 && ((y % 4 === 0 && y % 100 !== 0) || y % 400 === 0)) return 29;
                return [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][m1 - 1];
            }
            function kofDurationNextFrom(dur, anchor) {
                if (dur.months === 0 && dur.years === 0) return anchor + dur.fixed;
                const d = new Date(anchor);
                // total de meses desde o ano 0 (mês 0-based); clamp no
                // último dia do mês alvo — mesmo algoritmo do JVM.
                const mo0 = d.getUTCFullYear() * 12 + d.getUTCMonth() + dur.years * 12 + dur.months;
                const y2 = Math.floor(mo0 / 12);
                const m2 = mo0 - y2 * 12;
                const da2 = Math.min(d.getUTCDate(), kofDurationDim(y2, m2 + 1));
                const base = Date.UTC(y2, m2, da2, d.getUTCHours(), d.getUTCMinutes(), d.getUTCSeconds(), d.getUTCMilliseconds());
                return base + dur.fixed;
            }
            export function kofDurationNextDelayMs(expr, nowMillis) {
                const dur = kofDurationParse(expr);
                if (dur === null) throw new Error("duration: not a duration expression: " + expr);
                return kofDurationNextFrom(dur, nowMillis) - nowMillis;
            }
            // ── kof.scheduler.at — cron real (CRON001) ──────────────
            // 5 campos em UTC (paridade com o JVM). BigInt como máscara de
            // bits (o minuto precisa de 60 bits; Number só tem 32 em bitwise).
            // Cron inválido lança (alto, nunca silencioso — R6).
            export function kofTimeScheduleCron(cron, fn) {
                const dur = kofDurationParse(cron);
                const id = "a" + (++kofTimeSeq.value);
                const now = Date.now();
                if (dur !== null) {
                    const anchor = now;
                    kofTimeJobs.set(id, {
                        dur: dur,
                        anchor: anchor,
                        run: () => kofTimeRunJob(fn),
                        // via kofDurationNextDelayMs (= API pública): a fatia
                        // só sobrevive ao tree-shake se for alcançável.
                        next: now + kofDurationNextDelayMs(cron, now)
                    });
                    return id;
                }
                const fields = kofCronParse(cron);
                kofTimeJobs.set(id, {
                    cron: fields,
                    run: () => kofTimeRunJob(fn),
                    next: now + kofCronNextDelayMs(fields, now)
                });
                return id;
            }
            function kofCronParse(cron) {
                if (cron === null || cron === undefined) throw new Error("cron: null expression");
                const f = String(cron).trim().split(/\\s+/);
                if (f.length !== 5) throw new Error("cron: expected 5 fields, got " + f.length + ": " + cron);
                const out = [0n, 0n, 0n, 0n, 0n, 0, 0];
                out[0] = kofCronField(f[0], 0, 59, "minute");
                out[1] = kofCronField(f[1], 0, 23, "hour");
                out[2] = kofCronField(f[2], 1, 31, "day-of-month");
                out[3] = kofCronField(f[3], 1, 12, "month");
                out[4] = kofCronField(f[4], 0, 7, "day-of-week");
                if ((out[4] & (1n << 7n)) !== 0n) out[4] |= 1n;
                out[4] &= ~(1n << 7n);
                out[5] = f[2] === "*" ? 0 : 1;
                out[6] = f[4] === "*" ? 0 : 1;
                return out;
            }
            function kofCronField(spec, min, max, name) {
                let mask = 0n;
                for (const part of String(spec).split(",")) {
                    if (part.length === 0) throw new Error("cron: empty " + name + " field");
                    let step = 1;
                    let range = part;
                    const slash = part.indexOf("/");
                    if (slash >= 0) {
                        range = part.substring(0, slash);
                        step = kofCronInt(part.substring(slash + 1), name);
                        if (step <= 0) throw new Error("cron: bad step in " + name + ": " + part);
                    }
                    let lo;
                    let hi;
                    if (range === "*") {
                        lo = min;
                        hi = max;
                    } else {
                        const dash = range.indexOf("-");
                        if (dash >= 0) {
                            lo = kofCronInt(range.substring(0, dash), name);
                            hi = kofCronInt(range.substring(dash + 1), name);
                        } else {
                            lo = kofCronInt(range, name);
                            hi = slash >= 0 ? max : lo;
                        }
                    }
                    if (lo < min || hi > max || lo > hi) {
                        throw new Error("cron: " + name + " out of range: " + part);
                    }
                    for (let v = lo; v <= hi; v += step) mask |= 1n << BigInt(v);
                }
                return mask;
            }
            function kofCronInt(s, name) {
                const n = Number(String(s).trim());
                if (!Number.isInteger(n)) throw new Error("cron: bad " + name + " value: " + s);
                return n;
            }
            function kofCronMatches(f, d) {
                if (((f[0] >> BigInt(d.getUTCMinutes())) & 1n) === 0n) return false;
                if (((f[1] >> BigInt(d.getUTCHours())) & 1n) === 0n) return false;
                if (((f[3] >> BigInt(d.getUTCMonth() + 1)) & 1n) === 0n) return false;
                const domOk = ((f[2] >> BigInt(d.getUTCDate())) & 1n) !== 0n;
                const dowOk = ((f[4] >> BigInt(d.getUTCDay())) & 1n) !== 0n;
                if (f[5] === 1 && f[6] === 1) return domOk || dowOk;
                if (f[5] === 1) return domOk;
                if (f[6] === 1) return dowOk;
                return true;
            }
            export function kofCronNextDelayMs(cron, nowMillis) {
                const f = typeof cron === 'string' ? kofCronParse(cron) : cron;
                let t = Math.floor(nowMillis / 60000) * 60000 + 60000;
                for (let i = 0; i < 366 * 24 * 60 * 4; i++) {
                    if (kofCronMatches(f, new Date(t))) return t - nowMillis;
                    t += 60000;
                }
                return 60000;
            }
            """;
    }
}
