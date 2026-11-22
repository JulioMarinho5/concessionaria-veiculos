# Concessionaria de Veiculos - Parte 5: OLAP e Mineracao de Dados

Integrante 5 - Analista de BI e Mineracao.

Esta parte analisa o Data Warehouse carregado pelo ETL (Parte 4): as consultas OLAP
(requisito 4.3.3), a comparacao de tempo entre OLTP, estrela e floco de neve, e a mineracao
de dados (requisito 4.3.4). A analise previa feita sobre o OLTP esta em
[Parte 5 - Analise de BI do OLTP](Parte%205%20-%20Analise%20de%20BI%20do%20OLTP.md).

## Arquivos

| Arquivo | Conteudo |
|---|---|
| `database/olap/consultas_olap.sql` | 10 consultas OLAP no `dw_estrela` (2 por operacao) |
| `database/olap/resultado_olap.txt` | Saida das 10 consultas |
| `database/olap/comparacao_tempo.sql` | Mesma consulta no OLTP, no estrela e no floco, com medicao de tempo e planos |
| `database/olap/resultado_comparacao_tempo.txt` | Saida da comparacao |
| `mineracao/exportar_base.sql` | Exporta do DW as bases da mineracao (CSV) |
| `mineracao/base_cestas.csv`, `mineracao/base_clientes.csv` | Bases exportadas |
| `mineracao/mineracao.py` | Apriori e k-means em Python (pandas, scikit-learn) |
| `mineracao/resultado_mineracao.txt` | Saida da mineracao |
| `mineracao/regras_associacao.csv`, `mineracao/clientes_grupos.csv` | Todas as regras e o grupo de cada cliente |
| `mineracao/grafico_cotovelo.png`, `mineracao/grafico_grupos.png` | Escolha do k e grupos de clientes |

Como rodar, a partir da raiz do projeto, depois de criar o OLTP, o DW e executar o ETL
(ordem em `database/etl/05_executar_etl.sql`):

```
psql -U postgres -d concessionaria -f database/olap/consultas_olap.sql
psql -U postgres -d concessionaria -f database/olap/comparacao_tempo.sql
psql -U postgres -d concessionaria -f mineracao/exportar_base.sql
cd mineracao
python mineracao.py
```

Os resultados abaixo foram gerados com o banco recriado do zero e uma unica execucao do ETL
(5.015 linhas na fato, valor liquido total de R$ 116.959.476,84, igual ao OLTP).

## 1. Consultas OLAP

Todas as consultas leem o modelo estrela. Medida principal: `valor_liquido`. As linhas de
subtotal do ROLLUP/CUBE sao identificadas com a funcao `GROUPING`.

| # | Operacao | O que faz | O que o resultado mostra |
|---|---|---|---|
| RU1 | Roll-up | Faturamento mes -> trimestre -> ano -> total (`ROLLUP`) | O 4o trimestre e o mais forte em 2024 (204 vendas, R$ 12,95 mi) e em 2025 (210 vendas). Total por ano: R$ 38,60 mi (2024), R$ 41,16 mi (2025) e R$ 37,19 mi em 2026, que so vai ate outubro. |
| RU2 | Roll-up | Veiculos: marca -> categoria -> total (`ROLLUP`) | SUV e a categoria que mais fatura (R$ 39,26 mi, 262 unidades). Hatch vende quase o mesmo numero (252), mas fatura R$ 22,91 mi. A Fiat lidera em unidades no Hatch, na Picape e no SUV. |
| DD1 | Drill-down | Desce da categoria SUV (RU2) para cada modelo | Nenhum modelo passa de 11,2% do faturamento de SUV (Fiat Fastback lidera). O Jeep Commander tem o maior ticket medio (R$ 234 mil). |
| DD2 | Drill-down | Desce de cada loja para cada vendedor, com ranking e participacao | Em todas as lojas o melhor vendedor faz de 34% a 44% do faturamento da loja. Os consultores de servico fazem muitas vendas (81 a 134 cada), mas menos de 1% do faturamento. |
| SL1 | Slice | Fatia forma de pagamento = Financiamento, por categoria | Hatch (126) e SUV (125) sao os mais financiados em unidades, mas o SUV financiado fatura R$ 18,87 mi contra R$ 11,43 mi do Hatch. |
| SL2 | Slice | Fatia ano = 2025, desconto por loja | Iguatemi fatura mais (R$ 9,18 mi). A Paralela da o maior desconto: 3,15% do bruto, contra 2,19% a 2,47% nas outras. |
| DC1 | Dice | SUV e Picape x lojas de Salvador x 2025 e 2026 (jan-out) | SUV cresceu nas duas lojas de Salvador (Iguatemi 14 -> 18, Paralela 10 -> 19 unidades); Picape ficou estavel. |
| DC2 | Dice | Servicos x lojas de Feira e Vitoria da Conquista x 2o semestre | Emplacamento lidera (acompanha a venda de carro). Em Feira o 2o servico e a garantia estendida (R$ 81,5 mil); em Conquista, a pelicula (R$ 52,1 mil). |
| PV1 | Pivot | Lojas nas linhas x anos nas colunas (`FILTER` + `ROLLUP`) | Feira de Santana ja faturou em 2026 (10 meses, R$ 8,75 mi) mais que em 2025 inteiro (R$ 7,40 mi). Vitoria da Conquista e Lauro de Freitas caem em 2026, mesmo descontando os 2 meses que faltam. |
| PV2 | Pivot | Categoria x forma de pagamento (`CUBE`, formas giradas para colunas) | Financiamento e a forma mais usada em todas as categorias (424 de 893 veiculos, 47,5%). Na Minivan, financiamento e consorcio empatam (9 e 9). |

## 2. Comparacao de tempo: OLTP x estrela x floco de neve

Pergunta: faturamento liquido de veiculos por ano, trimestre, categoria e cidade da loja.
O script confere antes que as tres versoes devolvem as mesmas 210 linhas (0 diferencas) e
depois executa cada uma 200 vezes, apos 5 execucoes de aquecimento. Antes, o `ANALYZE`
atualiza as estatisticas das tabelas, para o otimizador escolher bons planos nos tres modelos.

| Modelo | Tabelas | Juncoes | Mediana | Minimo | Maximo |
|---|---|---|---|---|---|
| OLTP | venda, item_venda, veiculo, modelo, loja | 4 | 2,263 ms | 2,085 ms | 4,957 ms |
| Estrela | fato, dim_tempo, dim_produto, dim_loja | 3 | 1,363 ms | 1,246 ms | 2,136 ms |
| Floco de neve | fato, dim_tempo, dim_produto, dim_modelo, dim_loja, dim_cidade | 5 | 1,595 ms | 1,468 ms | 2,273 ms |

- **Estrela e o mais rapido:** 40% menos tempo que o OLTP. Cada dimensao e uma unica juncao
  com a fato, e ano e trimestre ja estao prontos na `dim_tempo`. No OLTP e preciso juntar
  `item_venda` com `venda` e calcular `EXTRACT` em cada linha.
- **Floco de neve fica no meio:** 17% mais lento que o estrela, por causa das 2 juncoes a mais
  (`dim_modelo` para a categoria e `dim_cidade` para a cidade), mas ainda 30% mais rapido que o OLTP.
- **Cuidado com a escala:** com 5 mil linhas a diferenca e de milissegundos. Os tempos mudam de
  uma maquina para outra e de uma execucao para outra; o que vale e a comparacao entre os tres
  na mesma maquina. Rodando o script varias vezes, o estrela foi sempre o mais rapido e o floco
  ficou quase sempre entre os dois. Com volumes
  maiores, a vantagem do modelo dimensional tende a crescer.

## 3. Mineracao de dados

Foram aplicadas duas tecnicas do enunciado sobre bases exportadas do DW.

### 3.1 Regras de associacao (Apriori)

- **Preparacao:** cada venda vira uma "cesta" com os seus itens (2.000 cestas, 20 itens
  distintos). Servico entra pelo nome; veiculo entra pela categoria ("Veiculo SUV"), porque o
  modelo exato quase nunca se repete.
- **Parametros:** suporte minimo de 2%, confianca minima de 40%, conjuntos de ate 3 itens.
- **Resultado:** 98 conjuntos frequentes e 83 regras. 27 regras sao triviais (ligam veiculo e
  emplacamento, que so existe na venda de carro) e foram separadas; ficaram 56 regras uteis.

Regras uteis de destaque:

| Se comprou | Entao compra | Suporte | Confianca | Lift |
|---|---|---|---|---|
| Troca de pneus | Alinhamento e balanceamento | 7,0% | 85,9% | 5,58 |
| Higienizacao interna | Polimento e cristalizacao | 4,1% | 59,9% | 3,59 |
| Polimento + Veiculo Picape | Pelicula | 2,4% | 92,3% | 3,53 |
| Polimento + Veiculo SUV | Pelicula | 3,7% | 91,4% | 3,49 |
| Instalacao de multimidia | Veiculo Hatch | 3,4% | 43,6% | 3,46 |
| Revisao 20.000 km | Troca de oleo e filtro | 5,95% | 72,1% | 2,95 |
| Veiculo Picape | Pelicula | 5,65% | 70,2% | 2,68 |

**Exemplo de leitura (questao 13):** a regra "Troca de pneus -> Alinhamento" aparece em 140
das 2.000 vendas, entao o **suporte** e 7,0%. Das 163 vendas com troca de pneus, 140 tambem
tem alinhamento: a **confianca** e 85,9%. Sem saber nada da venda, a chance de ter alinhamento
e de 15,4% (308 de 2.000); quem troca pneus tem 85,9%, ou seja, 5,58 vezes mais: esse e o
**lift**. Lift maior que 1 indica que os itens andam juntos mais do que o acaso explicaria.

### 3.2 Agrupamento de clientes (k-means)

- **Preparacao:** 184 clientes com pelo menos uma compra, descritos por recencia (dias desde a
  ultima compra ate 31/10/2026), frequencia (numero de vendas) e valor (faturamento liquido).
  Frequencia e valor sao muito assimetricos, entao foi aplicado log(1 + x) e depois todas as
  variaveis foram padronizadas (media 0, desvio 1); sem isso o valor em reais dominaria a distancia.
- **Parametros:** k testado de 2 a 8, 10 inicializacoes, semente 42. O k = 4 teve a maior
  silhueta (0,486) e coincide com o "cotovelo" da inercia (`grafico_cotovelo.png`).
- **Resultado:**

| Grupo | Perfil | Clientes | Recencia media | Compras em media | Valor medio | % do faturamento |
|---|---|---|---|---|---|---|
| G1 | Fieis de alto valor | 68 (37%) | 39 dias | 23,0 | R$ 1.325.758 | 77,1% |
| G2 | Compradores de veiculo ocasionais | 85 (46%) | 155 dias | 4,4 | R$ 297.895 | 21,6% |
| G3 | Inativos | 15 (8%) | 606 dias | 2,0 | R$ 96.302 | 1,2% |
| G4 | So pos-venda (nunca compraram carro) | 16 (9%) | 156 dias | 2,1 | R$ 2.647 | 0,04% |

O G3 se separa pela recencia (quase 20 meses sem comprar), o que aparece no painel
"Recencia x valor" do `grafico_grupos.png`.

### 3.3 Conclusoes de negocio

1. **Pacote pneus + alinhamento.** 86% de quem troca pneus faz alinhamento na mesma visita
   (lift 5,58). Vender os dois como pacote, com preco unico, e oferecer alinhamento a quem
   ainda nao fez.
2. **Kit protecao na entrega de SUV e Picape.** 67% a 70% dos SUVs e picapes saem com pelicula,
   e com polimento a taxa passa de 91%. Um kit pelicula + polimento oferecido na negociacao
   aproveita esse comportamento.
3. **Revisao com troca de oleo incluida.** De 61% a 72% das revisoes vem com troca de oleo.
   Incluir a troca de oleo no preco da revisao simplifica a venda e aumenta o ticket do pos-venda.
4. **Multimidia para o Hatch.** Quem instala multimidia tem 3,46 vezes mais chance de estar
   comprando um Hatch (modelos de entrada, muitas vezes sem central de fabrica): oferecer a
   instalacao ja na venda do Hatch.
5. **Fidelizar G1, reativar G3, converter G4.** 37% dos clientes (G1) fazem 77% do faturamento:
   prioridade para programa de relacionamento. Os 15 clientes inativos (G3) pedem uma campanha
   de reativacao. Os 16 clientes do G4 ja frequentam a oficina mas nunca compraram carro: sao
   candidatos naturais a uma oferta de veiculo.

## Uso de IA

As consultas, os scripts de mineracao e este texto foram preparados com apoio do Claude
(Anthropic), conforme pede o enunciado. Tudo foi executado e conferido sobre o banco da equipe
recriado do zero.
