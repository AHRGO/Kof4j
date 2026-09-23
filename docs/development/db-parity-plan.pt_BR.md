[English](db-parity-plan.md) | [Português](db-parity-plan.pt_BR.md)

# Plano de Paridade de DB — todo alvo aceita todo scheme (mariadb, mysql, sqlite, mongodb, …)

> **EM DESENVOLVIMENTO** — aberto 21/09/2026 a partir da diretiva da mantenedora
> (adendo `D-DB-GAPS`, `DECISIONS.md`). O plano é a fila; cada fatia pousa com
> prova (Q0–Q7) e este doc só **move para `docs/stdlib/`** quando a paridade
> estiver completa.

**Dono:** lane `gaps-db` (repassada 21/09 por ordem da mantenedora, sob `D-DB-PARITY-OWNER`; S0/S1 autorizadas) · **Registros/plano:** lane docs/plataforma
**Branch:** `beta-0.5.0` · **Estado:** S0 ✅ FEITO (21/09, sessão 9092) — recusa nativa honesta de scheme não suportado com o código nomeado `DB001`, mais link-by-use real (sem link de `libmariadb` para literais não-mysql); **S1 ✅ FEITO no Native x86-64 (23/09, lane gaps-db)** — `mariadb://` é alias do wire `mysql://`; no cross segue `DB001` honesto até o wire mysql ser portado (R7); S2–S4 seguem

---

## Por quê

Perguntada qual gap-code usar para a **aceitação silenciosa** de schemes não
suportados no Native (§421: `kof_db_connect` devolve um handle tipo-0 e a falha
aparece tarde no `.Lorm_conn`), a mantenedora respondeu **paridade total**: todo
alvo deve **aceitar** `mariadb`, `mysql`, `sqlite`, `mongodb`, … — sem endpoint de
gap-code. Isso generaliza o `D-DB-GAPS` DB-3 (estender MySQL a riscv64/aarch64)
numa meta de **paridade de schemes entre alvos**.

Isto **não** é mudança de superfície congelada: a API `kof.db`/`kof.orm` não muda —
o plano só **alarga o conjunto de URLs aceitas**, cada scheme **real** (R6). Um
scheme não suportado é **gap interino honesto** enquanto sua fatia pousa (R6: um
diagnóstico, nunca silêncio) — nunca recusa permanente, nunca aceite silencioso.

## Estado medido (21/09/2026 — medição, não memória)

| Scheme | JVM | Android | JS | Native (x86/riscv/aarch) |
|---|---|---|---|---|
| `sqlite:` | ✅ JDBC (driver) | ✅ JVM | ✅ JDBC host | ✅ `sqlite3` link-by-use |
| `mysql://` | ✅ JDBC | ✅ JVM | ✅ JDBC host | ⚠️ wire em andamento (`RuntimeDb2` — auth scramble/lenenc feito; handshake/prepared completo pendente) |
| `mariadb://` | ✅ JDBC (driver) | ✅ JVM | ✅ JDBC host | ⚠️ x86-64 ✅ alias do wire `mysql://` (23/09); riscv/aarch ainda `DB001` (wire mysql não portado → R7) |
| `mongodb://` | ✅ JDBC (driver) | ✅ JVM | ✅ JDBC host | ❌ não parseado |
| `oracle://` | ✅ JDBC (driver) | ✅ JVM | ✅ JDBC host | ❌ não parseado |
| `postgres://` | ✅ JDBC (driver) | ✅ JVM | ✅ JDBC host | ❌ não parseado |

- **JVM/Android/JS** aceitam qualquer URL JDBC com driver no classpath; o delegate
  JS **é** o JDBC do host. Driver ausente é `DB001` em runtime hoje.
- **Native** parseia só `sqlite:` e `mysql://`; qualquer outro scheme registra um
  handle tipo-0 (o aceite silencioso do §421). `kof_db_type` já reserva
  **1=sqlite 2=mysql 3=oracle 4=mongo**.
- Referência: `docs/stdlib/DATABASE_VISION.md` (Níveis 0–4, face Mongo no JVM,
  SQLite nativo real, MySQL/MariaDB nativo em andamento).

## Aceitação (definição de paridade)

Um scheme está **em paridade** quando o **mesmo programa Kof** (connect →
execute/query → roundtrip tipado) produz o **mesmo resultado observável** nos
quatro alvos, ou um **diagnóstico declarado `XXX00x`/`DB00x`** onde um alvo
genuinamente não consegue rodar (nunca divergência silenciosa). E2E cross-target
por scheme é a prova.

## Fatias

- **S0 — diagnóstico interino honesto (limpa o §421). ✅ FEITO 21/09 (sessão 9092).**
  No Native, `kof_db_connect`/`kof_db_connect2` agora **recusam** scheme fora de
  `sqlite:`/`mysql://` com o código nomeado (`DB001: unsupported db scheme …`)
  lançado no connect, em vez de handle nulo silencioso que só morria depois no
  `.Lorm_conn`. Falhas reais de conexão (auth / limite de slot / `sqlite3_open`)
  ficaram em `.Ldb_connect_bad`. x86-64 + riscv/aarch (`NativeRiscvAsmRtB47`,
  aarch pelo translator). Também tornei `NativeBackend.connectsToMysql` link-by-use
  real (só literal `mysql://`/`mariadb://`/`jdbc:mysql://` liga `libmariadb`), para
  a sonda linkar+rodar em host sem a lib. Transitório: removido por scheme
  conforme S1–S4 pousam. *Prova (medida, skipped=0):*
  `MakealiveDbStateE2ETest.stateSurfaceNativeNeverSilent` verde +
  `KofDbE2ETest.nativeUnsupportedSchemeNamesGapNotSilent` (rc≠0, `DB001`, sem
  `unknown db connection`) + `NativeDbSchemeRefusalAsmTest` (codegen
  determinístico) + `LinkByUseTest` 3/3.
- **S1 — `mariadb://` = alias mysql-wire (Native, 3 arcos). ✅ FEITO em x86-64
  23/09 (lane gaps-db).** O `kof_db_connect_inner` (`RuntimeDb2`) casa
  `mariadb://` e reusa o caminho `mysql://` (`r12 = schemeStart+2`, para o
  `leaq 8(%r12)` compartilhado cair após o scheme de 10 chars); `kof_db_type`
  reporta a família mysql (2), então `execute`/`query`/ORM pegam o wire. A
  mensagem `DB001` agora lista `mariadb://`. No riscv64/aarch64 tanto `mysql://`
  quanto `mariadb://` seguem recusando com `DB001` (wire mysql cross não
  portado — R7 honesto). *Prova:* `KofDbE2ETest#nativeMariadbAliasWireProtocol`
  — MariaDB real (KOF_MYSQL_PORT), nas duas formas `user:pass@host` e só-host,
  byte-idêntico `{"id":7,"name":"Alias"}`; `KofDbE2ETest` 28/0F +
  `NativeDbSchemeRefusalAsmTest` 2/2.
- **S2 — paridade de schemes JDBC JVM/JS/Android.** Medição por-driver (h2,
  sqlite, mysql, mariadb, postgres) + diagnóstico honesto de driver ausente com o
  código. *Prova:* E2E por scheme no JVM; prova do delegate JS/Android.
- **S3 — `mongodb://` interop-first (R9).** Driver/wire por trás da API Kof —
  **nunca** um servidor caseiro. Fechamento nativo conforme R7.
  *Prova:* roundtrip E2E, ou gap declarado com o código até o driver pousar.
- **S4 — `oracle://` (mesma rota do S3).**

## Não-objetivos / invariantes

- Sem sintaxe nova, sem mudança de API; sem edição de semântica congelada
  (behavior freeze).
- Interop-first (R9): drivers/protocolos vêm de libs externas auditadas, nunca
  reimplementados. Native fecha por último (R7), cada fatia honesta enquanto isso
  (R6).
- Pool de conexões segue o item PLANNED separado do `DATABASE_VISION.md` (não faz
  parte da paridade de schemes).
