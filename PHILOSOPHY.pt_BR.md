[English](PHILOSOPHY.md) | [Português](PHILOSOPHY.pt_BR.md)

# Manifesto da Filosofia Kof

## Koffie voor iedereen.

Software deveria começar com uma intenção humana e terminar em uma máquina fazendo exatamente aquilo que foi pedido.

Entre esses dois pontos existe complexidade.

Parte dela é inevitável.

Grande parte, porém, é criada pelas próprias ferramentas que usamos para construir software.

Kof nasce para questionar essa complexidade.

Não para fingir que sistemas complexos são simples.
Não para esconder o funcionamento da máquina.
Não para transformar programação em mágica.

Kof nasce para remover aquilo que não precisa estar no caminho.

**Menos complexidade acidental. Mais intenção.**

---

## I. A intenção vem primeiro

Programadores deveriam descrever o problema que estão resolvendo, não lutar contra a ferramenta para expressá-lo.

A linguagem deve aproximar o código da intenção.

Quando alguém lê um programa Kof, deve conseguir entender não apenas como ele funciona, mas principalmente **o que ele pretende fazer**.

Boilerplate não é profundidade.

Configuração não é arquitetura.

Cerimônia não é engenharia.

Abstração não é automaticamente qualidade.

Se uma parte do código existe apenas porque a ferramenta exige que ela exista, devemos questionar sua necessidade.

---

## II. Simplicidade não significa limitação

Kof segue o espírito do KISS:

**Keep It Simple.**

Mas simplicidade não significa remover capacidade.

Sistemas reais são complexos.

Redes são complexas.
Sistemas distribuídos são complexos.
Concorrência é complexa.
Hardware é complexo.
Memória é complexa.
Segurança é complexa.

Kof não pretende esconder essa realidade.

Pretende impedir que a linguagem adicione complexidade onde o problema não adicionou nenhuma.

**A complexidade deve existir porque o problema exige, não porque a ferramenta exige.**

---

## III. A máquina importa

Abstração não deve significar ignorância.

Kof pode permitir que alguém escreva software em alto nível sem pensar constantemente em registradores, ponteiros, syscalls ou detalhes de uma CPU.

Mas esses detalhes continuam existindo.

E devem continuar acessíveis quando forem importantes.

O programador deve poder subir na abstração sem perder a capacidade de descer até a máquina.

Queremos uma linguagem que seja confortável para construir uma aplicação web e suficientemente próxima do sistema para construir software de baixo nível.

**High-level não precisa significar alienado do hardware.**

---

## IV. Pequenas coisas, bem feitas

Kof segue o espírito do Unix:

**faça uma coisa e faça bem.**

Ferramentas devem possuir responsabilidades claras.

Componentes devem ser composáveis.

Sistemas devem ser construídos a partir de partes que possam existir independentemente.

Não queremos uma única abstração gigantesca que resolva tudo.

Queremos componentes que possam trabalhar juntos.

Um compilador deve compilar.

Um debugger deve depurar.

Um servidor deve servir.

Uma biblioteca deve resolver seu problema.

E quando várias ferramentas precisam trabalhar juntas, elas devem fazê-lo sem exigir que o desenvolvedor reconstrua o mundo para conectá-las.

**Composição é uma forma de simplicidade.**

---

## V. Não existe arquitetura sagrada

Kof não deve decidir pelo desenvolvedor se seu sistema precisa ser monolítico, distribuído, modular, orientado a serviços ou qualquer outra arquitetura.

Um sistema pequeno pode ser um monólito.

Um sistema grande pode ser dividido.

Uma aplicação pode possuir frontend e backend no mesmo projeto.

Outra pode separar tudo em serviços independentes.

A arquitetura deve responder ao problema.

Não à moda.

Kof fornece ferramentas para construir sistemas.

Não uma religião arquitetural.

---

## VI. A plataforma deve trabalhar para o programador

O computador é excelente em trabalho mecânico.

Devemos utilizá-lo para isso.

O programador não deveria precisar administrar manualmente cada detalhe que pode ser determinado com segurança pelo compilador, runtime ou ferramenta.

Imports triviais.
Boilerplate.
Wiring.
Configuração repetitiva.
Geração mecânica.
Detalhes administrativos.

Quando a máquina consegue resolver algo sem comprometer clareza, segurança ou controle, **deixe a máquina fazer o trabalho.**

Automação deve remover burocracia.

Não autonomia.

---

## VII. Poder sem cerimônia

Kof não busca ser poderosa adicionando milhares de conceitos.

Busca ser poderosa porque seus conceitos fundamentais conseguem compor.

Uma linguagem não precisa de uma solução diferente para cada problema quando possui fundamentos suficientemente bons.

Queremos expressividade sem verbosidade.

Flexibilidade sem caos.

Abstração sem aprisionamento.

Performance sem exigir que todo programa seja escrito como código de máquina.

**Poder não deveria exigir cerimônia.**

---

## VIII. A stdlib deve ser grande. O programa não.

Uma linguagem útil inevitavelmente acumula ferramentas.

Kof deve possuir uma biblioteca padrão capaz de lidar com aplicações reais: rede, dados, concorrência, sistemas, automação, segurança, interfaces e outras áreas que surgirem.

Mas uma stdlib grande não deve significar programas grandes.

O compilador deve conhecer o que está sendo utilizado e produzir somente aquilo que é necessário.

O desenvolvedor não deveria precisar administrar manualmente cada dependência interna.

**A plataforma pode ser enorme. O executável deve ser tão pequeno quanto o problema permitir.**

Isso vale desde um servidor até um microcontrolador.

---

## IX. Um ecossistema, diferentes máquinas

A intenção de um programa não deveria estar presa a uma única plataforma quando isso não for necessário.

JVM.

Native.

JavaScript.

WASM.

ARM.

RISC-V.

Outras plataformas que ainda nem existem.

Kof deve buscar preservar a semântica da linguagem enquanto adapta sua execução às características de cada ambiente.

Não fingimos que todas as máquinas são iguais.

Respeitamos suas diferenças.

Mas também não aceitamos que cada diferença de plataforma obrigue o desenvolvedor a reaprender a expressar a mesma intenção.

**Uma intenção. Diferentes formas de execução.**

---

## X. Interoperabilidade é liberdade

Nenhuma linguagem existe sozinha.

Kof deve conversar com o mundo existente.

Java.

C.

Sistemas operacionais.

Bibliotecas nativas.

JavaScript.

Outras linguagens.

Outras plataformas.

FFI e interoperabilidade não são concessões.

São liberdade.

Uma linguagem que exige abandonar tudo o que veio antes para ser utilizada está impondo seu ecossistema ao desenvolvedor.

Kof deve permitir que o desenvolvedor escolha quando começar algo novo e quando aproveitar aquilo que já existe.

---

## XI. Código deve ser nobre

Código nobre não é código sofisticado.

É código que possui uma razão para existir.

É código que expressa intenção.

É código que não cria abstrações apenas para parecer arquitetural.

É código que não esconde complexidade importante atrás de magia.

É código que pode ser lido.

Questionado.

Testado.

Otimizado.

Depurado.

Substituído.

Código nobre não é o menor código possível.

É o **menor código necessário para representar corretamente o problema.**

---

## XII. Humanos primeiro

Kof é uma ferramenta para pessoas.

A linguagem deve ser legível por humanos antes de qualquer outra coisa.

Documentação deve ser compreensível.

Erros devem explicar o problema.

Ferramentas devem ajudar, não atrapalhar.

A linguagem deve ser consistente o suficiente para que uma pessoa consiga formar um modelo mental confiável do sistema.

E justamente por ser bem estruturado para humanos, Kof também pode ser compreendido por ferramentas automatizadas.

LLMs, agentes, IDEs, compiladores, analisadores e outras ferramentas podem trabalhar melhor com uma linguagem cuja intenção seja explícita.

**LLM-friendly deve ser consequência de ser human-friendly.**

Nunca o contrário.

---

## XIII. IA é ferramenta, não autoridade

Kof pode ser desenvolvido com inteligência artificial.

Kof pode possuir agentes.

Kof pode ser utilizado por agentes.

Mas nenhuma IA possui autoridade sobre a filosofia da linguagem.

IA pode implementar.

Pode testar.

Pode revisar.

Pode documentar.

Pode sugerir.

Pode acelerar.

A intenção, a arquitetura e os princípios continuam sendo responsabilidade humana.

**Automatizar a implementação não significa terceirizar o pensamento.**

---

## XIV. Não esconda o importante

Kof deve esconder trabalho mecânico.

Nunca deve esconder decisões importantes.

Se uma operação possui custo relevante, comportamento relevante ou consequência relevante, o desenvolvedor deve poder entendê-la.

Se algo pode causar uma falha séria, deve ser observável.

Se algo afeta performance, deve poder ser analisado.

Se algo afeta segurança, deve poder ser auditado.

Abstração boa remove ruído.

Abstração ruim remove entendimento.

**Kof deve esconder detalhes. Nunca esconder a verdade.**

---

## XV. Evolução sem dogma

Kof não precisa acertar tudo na primeira versão.

Uma linguagem viva precisa poder mudar.

Precisamos experimentar.

Medir.

Quebrar.

Corrigir.

Remover.

Simplificar.

Uma decisão antiga não se torna correta apenas porque foi tomada anteriormente.

Código legado não é uma justificativa para perpetuar complexidade.

Compatibilidade é importante.

Estabilidade é importante.

Mas quando uma decisão fundamentalmente ruim impede a evolução da linguagem, devemos ter coragem de corrigi-la.

**O passado informa o projeto. Não governa o projeto.**

---

## XVI. A linguagem pertence a quem a utiliza

Kof não deve ser construída em torno de uma elite de especialistas.

Deve ser suficientemente poderosa para especialistas e suficientemente coerente para quem está começando.

Não queremos uma linguagem que infantilize iniciantes.

Também não queremos uma linguagem que exija sofrimento como prova de competência.

Conhecimento técnico deve ser conquistado porque o problema é difícil.

Não porque a ferramenta decidiu criar obstáculos artificiais.

**Programar deve ser difícil quando o problema é difícil. Não quando a linguagem é ruim.**

---

## XVII. Faça software real

Kof não existe para ganhar uma guerra de benchmarks de linguagens.

Não existe para substituir todas as linguagens.

Não existe para criar mais uma sintaxe bonita para os mesmos problemas.

Existe para construir software.

Aplicações.

Serviços.

Ferramentas.

Sistemas.

Interfaces.

Infraestrutura.

Software embarcado.

Experimentos.

Coisas pequenas.

Coisas enormes.

Coisas que ainda não sabemos que precisaremos construir.

A melhor prova de uma linguagem não é quantas features ela possui.

É **o que as pessoas conseguem construir com ela.**

---

# O princípio central

Quando houver dúvida sobre uma decisão no Kof, devemos voltar à pergunta fundamental:

> **Isso reduz a distância entre a intenção do programador e o software executado pela máquina, ou aumenta essa distância?**

Se reduz, provavelmente estamos no caminho certo.

Se aumenta, precisamos de uma boa razão.

Kof não busca eliminar a complexidade do software.

Busca colocar cada complexidade no lugar onde ela realmente pertence.

No problema, quando ela é necessária.

Na ferramenta, quando ela pode ser automatizada.

E nunca no código apenas porque ninguém parou para questioná-la.

---

# Kof

KISS nos lembra de manter as coisas simples.

Unix nos lembra de construir sistemas através de partes pequenas, claras e composáveis.

A engenharia nos lembra de respeitar a máquina.

A experiência nos lembra que abstrações possuem custos.

E Kof adiciona uma pergunta:

> **Qual era a intenção?**

A partir dela, construímos uma linguagem.

Uma plataforma.

Um ecossistema.

Uma comunidade.

Não para escrever mais código.

Mas para gastar menos pensamento lutando contra o código que não deveria existir.

**Menos complexidade acidental.**
**Mais intenção.**
**Mais controle.**
**Mais software.**

**Koffie voor iedereen.**
