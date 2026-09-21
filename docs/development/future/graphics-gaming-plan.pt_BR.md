[English](graphics-gaming-plan.md) | [Português](graphics-gaming-plan.pt_BR.md)

# Gráficos, jogos e mídia — a superfície de intenção do Kof

**Estado:** Plano futuro — somente design, **zero código**
**Local:** `docs/development/future/`
**Natureza:** arquitetura, contratos, dependências, estratégia de implementação e critérios de promoção
**Fonte normativa:** `DECISIONS.md` §D-GRAPHICS-GAMING + adendos registrados pela mantenedora
**Dependências principais:** R3 / FFI-ABI, runtime, capability matrix, stdlib boundary, conformance suite
**Status de implementação:** não iniciado

> **Regra fundamental:** este documento descreve uma direção arquitetural futura. Ele não altera a linguagem, não adiciona keywords, não cria namespaces e não abre uma frente de implementação.
>
> Toda sintaxe apresentada neste documento é **forma de intenção**. Ela serve para demonstrar como a API poderia expressar uma intenção humana. A forma definitiva da linguagem permanece sujeita à decisão da mantenedora.

---

# 0. Objetivo

O objetivo deste plano é definir como o Kof poderá oferecer uma superfície nativa para:

* gráficos 2D;
* gráficos 3D;
* janelas;
* game loops;
* input;
* sprites;
* tilemaps;
* áudio;
* reprodução de vídeo;
* mídia;
* integração com KofUI;
* jogos;
* aplicações gráficas interativas.

A característica central dessa superfície será:

> **o código Kof declara o que pretende fazer; o backend decide como realizar.**

Um programa Kof não deverá precisar conhecer:

* SDL;
* OpenGL;
* Vulkan;
* DirectX;
* WebGL;
* WebAudio;
* WASM;
* JavaFX;
* Swing;
* AWT;
* APIs proprietárias de cada sistema;
* detalhes de device;
* swapchain;
* framebuffer;
* audio buffer;
* codec;
* demuxer;
* event loop da plataforma.

Esses mecanismos pertencem ao backend.

O objetivo não é criar uma nova linguagem de gráficos dentro do Kof.

O objetivo é criar uma **superfície de intenção**.

---

# 1. Princípios arquiteturais

## 1.1 Intenção antes de mecanismo

O código deve responder:

> O que quero que aconteça?

e não:

> Qual API gráfica preciso chamar para fazer isso?

Exemplo:

```kof
sprite("player.png").at(100, 80).draw()
```

é intenção.

Já:

```text
createTexture(...)
bindTexture(...)
beginBatch(...)
drawQuad(...)
swapBuffers(...)
```

é mecanismo.

O segundo modelo deve permanecer escondido da aplicação.

---

## 1.2 A plataforma é responsável pelo loop

O usuário não deve precisar implementar:

```text
while (running) {
    pollEvents()
    update()
    render()
    swapBuffers()
}
```

O loop pertence à plataforma.

O usuário fornece a lógica:

```kof
frame { dt ->
    update(dt)
    draw()
}
```

O backend transforma isso no modelo equivalente do target.

---

## 1.3 APIs estrangeiras não atravessam a fronteira

A superfície Kof não deverá reproduzir APIs estrangeiras.

Não:

```kof
SDL_CreateWindow(...)
```

Não:

```kof
glClear(...)
```

Não:

```kof
canvas.getContext(...)
```

Não:

```kof
MediaPlayer(...)
```

Não:

```kof
javafx.scene...
```

A existência dessas tecnologias no backend não significa que elas fazem parte da linguagem.

---

# 2. Estado atual medido

A implementação futura deve partir do estado real do repositório na `beta-0.5.0`, e não de uma arquitetura presumida.

## 2.1 JavaFX

A medição de 21/09 estabelece:

```text
import javafx
javafx.
pom.xml → javafx/openjfx
```

como zero ocorrências de uso real.

As ocorrências encontradas em código são comentários relacionados aos sintomas de erros do launcher.

Portanto:

> **JavaFX não é uma implementação anterior de gráficos do Kof.**

Não existe uma migração JavaFX → nova stack.

Existe uma decisão de nunca introduzir JavaFX.

---

## 2.2 `kof.ui`

Atualmente:

* JVM/Native possuem uma alça de runtime sem renderização real;
* JVM possui `kof_ui_window_new`;
* setters/show possuem implementação vazia;
* KofJS possui a face funcional baseada em DOM/webview.

Isso significa que a futura superfície gráfica precisa substituir o estado no-op por uma arquitetura real, mas sem transformar o no-op atual em falsa compatibilidade.

Enquanto não houver implementação:

```text
GFX00x
```

deve representar o gap.

---

## 2.3 `kof.media`

Já existe uma base JVM.

Atualmente há suporte para:

* abrir/salvar bitmap;
* metadados de vídeo;
* amostragem de WAV;
* enumeração/gravação de microfone.

Não existe atualmente:

* playback de áudio;
* streaming de música;
* mixer;
* pipeline completo de vídeo;
* reprodução de vídeo;
* face Native;
* face JS equivalente.

Os gaps existentes incluem:

```text
MEDIA001
MEDIA003
```

A implementação futura deve evoluir essa superfície sem quebrar programas existentes.

---

# 3. Dependência estrutural: R3

A futura stack gráfica depende diretamente da infraestrutura de FFI/ABI definida pela R3.

O backend gráfico precisará atravessar a fronteira:

```text
Kof
 ↓
Kof IR
 ↓
backend
 ↓
ABI
 ↓
runtime/platform layer
 ↓
graphics/audio/video library
 ↓
OS/device
```

A camada de interop deverá ser capaz de representar, quando necessário:

* handles;
* ponteiros;
* buffers;
* structs;
* callbacks;
* arrays;
* strings;
* lifecycle;
* ownership;
* códigos de erro;
* recursos nativos.

A implementação gráfica **não deve criar uma FFI paralela**.

Se a R3 não fornecer um mecanismo necessário, isso deve virar uma extensão da R3 antes de criar uma solução específica para gráficos.

---

# 4. Modelo geral da arquitetura

A arquitetura futura deverá possuir cinco níveis:

```text
┌─────────────────────────────┐
│          Kof App             │
│   intenção gráfica/mídia     │
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│     Kof Graphics API         │
│   intenção independente      │
│          de target           │
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│       Kof Runtime ABI        │
│ handles / buffers / events   │
└──────────────┬──────────────┘
               │
      ┌────────┼─────────┐
      │        │         │
     JVM     Native      JS
      │        │         │
      ▼        ▼         ▼
 platform   platform   browser
```

A mesma intenção deve chegar aos diferentes targets.

---

# 5. Modelo de recursos

Recursos gráficos e de mídia são objetos de plataforma.

Exemplos:

```text
Window
Sprite
Texture
Tilemap
Mesh
Material
Camera
Sound
Music
Video
InputDevice
```

Esses objetos não devem expor detalhes da implementação.

Por exemplo, um `sprite` não deve revelar se internamente é:

* textura OpenGL;
* textura Vulkan;
* recurso WebGL;
* imagem HTML;
* objeto nativo;
* buffer GPU.

A aplicação manipula uma abstração Kof.

---

# 6. Lifecycle

Todo recurso gráfico/mídia precisa possuir lifecycle definido.

Exemplo conceitual:

```text
create
 ↓
ready
 ↓
use
 ↓
release
```

A documentação futura de cada recurso deverá responder:

* quando é criado;
* se é lazy;
* quando os dados são carregados;
* quando fica disponível;
* quem possui o recurso;
* quando pode ser liberado;
* se a plataforma realiza cache;
* o que acontece quando a janela/device desaparece.

A aplicação não deve ser obrigada a gerenciar manualmente detalhes de GPU se o backend puder fazer isso.

---

# 7. Janela

A janela será responsabilidade do backend.

A intenção futura poderá ser semelhante a:

```kof
Window("Pong") {
    frame { dt ->
        ...
    }
}
```

ou:

```kof
Scene("Pong") { dt ->
    ...
}
```

A forma definitiva ainda não está decidida.

A abstração deverá contemplar, conforme suportado:

* título;
* tamanho;
* fullscreen;
* resize;
* foco;
* fechamento;
* DPI;
* orientação;
* visibilidade;
* input.

Nenhum desses requisitos deve obrigar a aplicação a conhecer APIs específicas do sistema operacional.

---

# 8. Frame loop

O frame loop é uma abstração fundamental.

O backend deve controlar:

```text
clock
vsync
frame scheduling
polling
render submission
present
```

O programa recebe:

```text
dt
```

onde:

```text
dt = tempo desde o frame anterior
```

O contrato precisa definir posteriormente:

* unidade;
* precisão;
* comportamento do primeiro frame;
* comportamento após frame longo;
* limite de `dt`;
* pausa;
* janela minimizada;
* perda de foco.

---

# 9. Relógio virtual

Para tornar testes determinísticos, a arquitetura deverá permitir substituir o relógio real por um relógio virtual.

Exemplo:

```text
frame 1 → dt = 16ms
frame 2 → dt = 16ms
frame 3 → dt = 16ms
frame 4 → dt = 32ms
```

Assim, o mesmo programa poderá produzir um observável determinístico.

Isso é especialmente importante para:

* physics;
* animações;
* input;
* áudio;
* reprodução;
* golden tests.

---

# 10. Input

Input de jogos deve ser tratado como snapshot.

Exemplo:

```kof
frame { dt ->
    if (keys.down("left")) {
        player.left(dt)
    }

    if (keys.pressed("space")) {
        player.fire()
    }
}
```

## 10.1 Estados

A abstração deve distinguir:

```text
down
pressed
released
```

quando necessário.

A semântica exata permanece TBD.

## 10.2 Mouse

Intenção:

```kof
mouse.pos
mouse.down("left")
mouse.pressed("left")
```

## 10.3 Gamepad

Intenção:

```kof
pad.stick("left")
pad.down("a")
pad.pressed("start")
```

## 10.4 Teclado

A aplicação não deve precisar conhecer:

* scan codes;
* virtual key codes;
* X11 keycodes;
* Wayland codes;
* browser KeyboardEvent;
* Windows virtual keys.

O backend faz a tradução.

---

# 11. 2D

O primeiro nível gráfico deve ser 2D.

## 11.1 Sprite

```kof
var player = sprite("player.png")
player.at(120, 80)
player.draw()
```

## 11.2 Transformações

A família poderá contemplar:

```text
at
scale
turn
origin
flip
```

A API final é TBD.

## 11.3 Animação

Conceitualmente:

```kof
player.frames("walk")
player.animate()
```

ou equivalente.

A plataforma deve cuidar de:

* atlas;
* batching;
* upload;
* frame selection.

---

# 12. Tilemaps

Tilemaps devem representar intenção de mapa, e não gerenciamento manual de textura.

Possível intenção:

```kof
var level = tilemap("level.png", 16)
level.at(0, 0)
level.draw()
```

Questões futuras:

* tileset separado;
* atlas;
* layers;
* collision metadata;
* animated tiles;
* infinite maps;
* external map formats.

Não incluir funcionalidades de domínio que não sejam necessárias para a primeira fatia.

---

# 13. Rendering 2D

A aplicação declara:

```text
o que desenhar
```

O backend decide:

```text
como desenhar
```

O backend poderá realizar:

* batching;
* texture atlas;
* command buffering;
* draw ordering;
* texture caching;
* resource upload.

Esses detalhes não devem aparecer na API básica.

---

# 14. 3D

3D é deliberadamente posterior.

A superfície mínima planejada é:

```text
mesh
camera
material
light
transform
```

Exemplo:

```kof
var hero = mesh("hero.glb")
hero.with(material.stone)

var camera = camera3d()
camera.at(0, 2, 5)
camera.lookAt(hero.pos)

draw(scene3d {
    hero
    light.sun()
})
```

A sintaxe é apenas ilustrativa.

---

# 15. Formatos 3D

O Kof não deve implementar parsers próprios para formatos complexos.

Formatos candidatos:

```text
glTF / GLB
OBJ
```

A decisão definitiva depende da stack selecionada.

O backend poderá delegar parsing a bibliotecas maduras.

Critérios:

* licença;
* segurança;
* cobertura;
* manutenção;
* testabilidade;
* multiplataforma.

---

# 16. Materiais e shaders

O primeiro nível de abstração deve esconder:

* API gráfica;
* pipeline state;
* shader compilation;
* descriptor binding;
* uniform buffers;
* vertex buffers.

Shaders customizados são uma questão separada.

Antes de expor shaders ao usuário, deverá existir decisão sobre:

```text
Kof shader language?
SPIR-V?
WGSL?
GLSL?
HLSL?
cross compilation?
```

Essa decisão não faz parte da primeira fatia.

---

# 17. Áudio

Áudio deve possuir duas intenções principais:

```text
sound
music
```

Exemplo:

```kof
sound("boom.ogg").play()

var music = music("theme.ogg")
music.loop()
music.play()
```

A distinção existe semanticamente, mas a implementação pode decidir:

* preload;
* streaming;
* cache;
* decoder;
* buffer.

---

# 18. Contrato de áudio

O backend deverá controlar:

```text
decoder
buffer
mixer
output
device
latency
voice management
```

A aplicação não deve precisar criar:

```text
audio channel
audio buffer
audio callback
audio thread
```

manualmente.

---

# 19. Mixagem

A plataforma deverá ser responsável pelo mixer.

A API poderá futuramente oferecer:

```kof
sound("shot.wav").volume(0.5)
music("theme.ogg").volume(0.3)
```

Possíveis capacidades futuras:

```text
volume
pause
resume
stop
loop
fade
pan
```

Cada uma precisa de contrato cross-target antes de promoção.

---

# 20. Latência

"Baixa latência" não deve ser tratado como promessa vaga.

A implementação deverá medir:

```text
request play
      ↓
audio buffer submission
      ↓
audible output
```

por target.

O valor do contrato só será definido depois do spike 3.0/3.3.

---

# 21. Dispositivos de áudio

Possível superfície:

```kof
audio.devices()
audio.device(...)
```

Mas o comportamento depende do target.

Browser, por exemplo, possui restrições diferentes de um processo Native.

A API deve representar a capacidade comum, enquanto gaps específicos devem produzir diagnóstico honesto.

---

# 22. Vídeo

Vídeo será integrado à superfície de mídia/UI.

Intenção:

```kof
Window("Trailer") {
    video("intro.mp4").autoplay()
}
```

A aplicação não deverá manipular:

* demuxer;
* decoder;
* codec;
* frame queue;
* hardware decoder.

O backend é responsável por isso.

---

# 23. Codec

Codecs não serão implementados pelo Kof.

A plataforma selecionará bibliotecas maduras.

Possíveis fontes:

```text
FFmpeg
Libav
browser native codecs
OS media frameworks
```

A decisão dependerá da análise de:

* licença;
* target;
* segurança;
* manutenção;
* suporte de formatos;
* capacidade headless.

---

# 24. `kof.media`

A implementação futura deverá decidir como evoluir a face atual.

Hoje:

```text
bitmap
WAV
video metadata
microphone
```

Futuro:

```text
playback
streaming
mixing
video playback
```

A evolução deve ser aditiva sempre que possível.

Programas Kof existentes não devem quebrar simplesmente porque a implementação interna foi substituída.

---

# 25. KofUI

KofUI e gráficos não devem virar duas linguagens concorrentes.

A divisão conceitual é:

```text
KofUI
→ aplicações de interface

Graphics/Game
→ aplicações interativas e jogos
```

Existe interseção em:

* janela;
* input;
* vídeo;
* imagens;
* eventos.

Essas partes devem compartilhar infraestrutura quando semanticamente equivalentes.

---

# 26. KofJS

No browser:

```text
Kof
 ↓
KofJS
 ↓
Browser
```

A implementação gráfica deverá usar as capacidades do browser como backend.

Isso não significa que a API Kof será HTML.

Por exemplo:

```kof
video("intro.mp4")
```

pode resultar internamente em um elemento HTML apropriado.

Isso é detalhe de lowering.

---

# 27. WASM

Quando o target WASM existir:

```text
Kof
 ↓
WASM
 ↓
Browser/Host
```

A mesma superfície de intenção deve permanecer válida.

A integração WASM deverá ser documentada separadamente em:

```text
WASM/WASI implementation plan
```

e não duplicada aqui.

---

# 28. Native

O backend Native deve utilizar a stack portátil selecionada.

O objetivo:

```text
Kof
 ↓
Native
 ↓
graphics/audio/video platform layer
```

sem exigir que o usuário escreva bindings manualmente.

Targets:

```text
x86-64
aarch64
riscv64
```

devem entrar na matriz individualmente.

---

# 29. JVM

JVM não deverá utilizar:

```text
JavaFX
Swing
AWT
javax.sound
```

como backend oficial da superfície.

A arquitetura desejada é:

```text
Kof
 ↓
JVM
 ↓
R3 FFI/ABI
 ↓
portable platform stack
```

Isso mantém a semântica alinhada com Native e demais targets.

---

# 30. KofScript

KofScript deve utilizar a mesma semântica de intenção.

Não deve existir uma API gráfica exclusiva para Script.

O runtime Script deverá delegar para a implementação disponível no ambiente.

Caso o ambiente não possua capacidade:

```text
GFX001
SND001
VID001
```

ou código equivalente deverá ser produzido.

---

# 31. Capability Matrix

Cada operação gráfica/mídia precisa declarar suas capabilities.

Exemplo conceitual:

```text
Capability        JVM Native JS Script
window             ?     ?    ✓    ?
sprite             ?     ?    ✓    ?
audio              ?     ?    ?    ?
video              ?     ?    ?    ?
3d                 ?     ?    ?    ?
```

O valor só vira `✓` após:

1. implementação;
2. testes;
3. golden;
4. paridade;
5. documentação.

---

# 32. Gap codes

Nunca esconder capability ausente.

Famílias:

```text
GFX00x
INP00x
SND00x
VID00x
```

Exemplo:

```text
GFX001 — graphics capability unavailable on target
SND001 — audio playback unavailable on target
VID001 — video playback unavailable on target
```

Os códigos definitivos precisam entrar no catálogo normativo antes da implementação.

---

# 33. Regra de paridade

A superfície gráfica só será promovida quando todos os targets obrigatórios apresentarem comportamento equivalente.

Isso significa:

```text
JVM       ┐
Script    │
Native    ├── mesmo contrato observável
JS-Web    ┘
```

Não basta:

```text
compila em todos
```

É necessário:

```text
compila
+
executa
+
produz comportamento esperado
+
passa conformance
```

---

# 34. Observabilidade gráfica

Pixels precisam ser testáveis.

O sistema futuro deverá possuir uma forma determinística de:

```text
render
 ↓
readback
 ↓
buffer
 ↓
hash
```

O teste compara o contrato observável.

Não comparar diretamente:

* driver;
* GPU;
* framebuffer físico;
* screenshot sujeito a diferenças de hardware.

O formato exato do hash e tolerância, se houver, serão definidos durante a implementação.

---

# 35. Observabilidade de áudio

Áudio deverá possuir modo de teste offline:

```text
program
 ↓
audio mixer
 ↓
PCM buffer
 ↓
hash/reference
```

Isso permite testar:

* volume;
* mix;
* ordem;
* loop;
* duração;
* canais.

Sem depender de alto-falante físico.

---

# 36. Conformance

Cada operação terá testes cross-target.

Exemplo:

```text
graphics/sprite/basic.kof
graphics/input/pressed.kof
audio/play/basic.kof
audio/mix/basic.kof
media/video/basic.kof
```

O harness executa:

```text
JVM
Script
Native
JS
```

e compara os observáveis.

---

# 37. Golden tests

Golden tests devem ser utilizados para:

* frame sequence;
* input sequence;
* sprite rendering;
* transformations;
* audio mixing;
* media metadata;
* video decoding.

Para tempo:

```text
virtual clock
```

Para input:

```text
deterministic input stream
```

Para áudio:

```text
offline mixer
```

Para vídeo:

```text
deterministic frame readback
```

---

# 38. Fuzzing

O backend deve possuir fuzzing específico para:

* transformação;
* coordenadas;
* tamanho;
* textura;
* input;
* lifecycle;
* asset loading;
* malformed media;
* audio files;
* video containers;
* resource release.

Especial atenção para arquivos de mídia não confiáveis.

---

# 39. Segurança

Arquivos de imagem, áudio e vídeo são dados não confiáveis.

O backend deverá considerar:

* malformed files;
* integer overflow;
* memory corruption;
* decompression bombs;
* decoder vulnerabilities;
* resource exhaustion;
* sandbox boundaries.

O Kof não deve implementar codecs próprios justamente para evitar assumir responsabilidade desnecessária por esse código complexo.

---

# 40. Stack de terceiros

A seleção da stack deve ser resultado do spike 3.0.

Candidatos:

### Graphics/window/input

```text
SDL3
SDL2
raylib
GLFW + graphics API
```

### Audio

```text
miniaudio
OpenAL Soft
SDL audio
```

### Video

```text
FFmpeg
Libav
native browser/OS decoder
```

A seleção deverá avaliar:

```text
license
target coverage
maintenance
security
headless support
cross compilation
API stability
binary size
startup
performance
```

Não escolher por familiaridade do desenvolvedor.

---

# 41. Licenciamento

A licença das dependências deve ser analisada antes de qualquer integração.

Especialmente:

```text
GPL
LGPL
zlib
MIT
BSD
Apache
```

A análise deve considerar:

* distribuição do Kof;
* runtime;
* executável final;
* linking;
* static linking;
* dynamic linking;
* Native;
* JVM;
* JS;
* distribuição oficial.

Nenhuma dependência será aprovada apenas porque "é open source".

---

# 42. Não criar wrappers gigantes

A camada Kof deve permanecer pequena.

O objetivo é:

```text
Kof API
   ↓
thin abstraction
   ↓
backend
```

Não:

```text
Kof API
   ↓
reimplementação completa da SDL
   ↓
reimplementação completa do renderer
```

A plataforma é responsável pela complexidade.

---

# 43. Performance

Performance será medida.

Benchmarks futuros:

```text
startup
window creation
frame scheduling
sprite throughput
texture upload
draw calls
input latency
audio latency
mix throughput
video decode
memory
```

Comparar targets somente quando o benchmark representar a mesma operação semântica.

Não criar promessa de:

```text
"Native é X vezes mais rápido"
```

sem medição.

---

# 44. Memory budget

A implementação deverá monitorar:

```text
runtime memory
texture memory
audio buffers
video buffers
temporary allocations
```

Especialmente em:

```text
Native
mobile
WASM
embedded
```

A API não deve obrigar o usuário a administrar manualmente cada buffer.

---

# 45. Resource caching

Assets poderão ser cacheados pela plataforma.

Possíveis recursos:

```text
texture cache
sound cache
font cache
mesh cache
video cache
```

A política deve ser transparente.

A aplicação deve poder solicitar liberação quando necessário, caso o contrato final determine essa necessidade.

---

# 46. Assets

O build system deverá eventualmente reconhecer assets gráficos/mídia.

Exemplo:

```text
assets/
    sprites/
    sounds/
    music/
    video/
    models/
```

A documentação deverá definir posteriormente:

* copy;
* embed;
* compression;
* hashing;
* cache;
* path resolution;
* packaging.

Isso não deve ser implementado como parte da primeira fatia.

---

# 47. Packaging

O futuro `kof build` deverá saber que uma aplicação gráfica possui mais que código.

Possível resultado:

```text
application
├── executable
├── runtime
├── assets
└── metadata
```

No browser:

```text
application
├── JS/WASM
├── assets
└── bootstrap
```

O formato final permanece TBD.

---

# 48. Desenvolvimento headless

Todo componente possível deve possuir caminho headless.

Isso é essencial para:

* CI;
* testes;
* fuzzing;
* servidores;
* conformance.

Exemplo:

```text
graphics backend
    ↓
headless renderer
    ↓
render buffer
```

Sem abrir janela física.

---

# 49. Desenvolvimento local

Quando executado normalmente:

```bash
kof run
```

o backend pode utilizar janela/dispositivo real.

Mas:

```bash
kof test
```

não deverá depender de:

* monitor;
* GPU específica;
* caixa de som;
* microfone;
* câmera.

---

# 50. Android

Quando KofAndroid entrar nessa superfície, ele deverá utilizar a mesma intenção.

Não criar:

```text
KofAndroidGraphics
```

como uma linguagem separada.

A plataforma Android deverá implementar o contrato gráfico/mídia comum.

---

# 51. Futuro mobile

Os mesmos conceitos poderão posteriormente atender:

```text
Android
iOS
```

se esses targets forem suportados.

A arquitetura não deve bloquear isso.

Porém, iOS não entra no escopo desta fase sem decisão explícita.

---

# 52. Debugging

O debugger futuro deverá conseguir relacionar:

```text
Kof source
 ↓
graphics operation
 ↓
runtime
```

quando houver suporte.

Especialmente importante para:

* frame callback;
* resource creation;
* runtime errors;
* asset loading.

Não é necessário expor internals da GPU ao debugger inicial.

---

# 53. Diagnóstico

Erros devem apontar para o código Kof.

Exemplo ruim:

```text
SIGSEGV in libSDL...
```

Exemplo desejável:

```text
Kof graphics error GFX002

Resource:
    sprite("player.png")

Reason:
    asset could not be loaded

Source:
    game.kof:42
```

Quando possível, o backend deve traduzir falhas externas em diagnósticos Kof.

---

# 54. Asset errors

Erros de assets devem diferenciar:

```text
file missing
unsupported format
decode failure
permission denied
resource exhausted
```

Não transformar tudo em:

```text
asset not found
```

---

# 55. Threading

A implementação deverá definir claramente:

```text
main thread
render thread
audio thread
background loading
```

A aplicação não deve assumir um modelo específico.

O runtime controla isso.

Isso precisa ser compatível com:

```text
spawn
async
channels
scheduler
```

do Kof.

---

# 56. Determinismo

Jogos não precisam ser deterministicamente iguais em performance.

Mas testes precisam ser determinísticos.

Distinguir:

```text
runtime behavior
```

de:

```text
test behavior
```

A implementação deve evitar introduzir nondeterminismo no conformance harness.

---

# 57. Fases de implementação

## 3.0 — Spike e infraestrutura

Objetivo:

* avaliar stack;
* validar R3;
* validar FFI;
* medir licença;
* validar headless;
* validar cross-compilation;
* implementar guarda contra JavaFX.

Saída:

```text
architecture report
```

Nenhuma API Kof nova ainda.

---

## 3.1 — Janela, frame e input

Implementar futuramente:

```text
window
frame
clock
keyboard
mouse
basic gamepad
```

Critério:

```text
JVM ✓
Script ✓
Native ✓
JS ✓
```

com conformance.

---

## 3.2 — 2D

Implementar:

```text
sprite
texture
transform
tilemap
draw
```

Critério:

* golden;
* headless;
* cross-target;
* assets;
* lifecycle.

---

## 3.3 — Áudio

Implementar:

```text
sound
music
play
pause
stop
loop
volume
```

e infraestrutura de:

```text
decoder
mixer
device
```

Critério:

```text
offline PCM golden
```

---

## 3.4 — Vídeo

Evoluir `kof.media`.

Implementar:

```text
video
play
pause
seek
volume
```

quando o contrato estiver definido.

Critério:

```text
deterministic frame readback
```

---

## 3.5 — 3D

Somente iniciar se:

* stack suportar;
* targets suportarem;
* R3 suportar;
* runtime suportar;
* conformance puder ser determinístico.

Caso contrário:

```text
GFX00x
```

continua válido.

---

## 3.6 — Corpus e promoção

Atualizar:

```text
training/
learn/
docs/
conformance/
backend-parity/
```

Promover somente quando os gates forem cumpridos.

---

# 58. Critérios de promoção

Uma fatia só sai de `future/` quando:

```text
[ ] implementação concluída
[ ] sem código experimental escondido
[ ] runtime concluído
[ ] todos os targets obrigatórios
[ ] conformance
[ ] golden
[ ] headless
[ ] documentação
[ ] gaps catalogados
[ ] performance medida
[ ] segurança revisada
[ ] licença revisada
[ ] corpus atualizado
```

"Funciona no meu computador" não é critério de promoção.

---

# 59. Questões abertas

## Q1 — R1

`kof.sound` e `kof.media` permanecem stdlib core?

A superfície de jogo será:

```text
kof.game
```

ou outro namespace?

---

## Q2 — Scene

Qual forma será escolhida?

```kof
scene "Pong" {
    frame { dt ->
    }
}
```

ou:

```kof
Scene("Pong") { dt ->
}
```

A segunda mantém o princípio de evitar syntax additions quando uma função/HOF existente resolve a intenção.

---

## Q3 — 3D

3D entra apenas após a paridade total?

---

## Q4 — Golden

Qual será o contrato definitivo para:

```text
pixel hash
audio hash
video frame hash
```

---

## Q5 — Stack

Qual stack será escolhida após medição?

```text
SDL
raylib
GLFW
ou outra
```

---

## Q6 — Input

A superfície terá:

```text
snapshot
events
ambos
```

---

## Q7 — WASM

Quando WASM estiver disponível, ele entra automaticamente na matriz de paridade?

A resposta esperada arquiteturalmente é sim, mas a decisão formal pertence ao plano WASM/WASI.

---

## Q8 — Media atual

A face JVM existente será:

```text
mantida e expandida
```

ou:

```text
rebased
```

sobre a nova infraestrutura?

A compatibilidade dos programas existentes deve ser preservada.

---

# 60. Non-goals permanentes

Este plano não pretende:

* criar renderer próprio;
* criar mixer próprio;
* criar codec próprio;
* criar demuxer próprio;
* expor SDL;
* expor OpenGL;
* expor WebGL;
* expor DOM;
* expor HTML;
* expor CSS;
* expor JavaFX;
* expor Swing;
* expor AWT;
* criar API específica para cada target;
* criar uma linguagem de shaders antes da necessidade;
* criar abstrações que apenas embrulham APIs estrangeiras;
* aceitar paridade parcial como feature oficial;
* esconder capability ausente;
* abrir implementação sem promoção da mantenedora.

---

# 61. Regra de ouro

O teste mais importante para qualquer proposta desta área é:

> **Um desenvolvedor Kof precisa pensar em gráficos, áudio e mídia, ou precisa pensar na plataforma que está por baixo?**

A resposta desejada é:

```text
pensar na intenção.
```

Se para escrever:

```kof
sound("shot.ogg").play()
```

o desenvolvedor precisar saber como o áudio funciona no Linux, Windows, browser ou Android, a abstração falhou.

Se para desenhar um sprite precisar saber qual renderer está sendo usado, a abstração falhou.

Se para criar uma janela precisar saber qual API o sistema operacional fornece, a abstração falhou.

A plataforma existe para absorver essa complexidade.

---

# 62. Resultado arquitetural esperado

Ao final deste plano, a visão do Kof é:

```text
                    Kof Application
                           │
            ┌──────────────┼──────────────┐
            │              │              │
          UI           Graphics         Media
            │              │              │
            └──────────────┼──────────────┘
                           │
                  Kof Runtime Contract
                           │
                     Target Backend
                           │
       ┌──────────┬────────┼────────┬──────────┐
       │          │        │        │          │
      JVM       Native   Script     JS       WASM
       │          │        │        │          │
       ▼          ▼        ▼        ▼          ▼
   Platform    Platform  Runtime  Browser    Host
```

A aplicação permanece Kof.

O backend absorve a plataforma.

A linguagem permanece orientada à intenção.

A paridade permanece um requisito de promoção.

E a complexidade necessária para fazer gráficos, jogos e mídia funcionar fica onde deve ficar:

> **na implementação da plataforma, não no código que o desenvolvedor Kof escreve.**
