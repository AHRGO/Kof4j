[English](README.md) | [Português](README.pt_BR.md)

# docs/future/ — só plano futuro (zero código)

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
| `PLAN-UNIVERSAL-PLATFORM.md` | visão de longo prazo (Kof como plataforma universal) | 100% visão/estratégia — não é ordem de implementação; nenhum pacote `ml`/`bio`/`hpc`/`infra-*` no código |
| `scoped-resources-plan.md` | RAII leve (TIER 2.4, `using`/`resource_scope`) | design puro — zero ocorrências de `resource_scope`/`kof_resource`/`using` no lexer/parser/runtime; gated por bump |
| `PLAN-BAREMETAL-BOOT.md` | **nativo → bare-metal/bootável** (costura HAL B-0…B-5: freestanding, UEFI, BIOS legado, MCU) — diretiva da mantenedora 15/09 | **zero código** — o runtime está fixado a syscalls Linux, o x86 precisa de `-lc`/`-dynamic-linker`, codegen de 32 bits ausente; classificado por `PLAN-TREE-SHAKING.md` §T3 ("embedded real = backend RTOS/bare-metal em si") — vai p/ `docs/` quando B-1 produzir um ELF sem dinâmica |
| ~~`planning-stdlib-array-returns.md`~~ → `docs/stdlib/DD-STDLIB-01-array-returns.md` | DD-STDLIB-01 | **FECHADO 13/09** — decisão 6a + implementação (`randomBytesHex`->String; choice=idiom), movido p/ docs/ |
| `DECOMPILER.md` + `TRANSLATOR.md` + `LEGACY_MIGRATION.md` | plataforma de migração legado (decompiler/translator/IR/diff-testing) | **DESPRIORIZADO pela mantenedora 15/09 — de volta desde `docs/development/`. O código fica em kof-cli (DecompileTest 67/67, TranslateTest 61/61); a FILA está pausada: promoção exige decisão explícita dela |

## Já caíram para `docs/development/` (iniciados — regra dos 3 estados, 12/09)

| Doc | Gatilho da queda |
|-----|------------------|
| `DECOMPILER.md`, `TRANSLATOR.md`, `LEGACY_MIGRATION.md` (os `IMPLEMENTATION_PLAN.md`+`ACTION_PLAN.md` desta lista foram FUNDIDOS p/ `roadmap.md` §23 e os `DIFFERENTIAL_TESTING.md`+`LEGACY_IR.md` p/ dentro do `LEGACY_MIGRATION.md`, tudo 13/09) | plataforma de migração com código+testes: `kof inspect/decompile/translate/compare/migrate` no `Main.java:25-29`, `Confidence.java`, `Type.fromJvmSignature` (contagem viva em `roadmap.md` §23) |
| `PLATFORM-PLAN.md` | Fases 1–3, 8, 9 com código: `ProjectLocator`, `KofProjectConfig`, `Target.SCRIPT`, PKG006/PKG007, `conformance-matrix.md` travada por 11 testes |
| `APPLICATION_MODEL.md` | `application { onStart/onShutdown }` parseado+desugared+E2E nos 3 targets; `KofProjectConfig` |
| ~~`PLANNING-FUTURE-AUDIT.md`, `planning-future-reconcile.md`~~ → `docs/audits/` | auditorias **encerradas 13/09** (comparação branch×beta); R2→`DECISIONS.md` §D-APP/§D-PLATFORM (ratificado 13/09; os 6 arquivos foram apagados), R5→cluster migração |
| ~~`planning-finally-return.md`~~ → `docs/decisions/DD-01-finally-return.md` | FECHADO 13/09 (FinallyFrame IR + gates finallyReturnJvm/Js; bug 45 CORRIGIDO) |
| `planning-stdlib-time-design.md` | `addDays`/`diffDays` (o formato D2 do doc) implementados nos 5 alvos (TIME002 11/09) |

## Quando mover de `future/` para `docs/`

Quando o item deixar de ser "só plano" e **houver código em desenvolvimento**,
mesmo parcial:

1. Mover/reescrever o doc em `docs/` com **status `EM DESENVOLVIMENTO`**;
2. Documentar **o que já está feito** (arquivos/linhas reais) vs **o que falta**;
3. Incluir seção **"como finalizar"** (passo a passo com dependências);
4. Atualizar `docs/backend-parity.md` / `docs/status.md` para apontar o novo
   caminho;
5. Manter o gap-code (ex.: `NATIVE002`) até o item fechar.
