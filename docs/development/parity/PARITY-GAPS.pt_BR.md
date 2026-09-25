[English](PARITY-GAPS.md) | [Português](PARITY-GAPS.pt_BR.md)

# Ledger de paridade total — impeditivo da 0.5.0 (mantenedora 24/09)

> **MANDATO (mantenedora, 24/09): paridade total da plataforma é
> INDISPENSÁVEL para a release 0.5.0.** Um código de gap honesto (`PROC001`,
> `MEDIA001`, ...) é o jeito sancionado de RASTREAR uma face ausente — nunca o
> estado final sancionado. Cada linha abaixo é uma funcionalidade que hoje
> funciona em ALGUNS alvos e é gap nomeado/diagnosticado em outros. **A
> `0.5.0` não corta enquanto este ledger tiver QUALQUER linha aberta.**
>
> Regra do ledger (três estados, verificado por máquina via
> `check_release_050_gate.sh` → `full_parity`):
> - uma linha sai SOMENTE quando a funcionalidade compila E roda com paridade
>   byte/golden em TODOS os alvos (teste de prova nomeado, runner registrado);
> - fechos parciais movem as células de alvo da linha (nunca marcar uma linha
>   DONE parcialmente);
> - o ledger VAZIO (sem linhas) é o estado GREEN — `docs/development/parity/`
>   permanece (o contrato + a história), o corpo do arquivo encolhe para
>   "0 linhas abertas".
>
> "⏳ golden" = a face existe mas o golden cross (diff byte riscv64/aarch64 vs
> oráculo JVM) nunca foi medido — não medido NÃO é verde (Q5: sem falso verde).

## Linhas abertas (medidas 24/09 de `DomainGapCodesTest`, códigos de gap dos `Kof*.java`, tabela de paridade do `training/idioms/stdlib.md`, `docs/bugs-and-gaps/known-bugs.md`)

| # | Superfície | JVM/Script | Native x86-64 | Native riscv64/aarch64 | JS | Código | Fila / lane dona |
|---|------------|------------|----------------|--------------------------|----|--------|------------------|
| 1 | `process.run`/`spawn`/`exit` | ✅ | ❌ | ❌ | ✅ (KofJsRunner) | `PROC001` | lane native |
| 2 | `shell.cmd`/`run`/`runWith`/`pipeline`/`ok` | ✅ | ❌ | ❌ | ✅ (host runner) | `PROC001` | lane native |
| 3 | `ssh.cmd`/`run`/`ok` | ✅ | ❌ (sem dispatch) | ❌ | ❌ | (sem código de gap ainda — catalogar) | lane native/js |
| 4 | media: `Image.open`/`Audio.openWav`/`Video.open`/`Mic.record`/`list` | ✅ | ❌ | ❌ | ❌ | `MEDIA001`/`MEDIA003` | frente media |
| 5 | `mq.*` | ✅ | parcial (faces `MQ001`) | ⏳ golden | ⏳ | `MQ001` | lane infra |
| 6 | `gpu.*` (face JS) + golden cross | ✅ | ✅ | ⏳ golden | ❌ `GPU001` | `GPU001` | lanes gpu/native |
| 7 | `observability.*` golden cross + `OBS003` | ✅ | ✅ x86 | ⏳ golden | ⏳ (spans ✅, OBS003 travado) | `OBS003` | lane obs |
| 8 | `time.*` faces novas golden cross + `addDays`/`diffDays` cross | ✅ | ✅ x86 | ❌ `TIME002` | ⏳ (`collect` = `TIME004`) | `TIME002`/`TIME004` | lane stdlib |
| 9 | `cache.*`/`config.*`/`log.*` golden cross; `log` interpretador | ✅ (log ⏳ interp) | ✅ x86 | ⏳ golden + `CONF001` | ✅ | `CONF001` | lane stdlib |
| 10 | `math.pow` cross (estático, sem libc) | ✅ | ✅ (libm `-lm`) | ❌ `MATH001` | ✅ | `MATH001` | lane native cross |
| 11 | `strings.reverse` não-ASCII (UTF-16 vs byte) + `String.matches`/`replaceAll`/`replaceFirst`/`compareToIgnoreCase` | ✅ | ❌ `NAT-STR01`/`STR003` | ❌ `STR003` | ❌ `STR003` | `NAT-STR01`/`STR003` | lanes native/js |
| 12 | web T1 (faces do `kof.http.server`) no native/cross | ✅ | ⏳ | ❌ `WEB002`–`WEB006` | ✅ | `WEB00x` | lane web |
| 13 | faces de arquivo do `kof.io` no cross | ✅ | ✅ x86 | ❌ `NAT006`/`NAT007` | ✅ | `NAT006`/`NAT007` | lane native cross |
| 14 | família security no cross/native (faces bcrypt/argon2/keystore) | ✅ | parcial | ❌ `SECN001`/`003`/`004`/`005` | ⏳ | `SECN00x` | lane security |

> **As linhas 15 (`orm.*` nativo) e 16 (`db.*` nativo) foram FECHADAS em 24/09
> pela lane gaps-db (S5.5)** — o cross (riscv64/aarch64) eram as últimas células
> abertas; as provas estão na seção Fechadas.

## Já em paridade total (verificado — a revisão do que está FEITO, 24/09)

Medido 4/4 (JVM/Script ≡ Native x86-64 ≡ Native riscv64/aarch64 ≡ JS),
prova = casos std* do `ConformanceMatrixTest` + o E2E nomeado por linha:

- **Núcleo da linguagem:** operadores/precedência, `==` por conteúdo,
  exceções como String, estreitamento null-safe, expressões `if`/`switch`,
  pattern matching, closures, `spawn`/`await`/`poll`/`done`/`cancel`/
  `selectAny`/`awaitTimeout`, canais incl. riscv64/aarch64 (§423, §485),
  métodos default de interface (§248), interfaces genéricas + bridges
  covariante/primitiva (§355–357, §486), igualdade/hashCode de record por
  conteúdo em todo lugar (§104b-ii/§114), diagnósticos SEM.
- **Núcleo da stdlib (linhas da tabela de paridade com ✅ nas 4 colunas):**
  `math` (abs/sign/clamp/min/max/is*/sqrt/lerp/percentage/roundTo/parse*) —
  EXCETO `pow` (linha 10); `strings` (is*, count, capitalize/uncapitalize,
  reverse ASCII, toCamel/Pascal/Snake/Kebab, slugify, escapeHtml/Json,
  família de whitespace, dedent, repeat, truncate, indent, pad*) — EXCETO
  `reverse` não-ASCII (linha 11); `encoding` (hex, base64, base64Url, url);
  `net` (todos os 8); `uuid` (v4, v7, isUuid); `random` (todas as faces);
  `time` núcleo de calendário civil (isLeapYear, daysInMonth, dayOfWeek,
  daysBetween, isWeekend, isToday) — EXCETO o golden cross das faces novas
  (linhas 8/9); `validation` (docs BR, rede, cartão — todas as faces).
- **Coleções** (`List`/`Set`/`Map` API completa incl. `getOrDefault`,
  `putIfAbsent`, sort/indexOf/subList) — 4 alvos.
- **`json.encode`/`decode<T>`** — 4 alvos (o Native compõe em compile-time).
- **`kof.ui`** — cores/widgets/janelas nos 4 alvos (regra: a plataforma
  renderiza).
- **`String.toCharArray`** (linha 11, portado 24/09) — 4 alvos; array de code
  units UTF-16 (astral = high/low surrogate), paridade byte-a-byte
  JVM/x86/cross. Prova: `KofStringsTest#toCharArrayJvmJsNative` (JVM/JS/x86) +
  `NativeStringToCharArrayCrossTest` (riscv64/aarch64 sob qemu).

Uma entrada aqui só SAI quando a prova é nomeada; o runner
`ConformanceMatrixTest` re-executa a cada passagem do gate de release
(condição 1), então uma regressão REABRE a linha (zero regressão, regra 1
do freeze).

## Fechados (prova registrada aqui quando a linha esvazia)

- **Linha 15 — `orm.*` nativo (`kof_orm_*`)** — fechada 24/09 (lane gaps-db,
  S5.5). As 13 faces (`create`/`migrate`/`count`/`count_where`/`save`/
  `saveAll`/`find`/`all`/`where`/`where_op`/`page`/`delete`/`deleteAll`) rodam
  byte-idênticas em JVM + Native x86-64 + riscv64 + aarch64 sobre **os dois**
  SQLite e wire MySQL (os últimos gaps do cross — MySQL `save`/`saveAll`/
  `find`/`all`/`where`/`where_op`/`page` — landaram na S5.5, `RtB76`–`RtB81`,
  helpers `RtB78Helpers`/`RtB80Helpers`/`RtB81Helpers`); JS fechado 18/09
  (`KofJsOrmBridge`, mesmo SQL do `JvmOrmRuntime`). Prova: `KofOrmE2ETest`
  82/0F — incl. `crossNativeMariadb{Save,SaveAll,Find,All,Where,Page}MatchesOracles`
  (goldens byte JVM == x86-64 == riscv64 == aarch64) — + `NativeRiscvDbWireTest`
  41/0F + `NativeRiscvRuntimeSliceRegistryTest` 9/9.
- **Linha 16 — `db.*` paridade nativa query/prepared** — fechada 24/09 (lane
  gaps-db). O cross agora carrega o wire untyped completo (connect/handshake/
  COM_QUERY/execute/query/scalar, SQLite + MySQL, binds preparados — `RtB62`–
  `RtB73` + `RtB47b`); x86-64 real desde F1–F2; JS fechado 16/09 (DB001,
  `KofJsDbBridge`). Prova: `KofDbE2ETest` 40/0F + `NativeRiscvDbWireTest` 41/0F.

## Definition of done para TODA linha

1. A face compila no alvo (nenhum código de gap emitido).
2. Prova golden/E2E: saída byte-idêntica vs o oráculo JVM (cross sob qemu
   onde aplicável), teste nomeado no commit.
3. Tabela de paridade em `learn/39-stdlib.md`, `training/idioms/stdlib.md` e o
   capítulo do namespace sob `learn/stdlib/` atualizados no MESMO commit.
4. Linha removida deste ledger no MESMO commit + `full_parity` re-executado.
