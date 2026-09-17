# Manifesto do Cornerstone (SiteTune) / Cornerstone Manifesto

*Read this document in English below: [English Version](#english-version)*

> Portado do `MANIFESTO.md` do repositório `portfolio` (Portfólio Balancer). Os princípios são os mesmos da família de produtos SiteTune; a seção "O papel do kernel" traduz esses princípios para uma biblioteca sem tela, sem persistência e sem motor fiscal.

---

## Português

Este documento define o propósito e os princípios do produto. Ele deve guiar toda decisão de implementação, priorização e tom de comunicação — no kernel, isso inclui o desenho da API pública, as mensagens de exceção e a documentação.

### Público-alvo

Os usuários do Cornerstone da SiteTune são **Indivíduos**, **Casais** ou **Famílias**.

### Visão de dinheiro

O Cornerstone da SiteTune tem uma visão totalmente cristã/católica do dinheiro. Dinheiro não é o fim, mas o meio. Não somos escravos do dinheiro, somos os mestres, e como mestres usamos o dinheiro de maneira sábia e responsável para fazer o bem para a comunidade cristã/católica.

### Objetivos do produto

1. **Ajudar o usuário a organizar sua vida financeira sem causar prejuízos financeiros.**
2. **Ajudar o usuário a reduzir ao máximo os impostos pagos ao governo**, seja por meio de:
   - **a.** Criptomoedas, como o Bitcoin. ("A passagem bíblica gravada no bloco 666.666 do Bitcoin é o versículo de Romanos 12:21: 'Não te deixes vencer pelo mal, mas vence o mal com o bem'.")
   - **b.** Elisão fiscal.
   - **c.** Otimização fiscal.
3. **Independência de ferramentas e APIs pagas**: O Cornerstone não deve, de maneira alguma, depender exclusivamente de ferramentas, produtos e/ou APIs pagas.

### O papel do kernel

O `cornerstone-kernel` é a fundação sobre a qual o app, o servidor e a futura camada de persistência são construídos. Cada objetivo acima tem uma tradução concreta aqui:

1. **Sem prejuízo financeiro** → o livro nunca mente. Aritmética inteira exata, lançamentos válidos por construção, e **recusa** (exceção específica) em vez de aceitar ou ajustar silenciosamente qualquer incompatibilidade. Um centavo sumido num saldo é prejuízo para uma família.
2. **Redução lícita de impostos** → o kernel não calcula imposto, mas **não pode perder a informação** de que a elisão e a otimização fiscal dependem: custo de aquisição, quantidade por ativo, bruto vs. retido vs. líquido, taxa de câmbio exata aplicada, posições em cripto como qualquer outro ativo.
3. **Independência** → zero dependências de compilação além do JDK, build reproduzível com ferramentas livres (Maven Wrapper, JUnit, JaCoCo), nenhum serviço pago no caminho.

O público leigo não usa o kernel diretamente, mas sente cada decisão dele: uma API que permite estado inválido vira, lá na tela, um saldo errado que a família não sabe explicar.

### Como aplicar

Toda nova funcionalidade, mudança de API ou decisão de priorização deve ser avaliada à luz destes princípios: ela ajuda o usuário (Indivíduo, Casal ou Família) a organizar melhor sua vida financeira sem risco indevido, preserva o que é necessário para reduzir a carga tributária de forma lícita, e mantém o kernel independente de ferramentas/APIs pagas? Se não, reconsidere o escopo antes de implementar.

---

<a name="english-version"></a>
## English Version

This document defines the purpose and principles of the product. It must guide all decisions regarding implementation, prioritization, and tone of communication — in the kernel, that includes the public API design, exception messages, and documentation.

### Target Audience

The users of SiteTune's Cornerstone are **Individuals**, **Couples**, or **Families**.

### View on Money

SiteTune's Cornerstone holds a Christian/Catholic view of money. Money is not the end, but the means. We are not slaves to money; we are its masters, and as masters, we use money wisely and responsibly to do good for the Christian/Catholic community.

### Product Objectives

1. **Help the user organize their financial life without causing financial losses.**
2. **Help the user minimize taxes paid to the government as much as possible**, through:
   - **a.** Cryptocurrencies, such as Bitcoin. ("The biblical passage recorded in Bitcoin block 666,666 is Romans 12:21: 'Do not be overcome by evil, but overcome evil with good'.")
   - **b.** Tax avoidance.
   - **c.** Tax optimization.
3. **Independence from paid tools and APIs**: Cornerstone must not, under any circumstances, rely exclusively on paid tools, products, and/or APIs.

### The Kernel's Role

`cornerstone-kernel` is the foundation the app, the server, and the future persistence layer are built on. Each objective above has a concrete translation here:

1. **No financial losses** → the book never lies. Exact integer arithmetic, entries valid by construction, and **refusal** (a specific exception) instead of silently accepting or adjusting any mismatch. A missing cent in a balance is a loss for a family.
2. **Lawful tax reduction** → the kernel computes no tax, but it **must not lose the information** tax avoidance and optimization depend on: acquisition cost, per-asset quantity, gross vs. withheld vs. net, the exact exchange rate applied, crypto positions like any other asset.
3. **Independence** → zero compile-time dependencies beyond the JDK, a reproducible build with free tooling (Maven Wrapper, JUnit, JaCoCo), no paid service in the path.

Lay users never touch the kernel directly, but they feel every decision in it: an API that allows an invalid state becomes, on screen, a wrong balance the family cannot explain.

### How to Apply

Every new feature, API change, or prioritization decision must be evaluated in light of these principles: does it help the user (Individual, Couple, or Family) better organize their financial life without undue risk, preserve what is needed to reduce tax burden lawfully, and keep the kernel independent of paid tools/APIs? If not, reconsider the scope before implementing.
