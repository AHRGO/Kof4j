[English](stdlib-loading.md) | [Português](stdlib-loading.pt_BR.md)

# Stdlib por alcançabilidade (tree-shaking)

**Status:** implementado 12/09/2026 (`beta-0.4.0`, issue #97) · **Última
atualização:** 13/09/2026

> O desenvolvedor declara o que pretende utilizar; o compilador inclui
> **somente** o que for realmente necessário para executar o programa. Nada
> de microgerenciamento de dependências, nada de `--include=json.parser`,
> nada de lista manual para evitar bloat.

## Como funciona (por target)

| Target | Mecanismo | Granularidade | Sementes |
|---|---|---|---|
| Native x86_64 | poda por alcançabilidade sobre o texto do programa + fallback keep-all | 113 fatias (`RuntimeSlices`) | tokens `kof_*` no `.s` do programa |
| Native riscv64/aarch64 | mesma poda (port `RiscvSlices`, 48 peças) + `ld --gc-sections` | peça + seção por função | idem (vocabulário completo incl. símbolos sem prefixo `kof_`) |
| JS | poda por alcançabilidade ligada no writer | unidade de topo (`JsRuntimeSlices`, 17 blocos) | `runtimeImports`/`ioRuntimeImports` que o `JsBackend` já acumula |

**Propriedades (todas travadas por teste):**

- Fallback conservador: keep-all → byte-idêntico ao pré-poda; exceção no
  mapa → runtime completo + aviso (nunca link quebrado silencioso).
- Determinístico: mesma entrada → mesmo conjunto → mesma ordem → mesmo
  artefato (sementes em `TreeSet`, emissão na ordem do inventário).
- Multi-módulo JS = **união** dos fechamentos + reescrita do runtime
  compartilhado (nunca "primeiro módulo vence"); cabeçalho observável
  `// kof:seeds`, `// kof:units N/600`, `// kof:fallback <bloco>: <motivo>`.
- Falso-negativo de call site real é impossível (seed por texto erra só
  para MAIS — binário maior, link válido).

## Números (hello world — travados no `ArtifactSizeTest`)

| Alvo | Antes | Depois | Queda |
|---|---|---|---|
| x86_64 (bytes / syms) | 138.928 B / 627 | **32.520 B / 37** | −77% / −94% |
| riscv64 (bytes / syms) | 144.000 B / 258 | **133.288 B / 18** | −82% syms (bytes caem pouco: `.bss` do heap bump ~260 KB é fixo sem mark-sweep) |
| aarch64 (bytes / syms) | — / — | **133.112 B / 18** | baseline travado pela 1ª vez |
| JS (runtime) | 177.412 B | **6.873 B** | −96,1% |

Tolerância unilateral +5% só para inchaço — encolher é a meta; sabotagem do
baseline → FAIL. Gate: `ArtifactSizeTest` (tamanhos) + testes de ausência
por família (`nativeFamilyAbsenceAfterPrune`, `riscvFamilyAbsenceAfterPrune`,
`JsRuntimePruneWriterTest`).

## Limites honestos

- **x86 sem `--gc-sections`**: exige `kof_heap_root_end` + `emitStaticData`
  dentro do intervalo de raízes do scan conservative (fila bugfix).
- **Bytes riscv/aarch**: só caem de verdade com GC mark-sweep (o `.bss` do
  heap bump é fixo) — ver `docs/development/native-multiarch.md`.
- **`kof_platform` no JS** (issue #104): `uuid`/`random`/`security` fora do
  host GraalJS dão `ReferenceError` — a poda **preserva** o comportamento,
  não é regressão nem é corrigida aqui.

## Referências (código)

- `dev.kof.compiler.ArtifactSize` + `ArtifactSizeTest` (gate)
- `dev.kof.compiler.nat.RuntimeSlices` / `RiscvSlices` (mapas)
- `dev.kof.compiler.js.JsRuntimeSlices` + `JsArtifactWriter` (writer JS)
- `kof build --print-sizes` (JSON estável, aditivo)

Plano original de desenvolvimento:
`docs/stdlib/PLAN-TREE-SHAKING.md` (histórico da implementação S-1…S-6).
