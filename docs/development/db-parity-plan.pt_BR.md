[English](db-parity-plan.md) | [Português](db-parity-plan.pt_BR.md)

# Plano de Paridade de DB — todo alvo aceita todo scheme (mariadb, mysql, sqlite, mongodb, …)

> **EM DESENVOLVIMENTO** — aberto 21/09/2026 a partir da diretiva da mantenedora
> (adendo `D-DB-GAPS`, `DECISIONS.md`). O plano é a fila; cada fatia pousa com
> prova (Q0–Q7) e este doc só **move para `docs/stdlib/`** quando a paridade
> estiver completa.

**Dono:** lane `gaps-db` (repassada 21/09 por ordem da mantenedora, sob `D-DB-PARITY-OWNER`; S0/S1 autorizadas) · **Registros/plano:** lane docs/plataforma
**Branch:** `beta-0.5.0` · **Estado:** S0 ✅ FEITO (21/09, sessão 9092) — recusa nativa honesta de scheme não suportado com o código nomeado `DB001`, mais link-by-use real (sem link de `libmariadb` para literais não-mysql); **S1 ✅ FEITO no Native x86-64 (23/09, lane gaps-db)** — `mariadb://` é alias do wire `mysql://`; no cross segue `DB001` honesto até o wire mysql ser portado (R7); **S2 ✅ FEITO no JVM/JS/Android (23/09, lane gaps-db)** — driver JDBC ausente agora é diagnóstico `DB001` nomeado (falhas reais de conexão intactas); **S3/S4 ✅ FEITOS 23/09 (lane gaps-db)** — `mongodb://` real no JVM/Android e `DB001` declarado no JS/Native; `oracle` declarado (sem driver/servidor no host)

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

## Estado medido (corrigido 23/09/2026 — medição, não memória)

> A tabela anterior usava nomes de scheme crus como atalho para a **URL JDBC** no
> JVM/Android/JS. Isso era enganoso: `sqlite:`/`mysql://` são aceitos **como
> escritos** só no **Native**. No JVM/Android/JS o mesmo scheme precisa ser URL
> `jdbc:` (`jdbc:sqlite:`, `jdbc:mysql://`, …) — um scheme nu não-JDBC é `DB001`
> nomeado. `mongodb://` é a exceção (JVM/Android o tratam à parte).

| URL como escrita | JVM | Android | JS | Native (x86/riscv/aarch) |
|---|---|---|---|---|
| `jdbc:sqlite:` / `jdbc:h2:` | ✅ JDBC (driver) | ✅ JVM | ✅ JDBC host | — |
| `sqlite:` (nu) | ❌ `DB001` (use `jdbc:` no JVM/JS) | ❌ `DB001` | ❌ `DB001` | ✅ `sqlite3` link-by-use |
| `jdbc:mysql:` / `jdbc:mariadb:` / `jdbc:postgresql:` | ✅ JDBC (driver no cp) | ✅ JVM | ✅ JDBC host | — |
| `mysql://` / `mariadb://` (nu) | ❌ `DB001` (não é JDBC) | ❌ `DB001` | ❌ `DB001` | ⚠️ x86: wire `mysql://` + alias `mariadb://` (23/09); riscv/aarch `DB001` (wire não portado → R7) |
| `mongodb://` | ✅ real (driver via reflexão) | ✅ JVM | ❌ `DB001` — gap declarado (S3) | ❌ `DB001` |
| `jdbc:oracle:` | ✅ JDBC com driver no cp, senão `DB001` | ✅ JVM | ✅ JDBC host | — |
| qualquer outra / driver ausente | ❌ `DB001` nomeado (S2) | ❌ `DB001` nomeado | ❌ `DB001` nomeado | ❌ `DB001` nomeado (S0) |

- **JVM/Android/JS** aceitam qualquer URL **`jdbc:`** com driver no classpath; o
  delegate JS **é** o JDBC do host. Driver ausente agora é `DB001` **nomeado**
  (S2), nunca `SQLException` cru.
- **`mongodb://`** é real no JVM/Android (driver via reflexão) e `DB001`
  declarado no JS/Native (S3). O Native não vai criar servidor Mongo caseiro (R9).
- **Native** parseia só `sqlite:` e `mysql://`/`mariadb://`; qualquer outro scheme
  recusa com `DB001` nomeado no connect (S0). `kof_db_type` reserva
  **1=sqlite 2=mysql 3=oracle 4=mongo**.
- **Questão de design aberta (rule 6, NÃO é edição de agente):** JVM/Android/JS
  devem **normalizar** um scheme nu (`mysql://`, `sqlite:`) para seu equivalente
  `jdbc:` para que a *mesma URL* funcione em todo alvo? Hoje o chamador precisa
  escrever `jdbc:` no JVM/JS e a forma nua no Native. A normalização é frágil para
  credenciais (`mysql://user:pass@host` vs `?user=&password=`) — a mantenedora decide.
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
- **S2 — paridade de schemes JDBC JVM/JS/Android. ✅ FEITO 23/09 (lane gaps-db).**
  A medição por-driver já é provada pelo corpus E2E — `h2` (`KofDbE2ETest`
  execute/query/tipado), `sqlite` (`KofOrmE2ETest` `jdbc:sqlite:`, find/save/page
  no JVM), `mariadb` (`KofOrmE2ETest#mariadbCrud` + o oráculo Oracle JVM da F2d,
  servidor real), `postgres` (`KofOrmE2ETest#postgresCrud`, skip sem servidor) —
  tudo pelo mesmo caminho `DriverManager` para o qual JS/Android delegam. O que
  faltava era o **diagnóstico**: URL JDBC cujo driver está ausente vazava um
  `SQLException: No suitable driver` cru. Agora
  `JvmConfigRuntime.kof_db_connect/connect2` (JVM, e Android pelo mesmo runtime) e
  `KofJsDbBridge.connect/connect2` (delegate JS) mapeiam isso para o **nomeado**
  `DB001: no JDBC driver for this URL (add the driver to the classpath): <url>`,
  enquanto uma falha **real** de conexão (servidor fora / credencial ruim) passa
  **intacta** — nunca mascarada como `DB001`. *Prova:*
  `KofDbE2ETest#jvmMissingJdbcDriverNamesGapNotSilent` +
  `#jvmRealConnectionFailureIsNotRelabeledDb001` +
  `#jsMissingJdbcDriverNamesGapNotSilent` +
  `#jsRealConnectionFailureIsNotRelabeledDb001` (a do JVM VERMELHA no código
  antigo — `No suitable driver`); `KofDbE2ETest` 32/0F.
- **S3 — `mongodb://` interop-first (R9). ✅ FEITO 23/09 (lane gaps-db).**
  JVM/Android rodam de verdade: `KofOrmE2ETest#mongoCrud` (roundtrip ORM completo —
  save/find/where/count/saveAll/page/deleteAll — sobre o `mongodb-driver-sync`
  real). JS/Native são um `DB001` **declarado**, nunca silencioso:
  `KofJsDbBridge.connect/connect2` agora recusa `mongodb://` com
  `DB001: mongodb:// is not supported on the JS target yet (host driver bridge
  pending): <url>` (em vez da mensagem enganosa de "sem driver JDBC"), e o Native
  recusa via S0. No JVM, driver mongo ausente também é nomeado (`DB001: mongodb://
  needs the mongodb-driver-sync on the classpath`). Sem servidor caseiro (R9).
  *Prova:* `KofDbE2ETest#jsMongodbSchemeNamesGapNotSilent` +
  `#jvmMongodbMissingDriverNamesGap`; `KofOrmE2ETest#mongoCrud` (JVM real).
- **S4 — `jdbc:oracle:` (mesma rota do S3). ✅ FEITO 23/09 (lane gaps-db,
  declarado).** Não há driver/servidor Oracle neste host, e Oracle — como Mongo —
  nunca será servidor caseiro (R9). No JVM/Android/JS, `jdbc:oracle:` conecta com o
  driver no classpath, senão o `DB001` nomeado do S2 dispara; `oracle://` nu é
  `DB001` (não é URL JDBC). O Native recusa via S0. *Prova:* os testes de
  diagnóstico do S2 cobrem o caminho driver-ausente genericamente; nenhum E2E
  específico de servidor é possível aqui (declarado, não silencioso).

## Não-objetivos / invariantes

- Sem sintaxe nova, sem mudança de API; sem edição de semântica congelada
  (behavior freeze).
- Interop-first (R9): drivers/protocolos vêm de libs externas auditadas, nunca
  reimplementados. Native fecha por último (R7), cada fatia honesta enquanto isso
  (R6).
- Pool de conexões segue o item PLANNED separado do `DATABASE_VISION.md` (não faz
  parte da paridade de schemes).
