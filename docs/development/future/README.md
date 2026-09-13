# docs/future/ — só plano futuro (zero código)

**Regra desta pasta:** aqui vive **apenas** o que é **plano para o futuro** —
documento de arquitetura/visão **sem código implementado** (ou com código que
é explicitamente não-entregável e fora do escopo atual).

> Se uma ideia **já está sendo implementada** (mesmo parcialmente), o doc
> correspondente **não fica aqui** — ele vive em `docs/` e documenta o **estado
> real** (o que já existe) + **como finalizar**. Assim quem lê sabe exatamente
> onde a coisa está e o que falta.

## Exemplo recente (01/09)

- `kof-native-risc-arm.md` **saiu daqui** para `docs/native-multiarch.md`: o
  plumbing (enum `Target.NATIVE_RISCV64/AARCH64`, CLI `native.risc/arm`,
  dispatch, cross-as/ld) já está no código, então o item é **em desenvolvimento**
  e passou a ser documentado com estado real + plano de finalização.

## O que fica aqui (só plano, sem código)

| Doc | Tema | Por que fica em `future/` |
|-----|------|---------------------------|
| `PLAN-UNIVERSAL-PLATFORM.md` | visão de longo prazo (Kof como plataforma universal) | 100% visão/estratégia — não é ordem de implementação; nenhum pacote `ml`/`bio`/`hpc`/`infra-*` no código |
| `scoped-resources-plan.md` | RAII leve (TIER 2.4, `using`/`resource_scope`) | design puro — zero ocorrências de `resource_scope`/`kof_resource`/`using` no lexer/parser/runtime; gated por bump |
| ~~`planning-stdlib-array-returns.md`~~ → `docs/development/` | DD-STDLIB-01 | **CAIU 13/09 — decisão 6a ratificada** (`randomBytesHex`->String; choice=idiom): agora é implementação pendente (lane STDLIB), não mais 'só plano' |

## Já caíram para `docs/development/` (iniciados — regra dos 3 estados, 12/09)

| Doc | Gatilho da queda |
|-----|------------------|
| `DECOMPILER.md`, `TRANSLATOR.md`, `DIFFERENTIAL_TESTING.md`, `LEGACY_MIGRATION.md`, `LEGACY_IR.md`, `IMPLEMENTATION_PLAN.md`, `ACTION_PLAN.md` | plataforma de migração com código+testes: `kof inspect/decompile/translate/compare/migrate` no `Main.java:25-29`, 63 testes kof-cli, `Confidence.java`, `Type.fromJvmSignature` |
| `PLATFORM-PLAN.md` | Fases 1–3, 8, 9 com código: `ProjectLocator`, `KofProjectConfig`, `Target.SCRIPT`, PKG006/PKG007, `conformance-matrix.md` travada por 11 testes |
| `APPLICATION_MODEL.md` | `application { onStart/onShutdown }` parseado+desugared+E2E nos 3 targets; `KofProjectConfig` |
| ~~`PLANNING-FUTURE-AUDIT.md`, `planning-future-reconcile.md`~~ → `docs/audits/` | auditorias **encerradas 13/09** (comparação branch×beta); R2→`decision-pending/` (APPLICATION_MODEL/PLATFORM-PLAN), R5→cluster migração |
| `planning-finally-return.md` | face JS do bug 45 corrigida (`c727fee`); decisão DD-01 só p/ JVM/Native/interp |
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
