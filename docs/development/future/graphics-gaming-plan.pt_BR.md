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

_(medição real, não memória)_

## 1. Superfície de intenção por domínio

### 1.1 Janela + game loop (`scene`/`frame`)
### 1.2 2D — sprites e tiles
### 1.3 3D — malha, câmera, material (escopo honesto)
### 1.4 Input por frame
### 1.5 Som — playback, streams, mix, latência, dispositivos
### 1.6 Vídeo — componente de reprodução, chrome da plataforma

## 2. Aceite = paridade TOTAL multi-alvo

## 3. Stack por alvo (interop-first, medir antes de prometer)

## 4. A fronteira R1 — `kof.sound`/`kof.media` vs pacotes oficiais

## 5. Códigos de gap honestos + a matriz de paridade

## 6. ERRADICAÇÃO — JavaFX nunca foi Kof e nunca vai ser

## 7. NON-GOALS

## 8. Fila de fatias (3.x) com critérios de prova

## 9. QUESTÕES ABERTAS para a mantenedora (regra 6)
