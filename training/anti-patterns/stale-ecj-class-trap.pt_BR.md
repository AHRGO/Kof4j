[English](stale-ecj-class-trap.md) | [Português](stale-ecj-class-trap.pt_BR.md)

# Os "297 erros" que não eram regressão — error-stubs ECJ velhos em `target/classes`

## Problema

O Maven daqui compila com **ECJ** (o Eclipse compiler), não javac. Quando um
arquivo-fonte tem erro de compilação, o ECJ não para o build como o javac:
ele **emite um `.class` que lança
`java.lang.Error: Unresolved compilation problem`** na linha exata, e o
reactor pode passar reto por isso. Esse classe então **mora em
`<modulo>/target/classes/`** e é empacotada no que os testes carregam — e
como o build é incremental, uma classe velha quebrada pode sobreviver muito
depois de a fonte ter sido consertada, se os timestamps nunca forçaram
recompilar o módulo **dependente**.

A armadilha: você roda a suíte completa num tip que moveu código, e em vez de
umas falhas pontuais você vê **centenas de ERROS** (este repo, 16/09: 297
erros — todo teste que encosta em `--target js` explodia com
`Unresolved compilation problem: KofJsWebview cannot be resolved` em
`KofJsRunner.run`), e o instinto diz "alguém publicou uma regressão".
Ninguém publicou: `kof-runtime/target/classes/.../KofJsRunner.class` era um
error-stub velho de um build interrompido anterior, enquanto as fontes
ATUAIS compilam limpas. Um `grep -rl FAILURE` nos relatórios parece um
incêndio; um `mvn -pl kof-runtime clean` e uma segunda corrida parece que
nada aconteceu. É a **mesma família de armadilha que o repo já conhece como
§165 / §257** (inlining `static final` mostrando valor velho) — esta entrada
é a face "estoura em runtime", que se disfarça de regressão EM MASSA em vez
de valor errado.

## Ruim

```
tip move código (commit de outra pessoa) → mvn test -o (incremental)
→ 297 erros em todo teste que toca JS
→ conclusão: "a fatia A da .18 regridiu o runtime" (ERRADO)
→ "conserto": reverter / culpar / abaixar as asserções para passar  ← proibido (Q5)
```

## Preferível

```
297 erros num tip que você não quebrou
→ (1) LEIA UMA stack trace antes de acreditar na contagem:
      grep -m1 -A8 '<<< ERROR' kof-compiler/target/surefire-reports/<Primeiro>.txt
      "Unresolved compilation problem" = STUB, não falha real
→ (2) reconstrua LIMPO o módulo implicado:
      mvn -o -pl kof-runtime clean compile
      strings kof-runtime/target/classes/<Suspeita>.class | grep -c "Unresolved compilation"  → tem que ser 0
→ (3) rode a suíte de novo. Se foi de 297 → 0 erros, a "regressão" era
      ambiental: registre a mordida da armadilha na mensagem do commit e siga.
→ (4) só se os erros SOBREVIVEREM ao rebuild limpo você tem vermelho real —
      aí vale Q0: raiz, dona, entrada no known-bugs.
```

## Porquê

- A regra "a suíte é o gate" presume que a suíte mede as FONTES. Com ECJ +
  build incremental, ela pode medir uma **mistura** de fontes e fantasmas —
  o número é real mas a causa não está em nenhum commit.
- "Qualquer falha que não seja uma guarda ambiental documentada é SUA"
  (AGENTS.md) continua valendo — mas **diagnosticar** de quem é vem antes de
  **agir** sobre isso. Uma leitura de erros-em-massa tem assinatura: centenas
  de ERROS (não falhas), todos passando pela mesma linha `Error:`, em
  módulo(s) que você não tocou.
- Culpar o código publicado de outra lane sem ler uma stack trace é o jeito
  mais rápido de envenenar a coordenação multi-agente (a .18 estava no meio
  do DB001 quando isso disparou em 16/09 — a conclusão "reverte aquela
  fatia" estava a um grep de distância).
- Relacionado neste repo: §165 (mesma família de armadilha, face de valor),
  §257 (mesma família, face `static final`), `weak-green-proof.md` (o erro
  espelhado: acreditar num verde). Esta entrada: **não acredite num vermelho
  tampouco — verifique se as classes são as que você acha que são.**
