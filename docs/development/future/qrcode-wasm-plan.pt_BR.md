[English](qrcode-wasm-plan.md) | [Português](qrcode-wasm-plan.pt_BR.md)

# Kof — Implementação Incremental de `kofqrcode` + `KofWasm`

> **Estado (19/09): FUTURE — plano apenas, zero código.** Não é fila de
> execução (regra dos três estados + R12). Notas de choque com o estado
> real antes de promover: (a) `kofqrcode` é domínio pesado → **pacote
> oficial** (R1 + gate `scripts/check_stdlib_boundary.sh`), decoder via
> interop (ZXing no JVM — R9), câmera via `kof.process`/FFI; (b)
> `KofWasm` = **novo target** → mexe no enum `Target`, na CLI e no
> dispatcher = decisão de design da mantenedora (regra 6), e o `app {
> route ... }` do exemplo é pseudocódigo (o `kof.ui` atual declara
> intenção, não HTML — regra 9); (c) os exemplos usam `let`/`print`
> (idiomas falsos — `training/anti-patterns/fake-idioms.md`); sintaxe
> real é `var`/`val`/`println`.

Quero evoluir o projeto Kof com **duas funcionalidades independentes**, seguindo rigorosamente a arquitetura existente e sem quebrar absolutamente nada que já funciona:

1. `kofqrcode` — Reader + Writer de QR Code
2. `KofWasm` — novo target de frontend baseado em WebAssembly, coexistindo com `KofJS`

## REGRA ABSOLUTA

### NÃO QUEBRAR A BASE EXISTENTE

A implementação deve ser **incremental, isolada e compatível com o estado atual do projeto**.

Antes de escrever código:

* analise toda a arquitetura relevante;
* entenda o compilador;
* entenda o sistema de targets;
* entenda o KofJS atual;
* entenda Kof4J/KofNative/KofScript;
* entenda o sistema de módulos;
* entenda APIs de frontend;
* entenda testes;
* entenda CLI;
* entenda LSP;
* entenda documentação;
* execute os testes existentes.

Não faça refactor arquitetural amplo sem necessidade.

Não altere APIs existentes sem motivo.

Não remova funcionalidades.

Não substitua implementações existentes simplesmente porque existe uma abordagem "melhor".

Não crie abstrações gigantescas antecipadamente.

**Preserve a base existente e evolua-a somente onde for necessário.**

---

# PARTE 1 — `kofqrcode`

Criar suporte para QR Code no ecossistema Kof.

A funcionalidade deve possuir dois lados:

```text
kofqrcode
├── Reader
│   ├── arquivo/imagem
│   └── câmera em tempo real
│
└── Writer
    └── texto → QR Code
```

## Reader — arquivo

Permitir algo conceitualmente semelhante a:

```kof
import kof.qrcode.*

let result = QRCode.read("qrcode.png")

print(result.text)
```

A API final deve seguir os padrões reais do Kof, portanto **não copie esse exemplo cegamente**.

O Reader deve:

* receber caminho de arquivo;
* carregar imagem;
* detectar QR Code;
* decodificar conteúdo;
* retornar resultado fortemente tipado;
* tratar arquivo inexistente;
* tratar imagem inválida;
* tratar ausência de QR Code;
* tratar QR Code inválido/corrompido;
* fornecer erros claros.

Avaliar uma estrutura semelhante a:

```text
QRCodeResult
├── text
├── format
└── rawBytes
```

Somente adicionar campos realmente necessários.

---

# Reader — câmera em tempo real

Adicionar suporte para leitura através de câmera quando o target/plataforma permitir.

Conceitualmente:

```kof
let reader = QRCode.camera()

reader.start(frame -> {
    if frame.hasCode() {
        print(frame.text)
    }
})
```

Novamente, isso é apenas referência de ergonomia.

A implementação real deve respeitar a arquitetura do Kof.

Considerar:

* abertura da câmera;
* permissões;
* captura de frames;
* processamento dos frames;
* detecção;
* callbacks/eventos;
* start/stop;
* liberação de recursos;
* câmera indisponível;
* concorrência;
* evitar processamento excessivo;
* não bloquear a thread principal desnecessariamente.

Se existir infraestrutura de async/eventos/threads/streams no Kof, reutilizá-la.

**Não implementar um loop infinito ingênuo.**

Se determinado target não possuir suporte adequado à câmera, documentar a limitação em vez de criar uma implementação fake.

---

# Writer

Permitir gerar QR Code a partir de texto.

Conceitualmente:

```kof
QRCode.write(
    "https://koflang.dev",
    "qrcode.png"
)
```

O Writer deve:

* receber texto;
* gerar QR Code;
* permitir salvar como imagem;
* tratar erros de escrita;
* suportar Unicode;
* funcionar de forma consistente com o Reader.

Não criar inicialmente uma API gigantesca de configuração.

Deixar espaço para evolução futura, como:

* tamanho;
* margem;
* formato;
* error correction level.

---

# Dependências do QR Code

Não implementar encoder/decoder de QR Code do zero se existir uma biblioteca madura e adequada.

Avaliar bibliotecas existentes.

Para JVM, considerar bibliotecas consolidadas como ZXing se forem compatíveis com:

* arquitetura;
* licença;
* tamanho;
* performance;
* targets.

Para Native/JS/WASM, avaliar soluções adequadas separadamente.

Não adicionar dependências gigantescas sem necessidade.

Toda dependência nova precisa ter justificativa.

---

# Testes do `kofqrcode`

Criar testes para:

### Writer

* texto simples;
* URL;
* Unicode;
* arquivo de saída;
* erros de escrita.

### Reader

* QR Code válido;
* QR Code gerado pelo Writer;
* arquivo inexistente;
* imagem inválida;
* imagem sem QR Code;
* Unicode;
* URL.

Principal teste de integração:

```text
texto
 ↓
QRCode Writer
 ↓
arquivo
 ↓
QRCode Reader
 ↓
texto original
```

O resultado precisa ser idêntico ao texto original.

---

# PARTE 2 — `KofWasm`

Adicionar um novo target:

```text
wasm
```

como alternativa ao:

```text
js
```

O objetivo é permitir que Kof seja usado no frontend através de **JavaScript ou WebAssembly**, mantendo o mesmo código-fonte da aplicação.

Arquitetura desejada:

```text
                         Kof Source
                             │
                             ▼
                    ┌─────────────────┐
                    │ Kof Frontend API│
                    └────────┬────────┘
                             │
                  ┌──────────┴──────────┐
                  ▼                     ▼
              KofJS                 KofWasm
                  │                     │
                  ▼                     ▼
             JavaScript               WASM
                  │                     │
                  └──────────┬──────────┘
                             ▼
                         Browser
```

## PRINCÍPIO FUNDAMENTAL

### O código da aplicação NÃO deve mudar entre JS e WASM.

Exemplo:

```kof
import kof.web.*

app {
    route("/") {
        page {
            title("Kof")
            text("Hello World")
        }
    }
}
```

O mesmo código deve poder ser compilado com:

```bash
kof build --target js
```

e:

```bash
kof build --target wasm
```

Sem criar:

```kof
if target == js
```

Sem duplicar a aplicação.

Sem criar:

```text
MyAppJS
MyAppWasm
```

A diferença deve estar no backend/runtime/interop, não na lógica da aplicação.

---

# Arquitetura de frontend

Antes de implementar `KofWasm`, estudar cuidadosamente o `KofJS` existente.

Identificar quais APIs são:

* linguagem;
* frontend;
* DOM;
* browser;
* runtime;
* JavaScript interop;
* compilador;
* backend.

Se necessário, extrair somente as abstrações que realmente precisam ser compartilhadas.

O objetivo é chegar progressivamente a algo como:

```text
kof.web
│
├── API comum
│   ├── DOM
│   ├── Events
│   ├── HTTP
│   ├── Storage
│   └── Browser APIs
│
├── KofJS implementation
│
└── KofWasm implementation
```

A API comum deve ser independente do backend.

---

# KofJS não pode ser quebrado

`KofJS` já existe.

Não substituir.

Não reescrever.

Não migrar tudo para WASM.

Não remover suporte JS.

`KofWasm` deve ser adicionado ao lado dele.

O resultado final precisa ser:

```text
Kof Frontend
├── JS
└── WASM
```

---

# Interoperabilidade WASM ↔ JavaScript

WebAssembly não possui acesso mágico ao DOM.

Portanto, projetar corretamente a comunicação entre:

```text
Kof/WASM
      ↕
JavaScript bridge
      ↕
Browser APIs
```

Avaliar cuidadosamente:

* DOM;
* eventos;
* fetch;
* Web APIs;
* console;
* storage;
* timers;
* callbacks;
* strings;
* arrays;
* objetos;
* lifecycle;
* memória;
* comunicação JS ↔ WASM.

Não esconder limitações reais da plataforma.

Se determinada API depender de JavaScript, encapsular isso em uma camada de interop.

---

# Target system

O compilador deve reconhecer:

```bash
kof build --target js
kof build --target wasm
```

E, se fizer sentido dentro da CLI atual:

```bash
kof run --target wasm
```

ou outro mecanismo equivalente.

Não inventar comandos incompatíveis com a CLI existente.

Seguir exatamente o padrão atual de targets.

---

# Compatibilidade

Documentar claramente:

```text
Target     Status
-------------------------
JVM        existente
Native     existente
JS         existente
WASM       novo
Script     existente
```

Para cada API de frontend, indicar se possui:

```text
JS
WASM
JS + WASM
```

Não declarar suporte que não existe.

---

# Mesmo código, targets diferentes

Criar um exemplo real:

```text
examples/frontend/
```

ou seguir a estrutura existente.

Exemplo:

```kof
import kof.web.*

app {
    route("/") {
        page {
            title("Kof WASM")
            text("Hello from Kof")
        }
    }
}
```

Testar o mesmo arquivo com:

```bash
kof build --target js
kof build --target wasm
```

O código-fonte deve permanecer exatamente igual.

Esse requisito é fundamental.

---

# Testes do KofWasm

Adicionar testes para:

* compilação para WASM;
* geração correta do artefato;
* carregamento no browser;
* execução;
* interop com JavaScript;
* DOM;
* eventos;
* HTTP, quando suportado;
* erros de compilação;
* APIs não suportadas;
* build existente de JS.

Garantir que:

```text
KofJS antes
        ↓
implementação KofWasm
        ↓
KofJS depois
```

continue funcionando.

---

# Integração com o compilador

Não criar um compilador paralelo.

O ideal é:

```text
Kof Parser
    ↓
Kof AST / IR
    ↓
    ├── JVM Backend
    ├── Native Backend
    ├── JS Backend
    └── WASM Backend
```

Se a arquitetura atual não estiver exatamente assim, adapte-se à arquitetura existente.

O princípio é evitar duplicação da linguagem.

`KofWasm` deve ser outro backend/target, não outra linguagem.

---

# LSP

Verificar se o novo target exige mudanças no LSP.

O código Kof deve continuar tendo:

* syntax highlighting;
* diagnostics;
* completion;
* hover;
* resolução de símbolos.

Não criar uma linguagem diferente para WASM.

---

# Documentação

Atualizar a documentação existente.

Documentar:

## `kofqrcode`

* Reader;
* Writer;
* arquivo;
* câmera;
* geração;
* tratamento de erros;
* targets;
* limitações.

## `KofWasm`

* o que é;
* por que existe junto com KofJS;
* como selecionar o target;
* compatibilidade;
* JavaScript interop;
* APIs disponíveis;
* limitações;
* exemplos.

Mostrar explicitamente:

```bash
kof build --target js
```

versus:

```bash
kof build --target wasm
```

e explicar que **o código-fonte da aplicação permanece o mesmo**.

---

# Ordem de implementação

Não tente implementar tudo de uma vez.

Faça em fases.

## Fase 0 — análise

* estudar arquitetura;
* rodar testes;
* estudar targets;
* estudar KofJS;
* identificar pontos de extensão.

## Fase 1 — `kofqrcode`

Primeiro:

```text
Writer
 ↓
Reader de arquivo
 ↓
testes
 ↓
integração
```

Depois:

```text
Camera Reader
```

## Fase 2 — abstrações frontend

Antes de WASM:

* identificar APIs compartilháveis;
* reduzir acoplamento do frontend ao JS;
* extrair somente abstrações necessárias.

Não reescrever KofJS inteiro.

## Fase 3 — `KofWasm`

Implementar:

```text
target
 ↓
backend
 ↓
runtime/bridge
 ↓
browser
```

Começar com o menor conjunto funcional possível.

## Fase 4 — frontend comum

Validar que o mesmo código Kof funciona com:

```text
--target js
--target wasm
```

## Fase 5 — estabilização

* testes;
* integração;
* documentação;
* performance;
* erros;
* regressões.

---

# Critérios de aceitação

A implementação só pode ser considerada concluída quando:

### `kofqrcode`

* [ ] Writer funcional
* [ ] Reader de arquivo funcional
* [ ] Reader de câmera funcional onde suportado
* [ ] Writer → Reader funcionando
* [ ] erros tratados
* [ ] testes adicionados
* [ ] documentação atualizada

### `KofWasm`

* [ ] target `wasm` reconhecido
* [ ] backend funcional
* [ ] artefato WASM gerado
* [ ] execução no browser
* [ ] JavaScript interop funcional quando necessário
* [ ] APIs frontend comuns funcionando
* [ ] mesmo código funcionando em JS
* [ ] mesmo código funcionando em WASM
* [ ] KofJS preservado
* [ ] testes adicionados
* [ ] documentação atualizada

### Projeto inteiro

* [ ] build existente continua funcionando
* [ ] testes existentes continuam passando
* [ ] golden tests continuam passando
* [ ] CLI continua funcionando
* [ ] LSP continua funcionando
* [ ] nenhum breaking change desnecessário
* [ ] nenhuma dependência desnecessária
* [ ] nenhuma refatoração fora de escopo
* [ ] nenhuma funcionalidade existente removida

---

# Regra final

Antes de cada alteração importante, pergunte:

> "Isso é necessário para implementar a feature ou estou aproveitando para refatorar o projeto?"

Se não for necessário, **não faça**.

A prioridade é:

```text
estabilidade
    ↓
compatibilidade
    ↓
arquitetura consistente
    ↓
testes
    ↓
funcionalidade
    ↓
otimização
```

Não sacrifique a base existente para acelerar a implementação.

O resultado esperado não é apenas "funciona".

É:

**Kof continua sendo Kof, só que agora possui QR Code e um backend WebAssembly de verdade.**
