[English](graphics-gaming-plan.md) | [Português](graphics-gaming-plan.pt_BR.md)

# Gráficos, jogos e mídia — a superfície de intenção do Kof (plano future)

**Estado:** Plano (só design) — **zero código**; mora em `future/` até a
mantenedora promover uma fatia (regra dos três estados + R12).
**Fonte:** `DECISIONS.md` §D-GRAPHICS-GAMING (20/09) + adendo (mídia
som+vídeo) + adendo 2 (sem JavaFX + paridade total) + adendo 3 (Kof nunca
usou JavaFX — eradicação) · `AGENTS.md` regras 8/9/10/11 · regra JavaFX (12/09).

> **Regra deste documento:** é um **plano para o futuro** — não muda
> comportamento e não abre frente. Toda sintaxe abaixo é a **forma da
> intenção**, não gramática comprometida; a superfície exata é decisão de
> regra 6 da mantenedora. Nenhuma lane pode atacá-la sem promoção explícita.

## 0. Estado real medido (21/09)

Medição real no tip da `beta-0.5.0` — não memória:

1. **JavaFX zero no código.** `import javafx` / uso qualificado `javafx.`
   em todo `kof-*/src`: **0**; `javafx`/`openjfx` em qualquer `pom.xml`:
   **0**. Os 6 arquivos de `src/main` que casam a palavra são **comentários
   sobre a regra do JavaFX** (o launcher engolindo um `VerifyError`) — uso
   correto, mantido. Ratificado pelo corpus: `training/language/ui.md` —
   *"There is no JavaFX, AWT or GUI dependency in any backend."* → o
   adendo 3 confirmado por medição: Kof nunca usou JavaFX (§6).
2. **`kof.ui` em JVM/Native é alça no-op.** A superfície do runtime JVM
   gerado (`jvm/JvmRuntimeUi.java`: `kof_ui_window_new` retorna `1`,
   setters/show têm corpo vazio). A renderização de widgets é
   **exclusiva do alvo KofJS** (DOM + webview nativo `bin/kof-webview`,
   WebKitGTK) — `training/language/ui.md` §"Semantics across targets".
3. **`kof.media` EXISTE hoje, só face JVM.** `KofMedia.java` +
   `jvm/JvmMediaCoreRuntime.java` / `JvmMediaWebRuntime.java`: abrir/salvar
   Bitmap, **metadados** de vídeo (sem decodificar frames — o app não
   decodifica; gap honesto anotado no próprio fonte), operação de amostra
   de áudio **WAV**, lista/gravação de microfone. Códigos de gap em uso:
   `MEDIA001` (default) e `MEDIA003` (`mic_record`) — `KofMedia.gapCode`.
   Não existe **pipeline de reprodução** (nenhum `play()`), nem mixagem,
   nem face JS/Native.
4. **Dependência dura da R3 (FFI/ABI).** Uma stack portátil de
   áudio/gráficos em JVM e Native chega pelo front de interop
   (`IMPLEMENTATION-UNIVERSAL-PLATFORM.md` R3; handles/out-buffers = passo
   2.8.2 do roadmap, `D-R3-3.3` DECIDIDO 21/09). Enquanto a R3 não landar,
   o plano não tem veículo — fica em `future/` (R12: nenhuma frente nova
   antes do SYSTEMS fechar).

## 1. Superfície de intenção por domínio

**Cada item abaixo é a forma da intenção** — curta, declarativa, o que o
usuário *quer dizer*; a plataforma rebaixa por alvo (§3). Nenhum é gramática
comprometida (portão da regra 11: um humano escreveria exatamente isso em
Kof?). Nenhum é API estrangeira transcrita (regras 8/10): não existe
`SDL_CreateWindow`, nem `<canvas>`, nem `MediaView` de toolkit em código
Kof — só o que um revisor Kof não acharia vergonhoso.

### 1.1 Janela + game loop (`scene`/`frame`)

Hoje `spawn`/`await` expressam concorrência; um jogo é um **frame loop**: a
plataforma é dona do relógio (vsync/tick), o usuário é dono de
`update`+`draw`. A intenção:

```kof
scene "Pong" {
    frame { dt ->
        ball.move(speed * dt)
        clear(black)
        draw(ball)
    }
}
```

- `scene` declara *"este programa é uma janela interativa com loop"* —
  título, criação de janela, fiação do loop principal e encerramento
  pertencem à plataforma, nunca ao código do usuário.
- `dt` é o tempo desde o frame anterior — o idioma determinístico de
  tempo de jogo; a taxa de frames é detalhe da plataforma.
- Alternativa candidata (mesma intenção, expressão em vez de bloco):
  `run onFrame { dt -> ... }` — **qual forma entra é decisão de regra 6**
  (§9 Q2); o *conceito* (loop da plataforma, passo do usuário) não está em
  renegociação, porque toda biblioteca de jogo de toda língua obriga o
  usuário a fiar o loop — o Kof absorve o mecanismo (diretriz primária).

### 1.2 2D — sprites e tiles

```kof
var ball = sprite("ball.png")
ball.at(120, 80)
ball.draw()

var level = tilemap("level.png", 16)
level.draw()
```

- `sprite(path)` carrega pela plataforma (suporte a codec/formato é
  problema da plataforma, como `image` em `kof.media`); `at`/`draw` são
  verbos de intenção. Animação, rotação e escala crescem da mesma família
  de verbos (`frames(...)`, `turn(...)`, `scale(...)`) — sem "SpriteBatch",
  sem "renderer.begin()", sem contabilidade de atlas no código do usuário
  (a plataforma faz o batching).

### 1.3 3D — malha, câmera, material (escopo honesto)

```kof
var cam = camera3d().at(0, 2, 5).lookAt(hero.pos)
draw(scene3d {
    mesh("hero.glb").with(material.stone)
    light.sun()
})
```

- `mesh`/`material`/`camera3d` declaram **o que** a cena é; upload para
  GPU, shaders, batching e ordem de desenho são o **como**, da plataforma.
- **Escopo honesto:** o 3D é a última fatia (§8 3.5) e pode legitimamente
  **não ser promovido** se algum alvo não alcançar paridade (§2 vale sobre
  o R7 aqui, por ordem da mantenedora). Carregar formatos (`glb`/`obj`) vai
  por libs maduras — nunca parser caseiro de formato binário de cena.

### 1.4 Input por frame

Input de jogo é um **snapshot lido dentro do frame**, não fiação de eventos
de UI:

```kof
frame { dt ->
    if (keys.down("left")) ship.turn(-speed * dt)
    if (keys.pressed("space")) ship.fire()
    aim.aimAt(mouse.pos)
}
```

- `keys.down` (segurada) vs `keys.pressed` (neste frame) — as duas
  perguntas que um jogo faz; autorepeat/debounce é ruído de plataforma que
  o idioma remove. Mouse/ponteiro: `pos`; gamepad por intenção
  (`pad.stick()`, `pad.pressed("a")`).
- Os eventos-lambda existentes de `kof.ui` continuam para formulários; eles
  **não** são o idioma de jogo (§9 Q6 fixa a superfície exata).

### 1.5 Som — reprodução, streams, mix, latência, dispositivos

O exemplo do canon (`sound.play("x.ogg")`) crescido para a superfície honesta
de áudio de jogo:

```kof
var boom = sound("boom.ogg")        // pré-carregado → SFX de baixa latência
boom.play()
var bgm = music("theme.ogg")        // em stream — nunca carrega o arquivo
bgm.loop()
bgm.stop()
sound("ok.ogg").volume(0.3)
```

- Uma família de verbos (`play`/`stop`/`pause`/`volume`) sobre dois
  comportamentos de plataforma: amostra pré-carregada vs stream —
  **escolhido pela plataforma pelo uso, sem cerimônia no código do
  usuário** (nada de tipos `AudioClip` vs `AudioStream`).
- **Contrato de latência (medido, não prometido):** `play()` de SFX vindo
  de dentro do frame tem de ser audível sem atraso perceptível em todo
  alvo — o número sai de medição por alvo na fatia 3.3 (§9 Q4). Mixagem e
  orçamento de vozes são da plataforma; "canais" só como diagnóstico de
  limite, nunca como fiação do usuário.
- Dispositivos: `audio.devices()` + seleção `audio.device(...)` —
  enumeração onde o alvo tem dispositivos de verdade (web = saída onde o
  browser permite; `SND00x` honesto onde o conceito não existe — §5).
- Formatos: a **plataforma** escolhe o decodificador (`ogg`/`mp3`/`wav`...)
  via libs auditadas (§3) — código Kof nomeia um arquivo, nunca um codec.

### 1.6 Vídeo — componente de reprodução, chrome da plataforma

```kof
Window("Trailer") {
    video("intro.mp4").autoplay()
}
```

- `video` é um componente da família de painéis de `kof.ui` — mesmo nível
  de intenção de `Label`/`Button`; o chrome do player (controles, barra de
  seek, tela cheia) pertence à plataforma, nunca ao código do usuário.
- Demux/decode vêm de libs maduras (§3); a face JVM atual
  `kof_media_video_*` (metadados) cresce para reprodução na fatia 3.4.
  Nenhuma tag `<video>` nem a forma `MediaView` de toolkit cruza para o
  Kof (regras 8/9/10).

## 2. Aceite = paridade TOTAL multi-alvo

**Ordem da mantenedora (adendo 2): para esta superfície o "escopo honesto
por alvo" do R7 NÃO se aplica.** Um recurso de gráficos/mídia entra na
superfície da linguagem somente quando **todo** alvo — JVM, KofScript,
Native (x86-64, riscv64, aarch64), JS-Web — roda o **mesmo programa com o
mesmo comportamento**; senão o recurso **nem é promovido** (fica como gap
com diagnóstico, §5 — nunca uma superfície parcial).

- **"Mesmo comportamento" é medido do jeito que a casa já prova
  paridade:** golden E2E em todo alvo, byte-a-byte no observável (a família
  `runAll3`/`runAll4` de E2ETests, os gates cross-arch, o harness da matriz
  de 8 alvos do EG-5). Superfícies sensíveis a tempo (frame loop, áudio)
  são testadas sob **relógio virtual** (sequência fixa de `dt`, mix de
  áudio offline) para o golden ser determinístico.
- **Observabilidade de pixel/áudio:** a superfície precisa expor um
  readback determinístico para testes (render-to-buffer com hash; mix
  offline para buffer) — o golden compara o **contrato observável**, não um
  artefato de GPU/driver. O mecanismo exato é medido na fatia 3.1 antes de
  qualquer promoção (§9 Q4).
- Até a paridade valer, o alvo responde com o código de gap honesto e
  mensagem clara (R6) — ex.: um build JS de um programa 3D diz `GFX00x` e
  falha com diagnóstico, nunca uma tela preta silenciosa.

## 3. Stack por alvo (interop-first, medir antes de prometer)

R9: a primeira pergunta é sempre "já existe fora e é melhor?" — e aqui a
resposta é sim em toda parte. **Nenhum renderizador caseiro, nenhum codec
caseiro, nenhum mixer caseiro** (non-goal permanente; adendo: crypto e
codecs nunca caseiros). O plano **avalia e mede** (spike 3.0 abaixo) — não
casa com lib por nome:

| Camada | Candidatos a MEDIR (licença × cobertura × testabilidade headless) | Nota |
|---|---|---|
| Janela/GL/input portátil | classe **SDL3/SDL2, raylib, GLFW+GL** | a forma que o adendo 2 nomeia: uma camada portátil, bindings por alvo — não chrome de plataforma |
| Áudio | **miniaudio** (zlib, arquivo único — cabe direto no Native), OpenAL-soft, áudio do SDL3 | mixagem/vozes da plataforma |
| Vídeo | **ffmpeg / Libav** (demux+decode) | questão GPL/LGPL vs saída GPLv3 — medida, nunca presumida |
| JS-Web | o browser **é** a plataforma: WebGL, WebAudio, `<video>` | a plataforma renderiza; tags nunca vazam ao usuário (§7) |

Por alvo (todos pelos mesmos verbos Kof de §1):

| Alvo | Veículo | Medido hoje |
|---|---|---|
| JVM | FFI (R3 `foreign`/Panama) para a stack portátil — **não** JavaFX/Swing/AWT | 0 JavaFX no código (§0); `kof.ui` JVM = no-op; `kof.media` JVM = só dados |
| KofScript | mesmo veículo do JVM (in-process, runtime compartilhado) | idem |
| Native x86-64 / riscv64 / aarch64 | link C direto da stack portátil (o backend já faz `cc`/cross-as) | sem face de áudio/vídeo hoje → gap honesto até paridade |
| JS-Web | lowering para as APIs do browser (o padrão DOM existente de `kof.ui` KofJS) | widgets renderizam aqui; nenhum pipeline de som/vídeo na superfície stdlib ainda |

**Rejeitados como backends (com motivo, não por gosto):** JavaFX/Swing/AWT
(adendos 2+3 + medido em §0); `javax.sound` e qualquer API de mídia só-JDK
— correta no JVM, mas é o *chrome de um alvo só*, e o adendo 2 manda o JVM
chegar ao idioma pela **mesma stack portátil** dos demais; canvas/HTML/CSS
(regras 8/9/10 — §7). **Alvos KofC/wasm são future:** quando landarem,
entram no mesmo gate de paridade como linhas extras, com honestidade
`XXX00x` no intervalo.

## 4. A fronteira R1 — `kof.sound`/`kof.media` vs pacotes oficiais

O gate R1 (`scripts/check_stdlib_boundary.sh` + ledger
`scripts/stdlib_boundary.txt`) decide a **camada do namespace**, não o peso
do backend: stdlib-base = *"essencial à plataforma e pequeno"*;
**domínios** pesados vão para pacotes oficiais (nascem `experimental`).

- **`kof.sound` (+ `kof.media` crescendo com playback de vídeo): stdlib
  core — recomendação.** Mídia é serviço de plataforma na mesma classe de
  JSON/DB/HTTP/crypto: a *superfície* é pequena (verbos de §1.5/§1.6), o
  trabalho pesado mora atrás dela na plataforma, e `kof.media` **já é core
  hoje** (§0.3). Som sem jogo é normal; instalar pacote para
  `sound.play("x.ogg")` seria cerimônia.
- **`scene`/`sprite`/`tilemap`/`camera3d` (a superfície de jogo): pacote
  oficial — recomendação** (nome de trabalho `kof.game`, `experimental`).
  Jogos são um *domínio* (os próprios exemplos da R1), e o escopo 3D é
  exatamente o tipo de capacidade pesada para que a camada de pacotes
  existe. A paridade (§2) vale para o pacote igual — experimental ≠
  isento.
- **O bloco `scene { frame { ... } }`, se escolhido como sintaxe, é decisão
  da LINGUAGEM independente da resposta R1** — keywords não moram em
  pacote. A forma sem sintaxe (só builtins/funções:
  `Scene("Pong") { dt -> ... }`) é a favorita da lei da simplicidade e fica
  aberta com §9 Q2.
- A ordem de registro é fixa pela R1: a linha da camada entra no ledger
  **antes** de qualquer namespace existir (senão o gate quebra o build).
  Decisão final: §9 Q1.

## 5. Códigos de gap honestos + a matriz de paridade

Toda face não atendida responde com código e mensagem (R6 — nunca
silêncio, nunca valor falso). Famílias propostas, mesmo estilo `XXX00x` de
`MEDIA001`/`WEB005`/`WASM001`:

| Família | Cobre | Primeiros códigos (exemplos da face honesta) |
|---|---|---|
| `GFX00x` | `scene`/janela, `sprite`, `tilemap`, `draw`, 3D | `GFX001` alvo sem veículo de gráficos (até R3/stack landar) |
| `INP00x` | `keys`/`mouse`/`pad` por frame | `INP001` snapshot de input não atendido neste alvo |
| `SND00x` | playback/stream/mix de som, dispositivos | `SND001` sem veículo de áudio; `SND002` formato não decodificável neste alvo (listar, nunca adivinhar) |
| `VID00x` | componente de reprodução `video` | `VID001` sem veículo de vídeo; `VID002` codec ausente — problema da plataforma, o usuário vê o código, não a flag |

A matriz a que o plano se compromete (coluna de hoje = medição do §0; uma
célula só vira ✅ quando o golden E2E do §2 roda nela; **os quatro ✅ juntos
ou o recurso não promove**):

| Superfície | JVM | Script | Native | JS-Web |
|---|---|---|---|---|
| `scene`/frame loop | `GFX001` (no-op hoje, §0.2) | `GFX001` | `GFX001` | `GFX001` (o DOM tem rAF como veículo — o mais perto do verde) |
| 2D sprite/tile | `GFX001` | `GFX001` | `GFX001` | `GFX001` |
| 3D malha/câmera/material | `GFX001` | `GFX001` | `GFX001` | `GFX001` |
| input por frame | `INP001` | `INP001` | `INP001` | `INP001` |
| som play/stream/mix | `SND001` (dados WAV existem, sem playback, §0.3) | `SND001` | `SND001` | `SND001` |
| reprodução `video` | `VID001` (só metadados, §0.3) | `VID001` | `VID001` | `VID001` |

## 6. ERRADICAÇÃO — JavaFX nunca foi Kof e nunca vai ser

O adendo 3 converte "migração" em **erradicação**: inventário medido de cada
ocorrência de `javafx` (21/09, `grep -rni`), cada uma classificada — as
únicas que seriam "bugs a remover" são ligações reais, e não existe nenhuma:

| Onde | Contagem | Classificação | Ação |
|---|---|---|---|
| código `kof-*/src/main` (`import javafx`, `javafx.`) | **0** | — | nada a erradicar; continua vero pelo gate §2 e pela rejeição §3 |
| comentários em `kof-*/src/main` (6 arquivos) | 6 | comentário **sobre a regra** (VerifyError disfarçado de mensagem do launcher) | manter — uso correto |
| comentários em `kof-*/src/test` (12 arquivos) | 12 | idem (sintoma da família de bugs §149) | manter |
| dependências em `pom.xml` | **0** | — | — |
| `DECISIONS.md` §D-GRAPHICS-GAMING corpo "JVM=JavaFX" | 1 | **alegação errada de doc no texto original da decisão** — já corrigida **no mesmo arquivo** pelo adendo 3 (o registro da decisão fica; a correção é o adendo) | não editar — o adendo vale |
| `docs/philosophy.md` "WebView/JavaFX em código de UI → rejeitado" | 1 | correto: uma **rejeição**, bate com a realidade | manter |
| `docs/status.md`, `known-bugs.md` "disfarçado de erro do launcher JavaFX" | 10+ | correto: nomeia o **sintoma** coberto pela regra de 12/09 | manter |
| `training/language/ui.md` "sem dependência de JavaFX, AWT ou GUI" | 1 | fato correto (bater com esta medição) | manter |

Regras que tornam a erradicação permanente:

1. **A regra do JavaFX de 12/09 não é relaxada por nada neste plano:** a
   mensagem de runtime `componentes de runtime do JavaFX não foram
   encontrados` **nunca** é benigna — é o launcher engolindo um
   `VerifyError`/`ExceptionInInitializerError` real; sempre causa raiz e
   conserto (um caminho JavaFX nunca é "acomodado" — isso é erro
   disfarçado).
2. **Guarda mecânica (proposta, fatia 3.0):** um check determinístico que
   quebra o build em qualquer `import javafx` / uso `javafx.` / dependência
   javafx sob `kof-*/src` (mesmo formato do gate de fronteira R1 da stdlib)
   — para que "nunca de novo" seja gate, não esperança.
3. **Compatibilidade retroativa não protege caminho JavaFX** (adendo 3
   (c)): código Kof de usuário nunca nomeou JavaFX; remover qualquer ligação
   hipotética não pode quebrar um programa Kof válido.

## 7. NON-GOALS

- **Nenhum vazamento de HTML, `<canvas>`, `<audio>`, `<video>`, CSS ou DOM
  para o código do usuário** — o browser é *backend*, não linguagem.
  Precedente: RawView #449 (regra 9): o código do usuário declara intenção;
  a plataforma renderiza.
- **Nenhuma transcrição de API estrangeira:** `SDL_CreateWindow`,
  `glfwSwapBuffers`, enums de OpenGL, formas `MediaView`/`MediaPlayer` de
  toolkit nunca chegam à superfície Kof (regras 8/10). O lowering é dono
  delas; o usuário é dono dos verbos (§1).
- **Nenhum renderizador, mixer, demuxer ou codec caseiro** (R9/R10; crypto
  e codecs nunca caseiros — non-goal permanente).
- **Nenhum "Kali in Kof"** (non-goal permanente dos invariantes da
  plataforma).
- **Nenhum JavaFX/Swing/AWT como backend em qualquer alvo** (§3, adendos
  2+3).
- **Nenhuma promoção com paridade parcial** (§2 vale sobre o R7 aqui, por
  ordem da mantenedora).
- **Nenhuma frente abre** deste documento: ele fica em `future/` até a
  mantenedora promover uma fatia (regra dos três estados + R12 + regra 6).

## 8. Fila de fatias (3.x) com critérios de prova

Ordem de promoção; **nenhuma fatia abre sem a mantenedora promover** (R12 +
regra 6). Cada fatia só shipa com a prova — o padrão de paridade da casa:
**E2E nos 4 alvos, byte-a-byte no observável (§2), mais a suíte vizinha
completa verde**. Uma fatia que não feche o último alvo em verde deixa o
recurso atrás do seu gap `XXX00x` e não promove — "a fila anda, a superfície
só cresce em paridade".

| # | Fatia | Escopo (uma linha) | Depende de | Prova |
|---|---|---|---|---|
| 3.0 | **medir + guarda** | spike classe SDL/raylib/GL + miniaudio/OpenAL + ffmpeg/Libav (licença×alvos×headless); guarda mecânica de binding javafx; `import javafx` quebra o build | R3 2.8.2 (handles) | relatório de medição escrito (o §3 deste doc ganha o veredito); teste da guarda RED-depois-GREEN |
| 3.1 | **janela + frame loop + input** | forma `scene` (pela Q2), loop a relógio do vsync, snapshot por frame (`keys`/`mouse`), relógio virtual + mecanismo de readback | 3.0 | golden byte-a-byte nos 4 alvos; diagnóstico de gap honesto onde não |
| 3.2 | **2D** | `sprite`/`tilemap`/`draw` + batching da plataforma | 3.1 | idem, + arestas (frame vazio, 1×1, off-screen) |
| 3.3 | **som** | play/stream/mix/volume/dispositivos; número do contrato de latência DEFINIDO da medição (Q4) | 3.0 (veículo de áudio; paralelo à 3.1) | golden de mix offline nos 4 alvos; enumeração de dispositivos honesta por alvo |
| 3.4 | **vídeo** | reprodução do componente `video` sobre `kof.ui`; chrome da plataforma; face de metadados existente absorvida | 3.3 + R3 | golden de readback de decodificação nos 4 alvos |
| 3.5 | **3D (escopado)** | conjunto mínimo honesto de `mesh`/`camera3d`/`material` — promovido SOMENTE se 3.0–3.4 provarem que o veículo segura paridade | 3.1–3.4 | golden nos 4 alvos ou fica `GFX00x` para sempre (Q3) |
| 3.6 | **corpus + promoção** | `training/idioms/graphics.md`+`audio.md`, `learn/`, `backend-parity.md`, tabela de gaps; o doc sai de `future/` | 3.1–3.5 | docs-lang 0/0/0 + células de conformance |

## 9. QUESTÕES ABERTAS para a mantenedora (regra 6)

Não decididas aqui — cada uma é decisão de design (semântica congelada /
superfície da linguagem / registro R1):

1. **Q1 — fronteira R1:** `kof.sound`+`kof.media` stdlib core e a
   superfície de jogo como pacote oficial `kof.game` (recomendação §4) —
   ou outro corte? A linha do ledger precisa ser registrada antes de
   qualquer namespace existir.
2. **Q2 — a forma do `scene`/`frame`:** bloco com sintaxe nova vs.
   builtins sem sintaxe (`Scene("Pong") { dt -> ... }` — a favorita da Lei
   da Simplicidade: sem keyword, sem cerimônia). Qual chega à superfície.
3. **Q3 — promoção do 3D:** o 3D entra na superfície (0.5.x/depois) ou fica
   com gap `GFX00x` até a paridade dos 4 alvos ser provada?
4. **Q4 — contratos mensuráveis:** o número de latência do som e o
   mecanismo do golden byte-a-byte para pixels/áudio (render-to-buffer com
   hash, mix offline, relógio virtual) — ratificados das medições 3.0/3.1
   antes da primeira promoção.
5. **Q5 — escolha da stack após 3.0:** qual camada portátil (classe SDL vs.
   raylib vs. GL cru), qual lib de áudio, ffmpeg vs. Libav — incluindo a
   **questão de licença** (stack GPL/LGPL sob a saída GPLv3).
6. **Q6 — superfície de input:** snapshot por frame (`keys.down`/`pressed`)
   vs. eventos vs. ambos; a interação exata com os eventos-lambda
   existentes de `kof.ui` (qual fica como idioma de formulário, qual é o
   idioma de jogo).
7. **Q7 — timing de promoção:** quando (se) a primeira fatia sai de
   `future/` — o R12 segura isto atrás de SYSTEMS/1.0, salvo ordem
   expressa dela (como `D-UNIVERSAL` fez uma vez).
8. **Q8 — a face de dados `kof.media` JVM-only existente** (imagem/WAV/
   metadados de vídeo, §0.3): manter aditiva sobre o topo da stack de
   paridade, ou rebaseá-la para a stack durante 3.3/3.4 — programas Kof de
   usuário não quebram em nenhum dos caminhos (a promessa de compat é aos
   programas, adendo 3 (c)).
