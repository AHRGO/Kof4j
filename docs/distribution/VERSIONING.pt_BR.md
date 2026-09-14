[English](VERSIONING.md) | [Português](VERSIONING.pt_BR.md)

# Versionamento do Kof

## Formato

```text
MAJOR.MINOR.PATCH
```

A hierarquia conceitual:

```text
Major releases
    >
Major fixes
    >
Bugfixes
```

| Componente | Significado |
|-----------|-------------|
| `X` (MAJOR) | Release maior |
| `Y` (MINOR) | Major fix / evolução significativa |
| `Z` (PATCH) | Bugfix — o *pontinho da vergonha* |

O `PATCH` é carinhosamente chamado de **pontinho da vergonha** porque
representa principalmente:

- bugfix;
- correção;
- regressão;
- pequenos ajustes;
- pequenas melhorias sem mudança arquitetural relevante.

## Estágio atual

O Kof está no estágio inicial:

```text
0.0.x
```

Portanto:

```text
0.0.4
0.0.5
0.0.6
...
```

## Convenção Alpha

Enquanto o Kof estiver em Alpha, o release carrega explicitamente essa
informação:

```text
0.0.5-alpha
```

Regras:

- `0.0.5-alpha` identifica o artefato, o GitHub Release e a tag
  (`kof-0.0.5-alpha`);
- a versão de componente (compiler/runtime/stdlib) é `0.0.4` — o sufixo
  `-alpha` pertence ao release;
- nada é chamado de stable;
- a evolução pretendida é Alpha → Beta → Release Candidate → Stable
  (sem máquina de estados complexa neste momento).

## Fonte única de verdade

A versão vive em **um único arquivo**: `VERSION` na raiz do repositório.

```text
VERSION ──► scripts/bump-version.sh ──► pom.xml (<revision>)
                                     ──► kof-compiler/src/main/resources/dev/kof/version.properties
```

A pipeline atualiza automaticamente:

- versão do compiler;
- versão da CLI;
- metadata do runtime;
- artefatos (jars);
- pacote de distribuição;
- GitHub Release;
- changelog.

**Nada de versão hardcoded em dezenas de arquivos** — isso é receita para
inconsistência. Se a versão precisa mudar, muda-se o `VERSION` (ou a
pipeline faz isso) e o resto segue.

## Quando a versão muda

Enquanto em Alpha, **todo commit na `main` gera a próxima versão Alpha**
(incremento de PATCH):

```text
0.0.5-alpha → 0.0.5-alpha → 0.0.6-alpha → ...
```

Regras de bom senso para o futuro:

- PATCH: bugfix, correção, regressão;
- MINOR: evolução significativa de capacidade;
- MAJOR: mudança arquitetural / quebra de compatibilidade.

## Verificação

`kof version` e `kof info` reportam a versão empacotada. O CI verifica que
`VERSION`, `pom.xml` e o resource de versão concordam antes de qualquer
build.