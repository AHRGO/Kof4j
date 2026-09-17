[English](debugging-native.md) | [Português](debugging-native.pt_BR.md)

# DEBUGGING_NATIVE.md — Debug no target Native

**Status:** Parcial (17/09) — DWARF line table real no ELF x86-64 (Fase 5 parcial): `NativeBackend`
emite `.file 1 "<fonte.kf>"` + `.loc 1 <linha> 0` quando debug esta habilitado; `objdump --dwarf=decodedline` mostra o arquivo Kof e a linha de cada instrucao
(`NativeDwarfLineInfoTest`). **Fatia 1 da frente 4 (17/09): CU DWARF `.debug_info`/`.debug_abbrev` do Kof com um `DW_TAG_subprogram` por funcao Kof
(`DW_AT_name` = nome-fonte, `low_pc`/`high_pc`, `decl_file`/`decl_line`) —
`NativeDwarf.java` + `NativeDwarfSubprogramTest`; o `as` suprime a CU automatica dele quando o programa emite `.debug_info` explicitamente (verificado no ELF linkado). **Fatia 2 (17/09): cada subprogram agora carrega `DW_AT_frame_base`
(`DW_OP_reg6`/rbp) + `DW_TAG_formal_parameter`/`DW_TAG_variable` filhos com
`DW_AT_location = DW_OP_fbreg` no MESMO slot do prologue (`-(index+1)*8`).
Duas licoes DWARF4 medidas contra gdb real: (1) `DW_AT_high_pc` e offset so na
v4+ (a v3 lia endereco absoluto e o gdb descartava a CU); (2) a ordem do header
v4 e version→abbrev_offset→address_size. Prova E2E: `gdb -batch -ex "b
Box_twice" -ex run -ex "info locals"` imprime `y = 20` (um `var` Kof) e nomeia
os args `this`/`w` — ler valores com tipo exige `DW_AT_type` (fatia 3).**
**Fatia 3 (17/09): DW_AT_type nos params/locals/retorno via DIEs filhos
`DW_TAG_base_type` (Int=signed4, Long=signed8, Double=float8, Float=float4,
Bool=boolean1, Char=unsigned2, Void=tamanho-0/sem-encoding,
classe/array/String = handle opaco de 8 bytes por enquanto). Codigo retirado
dos proprios bytes do `.debug_abbrev` do GCC, nao chutado. gdb le valores
tipados fim-a-fim: `print w` -> `5`, `ptype Box_twice` -> `Int (Opaque, Int)`.**
DAP on native e stepping pendentes.**
**Data:** 2 de setembro de 2026
**Versão:** 0.4.0-beta (7 targets)

---

## 1. Fluxo

```text
Kof Debug Info (IR)
    ↓
símbolos + line tables (DWARF futuro)
    ↓
ELF x86-64
    ↓
debug adapter (frame info Kof)
    ↓
DAP
    ↓
Editor
```

## 2. Fase inicial

- símbolos de função (já existem: `ClassName_methodName`);
- source locations por instrução (Fase 1 da IR);
- line tables ✅ (`.file`/`.loc` GAS → `.debug_line`; verificado com `objdump --dwarf=decodedline`);
- locals com tipos Kof;
- scopes;
- stack frames.

## 3. Depois

- DWARF completo;
- localizações otimizadas de variáveis;
- inspeção de memória nativa.

## 4. Regra

Nunca mostrar assembly como experiência primária — o mapeamento
`assembly → linha Kof` é interno.