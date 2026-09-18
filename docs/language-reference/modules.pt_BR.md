[English](modules.md) | [Português](modules.pt_BR.md)

# Módulos, Pacotes e Imports

**Status:** Stable (exceto onde etiquetado) · **Evidência:** `Parser.parsePackage`/`parseImports`, `CompilerImports.java`, `MemberResolver.qualifyViaImports`, `CompilerTypes.java:48-138`

---

## 1. Unidade de compilação

A unidade de compilação é **um arquivo `.kf`** (ou `.ks` no KofScript). O
parser produz um `CompilationUnitNode(packageName, imports, declarations)`.

- **`package` é opcional**; ausente → pacote `""` (pacote "default", classe vai
  para `Default/Main`).
- **`import` vem depois de `package`, antes das declarações.**
- **Não há** `module` keyword, nem `namespace`, nem arquivo de módulo separado.
  O "módulo" do Kof é o **diretório raiz** passado ao compilador (module root),
  usado para expandir imports de diretório.

---

## 2. Pacotes

`ebnf
package-declaration = "package" , identifier , { "." , identifier } , [ ";" ]
`

`kof
package com.dev.app
`

- Nome pontuado, semântica de namespace (mapeia para pacote JVM).
- **Não há** diretivas de visibilidade de pacote além de `public`/`private`/
  `protected` por membro.

---

## 3. Imports

`ebnf
import-declaration = "import" , ( "*" | import-path ) , [ ";" ]
import-path = identifier , { "." , identifier } , [ ".*" ]
`

`kof
import com.dev.NodeUI          // classe específica
import com.dev.*               // wildcard de pacote
import kof.json                // módulo stdlib
`

### 3.1 O que um import faz

1. **Qualificação de nome simples**: `qualifyViaImports` resolve um nome simples
   (sem `.`/`<`/`[]`) pelo **primeiro** import não-wildcard que termina em
   `.<nome>` (`MemberResolver.qualifyViaImports`).
   - **Wildcards `import a.b.*` NÃO qualificam nomes simples** (:57) — só
     trazem as declarações para o escopo (item 3.2).
   - Import **ambíguo** (dois imports com o mesmo nome simples) → **não chuta**:
     o tipo é preservado sem qualificação (`simpleNamePackage` retorna `null`,
     `CompilerTypes.java:102-122`). **Stable** (regra anti-chute do bug 32).
2. **Type-arguments são qualificados recursivamente** (`qualifyDeep`,
   `CompilerTypes.java:48-94`): `List<NodeUI>` com `import com.dev.NodeUI`
   resolve para `List<com.dev.NodeUI>` (bug 32). Nome simples do arg resolvido
   por imports → classes do módulo.
3. **Expansão de diretório** (`CompilerImports.expandKofImports`,
   chamada em `CompilerDriver.java`, método `compileSources`): `import a.b` onde `a/b/` é diretório no module
   root **puxa todos os `.kf` daquele diretório** para a unidade (fixpoint ≤256
   rodadas). É como arquivos separados do mesmo pacote se enxergam.

### 3.2 Imports transitivos e colisões

- Importar um pacote que importa outro **re-expõe** as declarações (import
  transitivo não é colisão — PKG005 corrigido).
- Dois `main()` em arquivos do mesmo módulo → **`PKG002`** (*probe*: "module
  has 2 main() functions; expected exactly one").

---

## 4. Resolução de nomes (ordem)

Para um identificador `x` (ver [type-system.md](type-system.md) §6):

`text
escopo local (cadeia de pais)
  → args em main
  → constante de enum não-qualificada
  → membro da classe corrente (resolveInHierarchy BFS)
  → tipos/classes do módulo (knownClasses, fase preDeclareType)
  → imports (qualifyViaImports)
  → namespaces builtin (json, process, KofWeb, …)
  → senão SEM011
`

- **Não há** `import static`, nem renomeação (`import a.b as C`), nem
  `export`/re-export.
- **Não há** resolução por wildcard de pacote para nome simples (item 3.1).

---

## 5. Standard library (`kof.*`)

A stdlib é um conjunto de **namespaces** acessíveis por `import kof.<área>` e
usados via objeto global (`json.encode`, `http.get`, …). Os namespaces
reconhecidos pelo analisador (`SemExpressionTyper`/`MemberResolver`, lista de namespaces builtin):

`text
json  process  KofWeb  KofConfig  KofCache  KofGpu  KofDb  KofOrm
KofLog  KofSecurity  KofValidation  KofObservability  KofHttp  KofMq
KofTime  KofScheduler  KofTetris  KofMedia  KofUi  Theme
`

Cada área tem documento próprio em `docs/stdlib*.md` (não duplicados aqui). A
**linguagem** define que esses nomes existem e como resolvem; a **biblioteca**
define as assinaturas. **Experimental** como superfície (muda entre versões).

---

## 6. Interop com o target

- **JVM**: tipos Java são acessíveis por nome qualificado (`java.util.Date`)
  quando no classpath (`ExternalClasspath.resolveMethod`, `:1535-1549`).
  **Target-specific.**
- **FFI com C (`extern "<lib>" f(T): R`)** — binding direto a bibliotecas
  nativas (JVM, `java.lang.foreign`). **Superfície medida 18/09 (0.4.0-beta)**: a
  JVM casa **qualquer assinatura composta pelo conjunto escalar** `{Int, Long,
  Float, Double, Boolean, String}` em **todas as posições de parâmetro (aridade
  arbitrária, ≥0)** e qualquer um deles como **retorno**, além de **retorno `void`**
  (via `kof_ffi_void`, resultado descartado como statement); um retorno `String` lê
  de volta o `char*` nativo (`MemorySegment.getString`). **Profundidade da prova:**
  `Int`/`Long`/`Double`/`String`/`void` são exercitados ponta-a-ponta contra
  libc/libm (`FfiE2ETest`, incl. `Long` provado via `atol`→`labs` já que o Kof não
  tem literal `long`); `Float`/`Boolean` mapeiam para o layout FFM + `Type` Kof
  corretos no MESMO caminho genérico de downcall, travado por `FfiSignatureTest`
  (a libc não oferece um call site `float`/`_Bool` limpo e determinístico alcançável
  a partir do fonte Kof). Um único helper de
   runtime `kof_ffi(lib, name, sig, Object[])` (downcall FFM; `sig` codifica o
   layout) substituiu o trio `kof_ffi_i`/`_si`/`_dd`; gate
   `CompilerPipeline.isExternBound`. **Paridade JS (fatia 3.6, 18/09)**: a MESMA ABI
   escalar binda no target JS via bridge FFM no host `KofJsFfiBridge` (downcall
   idêntico ao `kof_ffi`) alcançado por `extern`→`kofFfi`→`kof_platform.ffi` no runner
   GraalJS/node — `FfiE2ETest` afirma igualdade byte-a-byte JVM↔JS (abs/atoi/sqrt/pow/
   `atol`→`labs` Long/strstr/srand void); o browser não tem host `kof_platform.ffi`,
   então uma chamada `extern` lança erro honesto em **runtime** (R7, o mesmo degrade do
    `kof.io`); e uma assinatura **não-escalar** (array/struct/pointer) ainda emite
    `FFI002` em compilação. **Callbacks/upcalls (fatia 3.4, 18/09)**: um `extern` com
    **parâmetro de tipo-função** binda na **JVM** — o valor de função Kof vira um
    ponteiro de função C real via `Linker.upcallStub` (`JvmFfiCallbackE2ETest` roda
    `(x,y)->x+y` através de callbacks C em ABIs Int/Long/Double/mistas → `42/42/6.0/7.5`);
    o contrato é **síncrono/não-escapante** (o stub vive na arena da chamada) e a ABI do
    callback é **só primitiva** — um `String`/struct/pointer dentro do callback, ou
    callback-como-retorno, segue `FFI001` honesto. O target **JS** ainda emite `FFI002`
    para callback (paridade = fatia 3.4-C3). Ainda NÃO bound — `FFI001` honesto em
    compilação (JVM/Native), nunca stub silencioso (R6): ABI de struct/array/pointer (design D6,
    ⛔ mantenedora), variadics (3.5, ⛔) e handles
    opacos/out-buffers (3.3, ⛔). O Native emite `FFI001` (`<target>` not supported
    yet, §61). Lib/símbolo ausente falha em **runtime** com exceção `kof_ffi` nomeando
    `lib::symbol` (stack trace, não mensagem cirúrgica). Fatias R3 restantes
    (structs/D6, variadics, handles, paridade Native, **paridade JS de callback
    3.4-C3**; **JVM escalar+callback e JS escalar fechados**)
    em
  `docs/development/IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (use-case #431);
  `extern "c"` no Native depende do §61.

- **Native/JS**: não há interop com tipos do host da mesma forma. **Unspecified.**
- **Annotations** (`@Name`, `@JsonFormat`) são metadados de interop emitidos no
  bytecode JVM. **Target-specific** (só JVM preserva).

---

## 7. Arquivos e extensão

- **`.kf`** — Kof (compilável para todos os targets).
- **`.ks`** — KofScript: **Kof puro executado direto** (sem `let`/`const`/
  `async`/`fn` — não é JavaScript). O wrapper só adiciona o modelo de script
  (`var`/`val` de topo → `KofScriptGlobals`; statements → `main()`).
- **Não há** header/source separado, nem `.kfi`, nem pré-processador.
