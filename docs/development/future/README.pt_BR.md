[English](README.md) | [Português](README.pt_BR.md)

# docs/development/future/ — só plano futuro (zero código)

**Regra desta pasta:** aqui vive **apenas** o que é **plano para o futuro** —
documento de arquitetura/visão **sem código implementado** (ou com código que
é explicitamente não-entregável e fora do escopo atual).

> Se uma ideia **já está sendo implementada** (mesmo parcialmente), o doc
> correspondente **não fica aqui** — ele vive em `docs/` e documenta o **estado
> real** (o que já existe) + **como finalizar**. Assim quem lê sabe exatamente
> onde a coisa está e o que falta.

## Exemplo recente (01/09)

- `kof-native-risc-arm.md` **saiu daqui** para `docs/development/native-multiarch.md`: o
  plumbing (enum `Target.NATIVE_RISCV64/AARCH64`, CLI `native.risc/arm`,
  dispatch, cross-as/ld) já está no código, então o item é **em desenvolvimento**
  e passou a ser documentado com estado real + plano de finalização.

## O que fica aqui (só plano, sem código)

| Doc | Tema | Por que fica em `future/` |
|-----|------|---------------------------|
| `PLAN-MULTIPARADIGMA.md` | multiparadigma / pipelines funcionais e queries declarativas (`users.filter{...}.map{...}`), diagnóstico no HEAD 16/09 | **design puro, zero código no doc** (§8 não lista nenhum arquivo alterado); promovido só quando o primeiro incremento funcional landar (SYSTEMS fechado, R12) |
| `scoped-resources-plan.md` | RAII leve (TIER 2.4, `using`/`resource_scope`) | design puro — zero ocorrências de `resource_scope`/`kof_resource`/`using` no lexer/parser/runtime; gated por bump |
| `value-records-plan.md` | value records / tipos-valor first-class (TIER 2.7, `value record`) — aceito 16/09 (issue #275, `DECISIONS.md` §D-VALUE-RECORD) | **zero código** — a feature não existe no lexer/parser/backends; só design, barrado pelo R12 + autorização explícita para abrir a frente |
| `shell-plan.md` | `kof.shell` — shell idiomático sobre `kof.process` (TIER 2.2, Estágio 2) — **PROPOSTO 18/09, aguardando decisão de escopo da mantenedora (regra 6)** | **zero código** — `kof.shell` não está no lexer/parser/backends; é uma proposta de design que vira a linha solta do tracker 2.2 (`🔵`, dono `—`) numa fila de fatias executável, barrada por Q1–Q3. As afirmações de ABI são medidas contra a superfície `kof.process` existente (JVM/JS reais, Native `PROC001`) |
| `PLAN-BAREMETAL-BOOT.md` | **nativo → bare-metal/bootável** (costura HAL B-0…B-5: freestanding, UEFI, BIOS legado, MCU) — diretiva da mantenedora 15/09 | **zero código** — o runtime está fixado a syscalls Linux, o x86 precisa de `-lc`/`-dynamic-linker`, codegen de 32 bits ausente; classificado por `PLAN-TREE-SHAKING.md` §T3 ("embedded real = backend RTOS/bare-metal em si") — vai p/ `docs/` quando B-1 produzir um ELF sem dinâmica |
| `DECOMPILER.md` + `TRANSLATOR.md` + `LEGACY_MIGRATION.md` | plataforma de migração legado (decompiler/translator/IR/diff-testing) | **DESPRIORIZADO pela mantenedora 15/09 — de volta desde `docs/development/`.** O código fica em kof-cli (`DecompileTest` 67/67, `TranslateTest` 61/61); a FILA está pausada: promoção exige decisão explícita dela |
| ~~`planning-stdlib-array-returns.md`~~ → `docs/stdlib/DD-STDLIB-01-array-returns.md` | DD-STDLIB-01 | **FECHADO 13/09** — decisão 6a + implementação (`randomBytesHex`->String; choice=idiom), movido p/ docs/ |

> **`IMPLEMENTATION-UNIVERSAL-PLATFORM.md` saiu de `future/` em 17/09/2026** — promovido
> para `docs/development/IMPLEMENTATION-UNIVERSAL-PLATFORM.md` como **trabalho corrente**
> por decisão da mantenedora, que **sobrepõe o portão R12** (ver `DECISIONS.md`
> §D-UNIVERSAL). A visão/design não muda; o ponto de entrada é o Estágio 1
> (consolidação SYSTEMS) e as recomendações executáveis R1–R12.

## Histórico: o que saiu de `future/` antes (snapshot 12/09 — NÃO é o estado atual)

> **Não leia como estado atual** (atualizado 17/09). O cluster de migração
> **voltou para `future/` em 15/09** (linha acima), e os docs de plataforma/app-model
> foram **ratificados e consolidados no `DECISIONS.md` em 13/09** (os 6 arquivos de
> `decision-pending/` foram apagados). A tabela fica só como registro da queda de 12/09.

| Doc | Destino / casa atual |
|-----|------------------|
| `DECOMPILER.md`, `TRANSLATOR.md`, `LEGACY_MIGRATION.md` (os `IMPLEMENTATION_PLAN.md`+`ACTION_PLAN.md` foram FUNDIDOS p/ `roadmap.md` §23 e os `DIFFERENTIAL_TESTING.md`+`LEGACY_IR.md` p/ dentro do `LEGACY_MIGRATION.md`, tudo 13/09) | **de volta em `future/` 15/09** (despriorizado) — código+testes ficam em kof-cli (`kof inspect/decompile/translate/compare/migrate`, `Main.java`); contagem viva em `roadmap.md` §23 TIER 3–5 |
| `PLATFORM-PLAN.md` | `DECISIONS.md` §D-PLATFORM (ratificado 13/09; o arquivo foi apagado) |
| `APPLICATION_MODEL.md` | `DECISIONS.md` §D-APP (Q1–Q10 travados 13/09; o arquivo foi apagado) |
| `PLANNING-FUTURE-AUDIT.md`, `planning-future-reconcile.md` | `docs/audits/` (encerradas 13/09); R2→`DECISIONS.md` §D-APP/§D-PLATFORM, R5→cluster migração |
| `planning-finally-return.md` | `docs/decisions/DD-01-finally-return.md` (FECHADO 13/09; bug 45 CORRIGIDO) |
| `planning-stdlib-time-design.md` | `DECISIONS.md` §D-STDLIB (ratificado 13/09; o arquivo foi apagado) — `addDays`/`diffDays` nos 5 alvos (TIME002 11/09) |

## Quando mover de `future/` para `docs/`

Quando o item deixar de ser "só plano" e **houver código em desenvolvimento**,
mesmo parcial:

1. Mover/reescrever o doc em `docs/` com **status `EM DESENVOLVIMENTO`**;
2. Documentar **o que já está feito** (arquivos/linhas reais) vs **o que falta**;
3. Incluir seção **"como finalizar"** (passo a passo com dependências);
4. Atualizar `docs/backend-parity.md` / `docs/status.md` para apontar o novo
   caminho;
5. Manter o gap-code (ex.: `NATIVE002`) até o item fechar.
