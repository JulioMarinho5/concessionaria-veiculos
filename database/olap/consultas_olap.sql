-- =====================================================================
-- consultas_olap.sql - Consultas OLAP no Data Warehouse (modelo estrela)
-- Integrante 5 - Analista de BI e Mineracao
--
-- Requisito 4.3.3: 2 consultas para cada operacao (roll-up, drill-down,
-- slice, dice e pivot), usando GROUP BY ROLLUP/CUBE.
--
-- Como executar (depois do ETL, a partir da raiz do projeto):
--   psql -U postgres -d concessionaria -f database/olap/consultas_olap.sql
--
-- Todas leem o esquema dw_estrela. Medida principal: valor_liquido
-- (valor bruto - desconto), ja calculada pelo ETL. A coluna "nivel" ou os
-- rotulos "Total ..." marcam as linhas de subtotal geradas pelo ROLLUP/CUBE
-- (funcao GROUPING retorna 1 quando a coluna foi agregada naquela linha).
-- Os dados vao de jan/2024 a out/2026: 2026 nao tem novembro e dezembro.
-- =====================================================================


-- =====================================================================
-- ROLL-UP: sobe na hierarquia de uma dimensao, somando os niveis abaixo
-- =====================================================================

-- RU1. Faturamento por mes, subindo para trimestre, ano e total geral.
--      Hierarquia da dimensao Tempo: mes -> trimestre -> ano.
--      Pergunta de negocio 1 (evolucao do faturamento).
SELECT CASE WHEN GROUPING(t.ano) = 1       THEN 'Total geral'
            WHEN GROUPING(t.trimestre) = 1 THEN 'Total do ano'
            WHEN GROUPING(t.mes) = 1       THEN 'Total do trimestre'
            ELSE 'Mes' END              AS nivel,
       t.ano, t.trimestre, t.mes,
       COUNT(DISTINCT f.id_venda)       AS vendas,
       SUM(f.valor_liquido)             AS faturamento
FROM dw_estrela.fato_venda f
JOIN dw_estrela.dim_tempo t ON t.sk_tempo = f.sk_tempo
GROUP BY ROLLUP (t.ano, t.trimestre, t.mes)
ORDER BY t.ano NULLS LAST, t.trimestre NULLS LAST, t.mes NULLS LAST;

-- RU2. Veiculos vendidos por marca, subindo para categoria e total geral.
--      Hierarquia da dimensao Produto: marca -> categoria -> todos.
--      Pergunta de negocio 2 (o que mais vende).
SELECT CASE WHEN GROUPING(p.categoria) = 1 THEN 'Total geral'
            WHEN GROUPING(p.marca) = 1     THEN 'Total da categoria'
            ELSE 'Marca' END            AS nivel,
       p.categoria, p.marca,
       SUM(f.quantidade)                AS unidades,
       SUM(f.valor_liquido)             AS faturamento
FROM dw_estrela.fato_venda f
JOIN dw_estrela.dim_produto p ON p.sk_produto = f.sk_produto
WHERE p.tipo_item = 'VEICULO'
GROUP BY ROLLUP (p.categoria, p.marca)
ORDER BY p.categoria NULLS LAST, GROUPING(p.marca), unidades DESC, faturamento DESC;


-- =====================================================================
-- DRILL-DOWN: desce na hierarquia, do resumo para o detalhe
-- =====================================================================

-- DD1. O roll-up RU2 mostra que SUV e a categoria que mais fatura.
--      Aqui descemos de SUV para o nivel mais detalhado da dimensao Produto:
--      cada modelo (com a marca), com unidades, ticket medio e participacao.
SELECT p.marca, p.modelo,
       SUM(f.quantidade)                                  AS unidades,
       SUM(f.valor_liquido)                               AS faturamento,
       ROUND(SUM(f.valor_liquido) / SUM(f.quantidade), 2) AS ticket_medio,
       ROUND(100 * SUM(f.valor_liquido) / SUM(SUM(f.valor_liquido)) OVER (), 1) AS pct_da_categoria
FROM dw_estrela.fato_venda f
JOIN dw_estrela.dim_produto p ON p.sk_produto = f.sk_produto
WHERE p.tipo_item = 'VEICULO'
  AND p.categoria = 'SUV'
GROUP BY p.marca, p.modelo
ORDER BY faturamento DESC;

-- DD2. Do faturamento de cada loja para o faturamento de cada vendedor
--      dentro da loja (participacao e posicao no ranking da loja).
--      Pergunta de negocio 3 (vendedores que mais vendem).
SELECT l.nome AS loja, v.nome AS vendedor, v.cargo,
       COUNT(DISTINCT f.id_venda) AS vendas,
       SUM(f.valor_liquido)       AS faturamento,
       ROUND(100 * SUM(f.valor_liquido)
             / SUM(SUM(f.valor_liquido)) OVER (PARTITION BY l.nome), 1) AS pct_da_loja,
       RANK() OVER (PARTITION BY l.nome ORDER BY SUM(f.valor_liquido) DESC) AS posicao
FROM dw_estrela.fato_venda f
JOIN dw_estrela.dim_loja l     ON l.sk_loja = f.sk_loja
JOIN dw_estrela.dim_vendedor v ON v.sk_vendedor = f.sk_vendedor
GROUP BY l.nome, v.nome, v.cargo
ORDER BY l.nome, posicao;


-- =====================================================================
-- SLICE: fixa UM valor de uma dimensao e analisa o "fatia" resultante
-- =====================================================================

-- SL1. Fatia: forma de pagamento = Financiamento.
--      Quais categorias de veiculo sao mais financiadas?
--      Pergunta de negocio 5 (formas de pagamento).
SELECT p.categoria,
       SUM(f.quantidade)                                   AS unidades,
       SUM(f.valor_liquido)                                AS faturamento,
       ROUND(SUM(f.valor_liquido) / SUM(f.quantidade), 2)  AS ticket_medio
FROM dw_estrela.fato_venda f
JOIN dw_estrela.dim_produto p         ON p.sk_produto = f.sk_produto
JOIN dw_estrela.dim_forma_pagamento g ON g.sk_forma_pagamento = f.sk_forma_pagamento
WHERE g.codigo = 'FINANCIAMENTO'
  AND p.tipo_item = 'VEICULO'
GROUP BY p.categoria
ORDER BY unidades DESC;

-- SL2. Fatia: ano = 2025 (ultimo ano completo).
--      Faturamento bruto, desconto e % de desconto por loja.
--      Pergunta de negocio 4 (desempenho das lojas).
SELECT l.nome AS loja,
       SUM(f.valor_bruto)   AS bruto,
       SUM(f.desconto)      AS desconto,
       SUM(f.valor_liquido) AS liquido,
       ROUND(100 * SUM(f.desconto) / SUM(f.valor_bruto), 2) AS pct_desconto
FROM dw_estrela.fato_venda f
JOIN dw_estrela.dim_tempo t ON t.sk_tempo = f.sk_tempo
JOIN dw_estrela.dim_loja l  ON l.sk_loja = f.sk_loja
WHERE t.ano = 2025
GROUP BY l.nome
ORDER BY liquido DESC;


-- =====================================================================
-- DICE: fixa VARIOS valores em duas ou mais dimensoes (um "sub-cubo")
-- =====================================================================

-- DC1. Sub-cubo: produto em {SUV, Picape} x loja em Salvador x ano em {2025, 2026},
--      so janeiro a outubro para comparar os dois anos no mesmo periodo.
SELECT t.ano, l.nome AS loja, p.categoria,
       SUM(f.quantidade)    AS unidades,
       SUM(f.valor_liquido) AS faturamento
FROM dw_estrela.fato_venda f
JOIN dw_estrela.dim_tempo t   ON t.sk_tempo = f.sk_tempo
JOIN dw_estrela.dim_loja l    ON l.sk_loja = f.sk_loja
JOIN dw_estrela.dim_produto p ON p.sk_produto = f.sk_produto
WHERE p.categoria IN ('SUV', 'Picape')
  AND l.cidade = 'Salvador'
  AND t.ano IN (2025, 2026)
  AND t.mes <= 10
GROUP BY t.ano, l.nome, p.categoria
ORDER BY l.nome, p.categoria, t.ano;

-- DC2. Sub-cubo: produto = servicos x lojas do interior (Feira de Santana e
--      Vitoria da Conquista) x 2o semestre. Quais servicos puxam o pos-venda?
--      Pergunta de negocio 6 (participacao dos servicos).
SELECT l.cidade AS loja_cidade, p.nome AS servico,
       SUM(f.quantidade)    AS quantidade,
       SUM(f.valor_liquido) AS faturamento
FROM dw_estrela.fato_venda f
JOIN dw_estrela.dim_tempo t   ON t.sk_tempo = f.sk_tempo
JOIN dw_estrela.dim_loja l    ON l.sk_loja = f.sk_loja
JOIN dw_estrela.dim_produto p ON p.sk_produto = f.sk_produto
WHERE p.tipo_item = 'SERVICO'
  AND l.cidade IN ('Feira de Santana', 'Vitoria da Conquista')
  AND t.semestre = 2
GROUP BY l.cidade, p.nome
HAVING SUM(f.quantidade) >= 10
ORDER BY l.cidade, faturamento DESC;


-- =====================================================================
-- PIVOT: gira um eixo, transformando valores de linha em colunas
-- =====================================================================

-- PV1. Lojas nas linhas x anos nas colunas (faturamento em R$ mil),
--      com a linha "Total" gerada pelo ROLLUP. 2026 vai so ate outubro.
SELECT COALESCE(l.nome, 'Total')                                       AS loja,
       ROUND(SUM(f.valor_liquido) FILTER (WHERE t.ano = 2024) / 1000, 1) AS "2024",
       ROUND(SUM(f.valor_liquido) FILTER (WHERE t.ano = 2025) / 1000, 1) AS "2025",
       ROUND(SUM(f.valor_liquido) FILTER (WHERE t.ano = 2026) / 1000, 1) AS "2026 (jan-out)",
       ROUND(SUM(f.valor_liquido) / 1000, 1)                            AS total
FROM dw_estrela.fato_venda f
JOIN dw_estrela.dim_tempo t ON t.sk_tempo = f.sk_tempo
JOIN dw_estrela.dim_loja l  ON l.sk_loja = f.sk_loja
GROUP BY ROLLUP (l.nome)
ORDER BY GROUPING(l.nome), total DESC;

-- PV2. Categoria de veiculo x forma de pagamento (unidades), com GROUP BY CUBE.
--      O CUBE calcula todas as combinacoes, inclusive os totais de cada
--      categoria e de cada forma; a consulta externa gira as formas para colunas.
--      Pergunta de negocio 5.
WITH cubo AS (
  SELECT COALESCE(p.categoria, 'Total') AS categoria,
         COALESCE(g.descricao, 'Total') AS forma,
         SUM(f.quantidade)              AS unidades
  FROM dw_estrela.fato_venda f
  JOIN dw_estrela.dim_produto p         ON p.sk_produto = f.sk_produto
  JOIN dw_estrela.dim_forma_pagamento g ON g.sk_forma_pagamento = f.sk_forma_pagamento
  WHERE p.tipo_item = 'VEICULO'
  GROUP BY CUBE (p.categoria, g.descricao)
)
SELECT categoria,
       SUM(unidades) FILTER (WHERE forma = 'Financiamento') AS financiamento,
       SUM(unidades) FILTER (WHERE forma = 'A Vista')       AS a_vista,
       SUM(unidades) FILTER (WHERE forma = 'Consorcio')     AS consorcio,
       SUM(unidades) FILTER (WHERE forma = 'Cartao')        AS cartao,
       SUM(unidades) FILTER (WHERE forma = 'Total')         AS total
FROM cubo
GROUP BY categoria
ORDER BY categoria = 'Total', total DESC;
