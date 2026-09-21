[English](db-parity-plan.md) | [Português](db-parity-plan.pt_BR.md)

# Plano de Paridade de DB — todo alvo aceita todo scheme (mariadb, mysql, sqlite, mongodb, …)

> **EM DESENVOLVIMENTO** — aberto 21/09/2026 a partir da diretiva da mantenedora
> (adendo `D-DB-GAPS`, `DECISIONS.md`). O plano é a fila; cada fatia pousa com
> prova (Q0–Q7) e este doc só **move para `docs/stdlib/`** quando a paridade
> estiver completa.

**Dono:** frente DB/ORM (dono a nomear) · **Registros/plano:** lane docs/plataforma
**Branch:** `beta-0.5.0` · **Estado:** fila aberta, S0 não iniciada

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
| `mariadb://` | ✅ JDBC (driver) | ✅ JVM | ✅ JDBC host | ❌ não parseado (compatível mysql-wire) |
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

- **S0 — diagnóstico interino honesto (limpa o §421).** No Native,
  `kof_db_connect` deve **recusar** um scheme que não implementa com o código
  documentado, em vez de registrar um handle tipo-0. Transitório: removido por
  scheme conforme S1–S4 pousam. *Prova:*
  `MakealiveDbStateE2ETest.stateSurfaceNativeNeverSilent` verde + um pin de que um
  scheme não suportado dá o diagnóstico, não o crash tardio do `.Lorm_conn`.
- **S1 — `mariadb://` = alias mysql-wire (Native, 3 arcos).** Parsear `mariadb://`
  no mesmo caminho de `mysql://`; `kof_db_type` reporta a família mysql.
  *Prova:* E2E nativo connect + query em x86-64 (cross guardado por toolchain).
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
