# Concessionaria de Veiculos - Parte 5: Analise de BI sobre o OLTP

Integrante 5 - Analista de BI e Mineracao.

Antes de montar o DW, esta parte confere se os dados da carga respondem as
[Perguntas de Negocio](Perguntas%20de%20Neg%C3%B3cio.txt) e se tem padroes suficientes para
o OLAP e a mineracao da Etapa 2. Tambem deixa pronta a consulta de referencia para
comparar o tempo OLTP x estrela x floco de neve (requisito 4.3.3).

## Arquivos

| Arquivo | Conteudo |
|---|---|
| `database/bi/analise_bi_oltp.sql` | Perfil dos atributos, perguntas 1 a 8 em SQL, previa da mineracao e consulta de referencia |
| `database/bi/resultado_analise_bi_oltp.txt` | Saida completa do script sobre os dados de `database/oltp/carga_dados.sql` |

Como rodar, a partir da raiz do projeto e nesta ordem:

```
psql -U postgres -d concessionaria -f database/oltp/schema-OLTP.sql
psql -U postgres -d concessionaria -f database/oltp/carga_dados.sql
psql -U postgres -d concessionaria -f database/bi/analise_bi_oltp.sql
```

O script de analise so faz SELECT.

## O que os dados mostram

Numeros tirados de `resultado_analise_bi_oltp.txt` (2.000 vendas, jan/2024 a out/2026).

1. **Crescimento ano a ano.** 2026 so vai ate outubro, entao o total anual engana (parece
   que 2026 caiu). Comparando so janeiro a outubro: 509 -> 560 -> 644 vendas (+10,0% e +15,0%)
   e R$ 29,97 mi -> R$ 33,75 mi -> R$ 37,19 mi de faturamento (+12,6% e +10,2%).
2. **Sazonalidade.** Em 2024-2025, dezembro teve em media 81,5 vendas, contra 39,5 em fevereiro
   e 56,5 na media dos meses: dezembro fica 44% acima da media.
3. **Servicos.** Sao 82% dos itens vendidos (4.122 de 5.015) e 55% das vendas (1.107 de 2.000
   sao so de servicos), mas so 3,5% do faturamento (3,48% -> 3,53% -> 3,61%, subindo devagar).
4. **Forma de pagamento.** Venda de veiculo e financiamento em 47% dos casos (424 de 893).
   Venda so de servico e cartao em 62% (691 de 1.107).
5. **Regionalidade.** De 53% a 81% das vendas de cada loja sao de clientes da propria cidade.
   As lojas do interior atraem as cidades vizinhas: Vitoria da Conquista recebe Itabuna (21%)
   e Ilheus (17%); Feira de Santana recebe Santo Antonio de Jesus (21%) e Alagoinhas (11%);
   Lauro de Freitas recebe Camacari (23%).
6. **Desconto.** A loja de Lauro de Freitas da o maior desconto (2,71% do bruto, contra 2,25%
   a 2,42% nas outras) e e a que menos fatura.
7. **Clientes concentrados.** Os 10% maiores clientes (19) fazem 41,5% do faturamento.
   A frequencia tem mediana de 5 compras, Q3 de 15 e maximo de 64; 16 clientes cadastrados
   nunca compraram. Ha grupos bem diferentes, o que favorece o k-means com R, F e V.
8. **Compra conjunta (previa do Apriori, suporte >= 2% e confianca >= 40%).**
   - Troca de pneus -> alinhamento: confianca 86%, lift 5,58 (a regra mais forte).
   - Higienizacao -> polimento: confianca 60%, lift 3,59.
   - Revisao (10, 20 ou 30 mil km) -> troca de oleo: confianca de 61% a 72%, lift de 2,51 a 2,95.
   - Picape ou SUV -> pelicula: confianca de 67% a 70%, lift ~2,6.
   - As regras com "Emplacamento" sao obvias (so existe emplacamento em venda de veiculo).
     Na Etapa 2 elas devem ser filtradas ou comentadas como regra trivial.

## Pontos de atencao para a equipe

- **Dados limpos demais para o ETL.** Hoje ha 0 nulos e 0 textos fora do padrao (parte 1.2
  do script). O enunciado pede tratamento de nulos e padronizacao de textos no ETL, entao
  precisamos de alguns casos para tratar. Opcoes: cadastrar pela aplicacao alguns clientes com
  cidade em minusculas, espacos ou telefone vazio, ou usar o caso que ja existe: na dimensao
  produto, marca, modelo e categoria ficam nulos quando o item e um servico.
- **SCD tipo 2.** So aparece se algum cliente mudar de cidade depois da primeira carga do DW.
  A opcao "Clientes > Alterar" da `Aplicacao` ja faz isso (`Cliente.alterarCidade`).
- **2026 incompleto.** Toda comparacao entre anos no OLAP deve usar o mesmo periodo (jan-out).
- **Granularidade sugerida para a fato:** um item de venda (uma linha de `item_venda`), como ja
  indicado em `ItemVenda`. A dimensao produto precisa cobrir veiculo e servico (atributo
  `tipo_item`). Medidas: quantidade, valor bruto, desconto, valor liquido.

## Plano para a Etapa 2

### OLAP: 2 consultas por operacao

| Operacao | Consulta 1 | Consulta 2 |
|---|---|---|
| Roll-up | Faturamento mes -> trimestre -> ano (`GROUP BY ROLLUP`) - pergunta 1 | Unidades modelo -> marca -> total (`ROLLUP`) - pergunta 2 |
| Drill-down | Faturamento de 2025 por trimestre, depois por mes | Faturamento por loja, depois por vendedor - pergunta 3 |
| Slice | So `FINANCIAMENTO`: faturamento por categoria de veiculo | So o ano de 2025: faturamento e desconto por loja - pergunta 4 |
| Dice | SUV e Picape, lojas de Salvador, 2025 e 2026 | Servicos, 4o trimestre, lojas de Feira e Vitoria da Conquista |
| Pivot | Lojas nas linhas x anos nas colunas (`SUM ... FILTER`) | Categoria x forma de pagamento (`GROUP BY CUBE`) - pergunta 5 |

### Comparacao de tempo

A consulta da parte 4 do script (faturamento por ano, trimestre, categoria e cidade da loja)
usa 4 juncoes no OLTP e levou cerca de 3 ms. Ela sera reescrita no estrela e no floco de neve e
executada varias vezes em cada modelo (usar a mediana, porque com 5 mil itens os tempos sao
pequenos e variam). O numero de juncoes de cada versao entra na questao 9 do relatorio.

### Mineracao

A pergunta 8 tem duas partes, entao a proposta e:

- **Principal: regras de associacao (Apriori)** sobre os itens de cada venda, com suporte
  minimo de 2% e confianca minima de 40% (os mesmos cortes da previa). A previa ja mostra regras
  fortes e faceis de explicar (questao 13 do relatorio: suporte, confianca e lift).
- **Complemento: k-means** dos clientes por recencia, frequencia e valor (normalizados), com o
  numero de grupos escolhido pelo metodo do cotovelo.

Base exportada do DW, notebook Python (pandas, scikit-learn, mlxtend) ou Weka.

## Uso de IA

A analise e o script desta parte foram preparados com apoio do Claude (Anthropic), conforme
pede o enunciado. Todas as consultas foram executadas e conferidas sobre os dados da equipe.
