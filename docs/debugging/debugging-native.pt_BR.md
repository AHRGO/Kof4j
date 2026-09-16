[English](debugging-native.md) | [Português](debugging-native.pt_BR.md)

# DEBUGGING_NATIVE.md — Debug no target Native

**Status:** Parcial (17/09) — DWARF line table real no ELF x86-64 (Fase 5 parcial): `NativeBackend`
emite `.file 1 "<fonte.kf>"` + `.loc 1 <linha> 0` quando debug esta habilitado; `objdump --dwarf=decodedline` mostra o arquivo Kof e a linha de cada instrucao
(`NativeDwarfLineInfoTest`). **Fatia 1 da frente 4 (17/09): CU DWARF `.debug_info`/`.debug_abbrev` do Kof com um `DW_TAG_subprogram` por funcao Kof
(`DW_AT_name` = nome-fonte, `low_pc`/`high_pc`, `decl_file`/`decl_line`) —
`NativeDwarf.java` + `NativeDwarfSubprogramTest`; o `as` suprime a CU automatica dele quando o programa emite `.debug_info` explicitamente (verificado no ELF linkado). Variaveis locais (`DW_AT_location`/fbreg), DAP on native e stepping pendentes.**
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