# Concessionaria de Veiculos - Etapa 1 (OLTP com JPA 2.1 e Hibernate 5.2.10)

Trabalho em Equipe - Banco de Dados II (2a Unidade). Esta parte cobre o requisito 4.2 do enunciado:
entidades JPA, `persistence.xml`, DAOs, consultas JPQL e Criteria, carga de dados e interface de console.

## Ambiente

| Componente | Versao |
|---|---|
| API de persistencia | JPA 2.1 (`javax.persistence`) |
| Implementacao ORM | Hibernate ORM 5.2.10.Final |
| JDK | 8 |
| IDE | Eclipse Neon (Java EE) |
| Banco | PostgreSQL (esquema `public` para o OLTP; o DW fica no esquema `dw`) |
| Build | Maven (pom do projeto com Spring Boot 2.1.18; as classes desta parte nao dependem do Spring) |

## Como rodar

1. No PostgreSQL, crie o banco: `CREATE DATABASE concessionaria;`
2. Se o seu usuario/senha nao for `postgres/postgres`, ajuste em `src/main/resources/META-INF/persistence.xml`.
3. No Eclipse: **File > Import > Maven > Existing Maven Projects** e selecione esta pasta.
   Confira se o projeto usa o JDK 8 (botao direito > Properties > Java Build Path).
4. Rode `carga/CargaDados.java` (Run As > Java Application). As tabelas sao criadas pelo
   Hibernate (`hbm2ddl.auto=update`) e preenchidas. No final ele mostra quantas linhas tem cada tabela.
5. Rode `app/Aplicacao.java` para usar o menu, ou `app/Relatorios.java` para ver as 13 consultas.

A carga usa semente fixa: todos da equipe geram exatamente os mesmos dados. Rodar de novo apaga e recria tudo.

## Estrutura

```
src/main/java/com/github/projetobd2/concessionaria_veiculos/
  modelo/   entidades (Pessoa, Cliente, Funcionario, DocumentoCliente, Loja, Marca, Modelo,
            Veiculo, Servico, Venda, ItemVenda), Endereco (@Embeddable), FormaPagamento (enum)
  dao/      JPAUtil, GenericDAO<T, ID>, ClienteDAO, FuncionarioDAO, VeiculoDAO, VendaDAO
  carga/    CargaDados (200 clientes, 5 lojas, ~1.000 veiculos, 15 servicos, 2.000 vendas 2024-2026)
  app/      Aplicacao (menu de console), Relatorios (executa as consultas)
src/main/resources/META-INF/persistence.xml
```

## Onde esta cada requisito do item 4.2

| Requisito | Onde |
|---|---|
| 8+ entidades | 11 entidades em `modelo/` |
| `@OneToOne` | `Cliente.documento` <-> `DocumentoCliente.cliente` (FK `cliente_id` UNIQUE) |
| `@OneToMany`/`@ManyToOne` bidirecional | `Venda.itens` <-> `ItemVenda.venda`; `Loja.funcionarios` <-> `Funcionario.loja` |
| `@ManyToMany` via entidade intermediaria | `ItemVenda` liga `Venda` a `Veiculo`/`Servico` com quantidade, preco e desconto |
| Heranca | `Pessoa` -> `Cliente` / `Funcionario`, `InheritanceType.JOINED` |
| `@Embeddable` | `Endereco` (em `Pessoa` e `Loja`) |
| `@Enumerated` | `Venda.formaPagamento` (`EnumType.STRING`) |
| Datas | `java.time.LocalDate` / `LocalDateTime` (suporte nativo do Hibernate 5.2) |
| `GenericDAO<T, ID>` | `dao/GenericDAO.java` (CRUD + controle de transacao com rollback) |
| 10 JPQL, 2+ `@NamedQuery` | tabela abaixo |
| 3 Criteria, 1 com filtros dinamicos | tabela abaixo |
| Carga de dados | `carga/CargaDados.java` |
| Interface com CRUD de 2 entidades | `app/Aplicacao.java` (Cliente e Veiculo, alem de registrar venda) |

## Consultas

| # | O que faz | Recurso exigido | Pergunta de negocio | Onde |
|---|---|---|---|---|
| JPQL 1 | Faturamento por ano e mes | `@NamedQuery`, GROUP BY | 1 | `Venda` / `VendaDAO` |
| JPQL 2 | Top 10 modelos mais vendidos | juncao em cadeia, `setMaxResults` | 2 | `VendaDAO` |
| JPQL 3 | Ranking de vendedores acima de um valor | `@NamedQuery`, HAVING, parametro nomeado | 3 | `Venda` / `VendaDAO` |
| JPQL 4 | Faturamento e desconto por loja | juncao, agregacao | 4 | `VendaDAO` |
| JPQL 5 | Uso das formas de pagamento | GROUP BY em enum | 5 | `VendaDAO` |
| JPQL 6 | Veiculos x servicos no faturamento | SUM com CASE | 6 | `VendaDAO` |
| JPQL 7 | Clientes por cidade | caminho pelo `@Embeddable` | 7 | `ClienteDAO` |
| JPQL 8 | Venda completa com itens | JOIN FETCH | - | `VendaDAO` |
| JPQL 9 | Veiculos em estoque | subconsulta (NOT EXISTS) | - | `VeiculoDAO` |
| JPQL 10 | Clientes em ordem alfabetica | paginacao (`setFirstResult`/`setMaxResults`) | - | `ClienteDAO` |
| Criteria 1 | Pesquisa de veiculos (marca, categoria, ano, preco, estoque) | filtros dinamicos | - | `VeiculoDAO` |
| Criteria 2 | Faturamento por loja em um periodo | multiselect, groupBy | 4 | `VendaDAO` |
| Criteria 3 | Clientes de um estado cadastrados desde uma data | where, orderBy | 7 | `ClienteDAO` |

Extra: `@NamedQuery` `Cliente.porCpf`. A pergunta 8 (perfis de cliente e o que compram junto) e
respondida pela mineracao no DW; a carga ja gera vendas com varios itens para isso.

## Justificativa de fetch e cascade (para o relatorio)

- **`@ManyToOne` sempre LAZY** (Venda -> cliente/vendedor/loja, ItemVenda -> veiculo/servico,
  Veiculo -> modelo, Modelo -> marca): o padrao da JPA para `@ManyToOne` e EAGER, o que carregaria
  objetos desnecessarios em toda consulta. Quando precisa, a consulta usa JOIN FETCH (JPQL 8 e 9,
  Criteria 1), que evita o problema N+1.
- **`Venda.itens`: cascade ALL + orphanRemoval**: o item so existe dentro da venda (composicao no UML).
- **`Cliente.documento`: cascade ALL + orphanRemoval**: o documento so existe junto com o cliente.
- **`Loja.funcionarios` e `Marca.modelos`: cascade PERSIST**: facilita o cadastro em conjunto, mas
  apagar uma loja ou marca nao apaga o historico.
- **`Cliente.vendas` sem cascade**: venda tem ciclo de vida proprio.
- **Heranca JOINED**: igual ao modelo relacional (cliente.id e funcionario.id sao PK e FK para
  pessoa.id). Normalizado e sem colunas nulas; o custo e um JOIN a mais.
- Observacao: o lado inverso de um `@OneToOne` (`Cliente.documento`) e carregado sempre pelo
  Hibernate, mesmo marcado LAZY, porque ele precisa saber se o documento existe. Ao listar 15 clientes,
  saem 15 SELECTs extras em `documento_cliente`: e um exemplo real de N+1 para a questao 6.

## Para o ETL

- `pessoa.data_atualizacao` e `venda.data_atualizacao` sao atualizadas automaticamente
  (`@PrePersist`/`@PreUpdate`) e servem para a carga incremental.
- Menu > Clientes > Alterar endereco chama `Cliente.alterarCidade()`: e a mudanca usada para
  demonstrar SCD tipo 2.
- Menu > Registrar venda grava uma venda nova pela aplicacao JPA (demonstracao final: a venda
  aparece no DW depois de rodar o ETL de novo).

## Observacoes

- O `spring-boot-starter-data-jpa` exclui a API JPA 2.2 para ficar so a JPA 2.1 do Hibernate 5.2.10.
- O driver do PostgreSQL (42.2.x, definido pelo Spring Boot) autentica no PostgreSQL 14 ou mais novo.
- Precos e nomes sao ficticios, gerados pela carga.
