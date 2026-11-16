-- =====================================================================
-- analise_bi_oltp.sql - Analise de BI sobre o OLTP (Concessionaria)
-- Integrante 5 - Analista de BI e Mineracao
--
-- Como executar (na ordem, a partir da raiz do projeto):
--   psql -U postgres -d concessionaria -f database/oltp/schema-OLTP.sql
--   psql -U postgres -d concessionaria -f database/oltp/carga_dados.sql
--   psql -U postgres -d concessionaria -f database/bi/analise_bi_oltp.sql
-- (ou usar a carga Java carga/CargaDados.java no lugar dos dois primeiros)
--
-- Objetivo: antes de montar o DW, conferir se os dados do OLTP respondem
-- as "Perguntas de Negocio" (documents/Perguntas de Negocio.txt) e se
-- tem padroes suficientes para o OLAP e a mineracao da Etapa 2.
--
-- Parte 1: perfil dos atributos que vao virar dimensao (cardinalidade,
--          nulos, dominios) - insumo para o Arquiteto e o ETL.
-- Parte 2: perguntas de negocio 1 a 7 em SQL puro sobre o OLTP. Sao a
--          linha de base: as mesmas perguntas serao refeitas no modelo
--          estrela e no floco de neve (requisito 4.3.3).
-- Parte 3: pergunta 8 - pre-analise para a mineracao (perfil RFM dos
--          clientes e itens comprados juntos, com suporte/confianca/lift).
-- Parte 4: consulta de referencia para comparar o tempo
--          OLTP x estrela x floco de neve (EXPLAIN ANALYZE).
--
-- Somente SELECT: o script nao altera nada no banco.
-- Valor liquido do item = quantidade * preco_unitario - desconto.
-- =====================================================================


-- ---------------------------------------------------------------------
-- PARTE 1 - Perfil dos atributos candidatos a dimensao
-- ---------------------------------------------------------------------

-- 1.1 Cardinalidade de cada atributo descritivo (quantos valores distintos)
SELECT 'cliente.cidade'        AS atributo, COUNT(DISTINCT p.cidade)  AS distintos FROM cliente c JOIN pessoa p ON p.id = c.id
UNION ALL SELECT 'cliente.estado',        COUNT(DISTINCT p.estado)  FROM cliente c JOIN pessoa p ON p.id = c.id
UNION ALL SELECT 'loja.cidade',           COUNT(DISTINCT cidade)    FROM loja
UNION ALL SELECT 'marca.nome',            COUNT(DISTINCT nome)      FROM marca
UNION ALL SELECT 'modelo.nome',           COUNT(DISTINCT nome)      FROM modelo
UNION ALL SELECT 'modelo.categoria',      COUNT(DISTINCT categoria) FROM modelo
UNION ALL SELECT 'veiculo.cor',           COUNT(DISTINCT cor)       FROM veiculo
UNION ALL SELECT 'servico.nome',          COUNT(DISTINCT nome)      FROM servico
UNION ALL SELECT 'venda.forma_pagamento', COUNT(DISTINCT forma_pagamento) FROM venda
UNION ALL SELECT 'funcionario.cargo',     COUNT(DISTINCT cargo)     FROM funcionario;

-- 1.2 Nulos e textos fora do padrao (espacos nas pontas, minusculas)
--     O ETL precisa tratar esses casos; aqui medimos quantos existem hoje.
SELECT verificacao, ocorrencias
FROM (
  SELECT 'Pessoa sem telefone'                      AS verificacao, COUNT(*) AS ocorrencias FROM pessoa WHERE telefone IS NULL OR telefone = ''
  UNION ALL SELECT 'Pessoa sem cidade/UF',           COUNT(*) FROM pessoa WHERE cidade IS NULL OR estado IS NULL
  UNION ALL SELECT 'Cidade com espaco nas pontas',   COUNT(*) FROM pessoa WHERE cidade <> TRIM(cidade)
  UNION ALL SELECT 'Cidade toda em minusculas',      COUNT(*) FROM pessoa WHERE cidade = LOWER(cidade)
  UNION ALL SELECT 'Modelo sem categoria',           COUNT(*) FROM modelo WHERE categoria IS NULL
  UNION ALL SELECT 'Veiculo sem cor',                COUNT(*) FROM veiculo WHERE cor IS NULL
  UNION ALL SELECT 'Item com desconto nulo',         COUNT(*) FROM item_venda WHERE desconto IS NULL
  UNION ALL SELECT 'Cliente sem CNH (sem documento)', COUNT(*) FROM cliente c
             WHERE NOT EXISTS (SELECT 1 FROM documento_cliente d WHERE d.cliente_id = c.id)
  UNION ALL SELECT 'Cliente que nunca comprou',      COUNT(*) FROM cliente c
             WHERE NOT EXISTS (SELECT 1 FROM venda v WHERE v.cliente_id = c.id)
) r;

-- 1.3 Distribuicao dos clientes por cidade (dimensao Cliente / SCD tipo 2)
SELECT p.cidade, p.estado, COUNT(*) AS clientes
FROM cliente c JOIN pessoa p ON p.id = c.id
GROUP BY p.cidade, p.estado
ORDER BY clientes DESC, p.cidade;


-- ---------------------------------------------------------------------
-- PARTE 2 - Perguntas de negocio 1 a 7 (linha de base no OLTP)
-- ---------------------------------------------------------------------

-- P1a. Faturamento por ano e trimestre.
--      2026 so tem dados ate outubro, por isso o total anual engana.
SELECT EXTRACT(YEAR FROM v.data_hora)::int    AS ano,
       EXTRACT(QUARTER FROM v.data_hora)::int AS trimestre,
       COUNT(DISTINCT v.id)                   AS vendas,
       SUM(i.quantidade * i.preco_unitario - COALESCE(i.desconto, 0)) AS faturamento
FROM venda v
JOIN item_venda i ON i.venda_id = v.id
GROUP BY 1, 2
ORDER BY 1, 2;

-- P1b. Comparacao justa entre anos: so janeiro a outubro de cada ano.
SELECT EXTRACT(YEAR FROM v.data_hora)::int AS ano,
       COUNT(DISTINCT v.id)                AS vendas_jan_out,
       SUM(i.quantidade * i.preco_unitario - COALESCE(i.desconto, 0)) AS faturamento_jan_out
FROM venda v
JOIN item_venda i ON i.venda_id = v.id
WHERE EXTRACT(MONTH FROM v.data_hora) <= 10
GROUP BY 1
ORDER BY 1;

-- P1c. Sazonalidade: media de vendas por mes do ano (2024 e 2025, anos completos).
SELECT EXTRACT(MONTH FROM data_hora)::int AS mes,
       ROUND(COUNT(*) / 2.0, 1)           AS media_vendas_mes
FROM venda
WHERE data_hora < DATE '2026-01-01'
GROUP BY 1
ORDER BY 1;

-- P2. Marcas e categorias que mais vendem (unidades e faturamento de veiculos).
SELECT ma.nome AS marca, mo.categoria,
       COUNT(*) AS unidades,
       SUM(i.preco_unitario - COALESCE(i.desconto, 0)) AS faturamento
FROM item_venda i
JOIN veiculo ve ON ve.id = i.veiculo_id
JOIN modelo mo  ON mo.id = ve.modelo_id
JOIN marca ma   ON ma.id = mo.marca_id
GROUP BY ma.nome, mo.categoria
ORDER BY unidades DESC, faturamento DESC
LIMIT 15;

-- P3. Melhor vendedor de cada loja (funcao de janela RANK por loja).
SELECT loja, vendedor, vendas, faturamento
FROM (
  SELECT l.nome AS loja, p.nome AS vendedor,
         COUNT(DISTINCT v.id) AS vendas,
         SUM(i.quantidade * i.preco_unitario - COALESCE(i.desconto, 0)) AS faturamento,
         RANK() OVER (PARTITION BY l.id
                      ORDER BY SUM(i.quantidade * i.preco_unitario - COALESCE(i.desconto, 0)) DESC) AS posicao
  FROM venda v
  JOIN item_venda i  ON i.venda_id = v.id
  JOIN funcionario f ON f.id = v.vendedor_id
  JOIN pessoa p      ON p.id = f.id
  JOIN loja l        ON l.id = v.loja_id
  WHERE f.cargo = 'Vendedor'
  GROUP BY l.id, l.nome, p.nome
) r
WHERE posicao = 1
ORDER BY faturamento DESC;

-- P4. Faturamento e desconto concedido por loja.
SELECT l.nome AS loja,
       SUM(i.quantidade * i.preco_unitario)              AS bruto,
       SUM(COALESCE(i.desconto, 0))                                   AS desconto,
       SUM(i.quantidade * i.preco_unitario - COALESCE(i.desconto, 0)) AS liquido,
       ROUND(100 * SUM(COALESCE(i.desconto, 0)) / SUM(i.quantidade * i.preco_unitario), 2) AS pct_desconto
FROM venda v
JOIN item_venda i ON i.venda_id = v.id
JOIN loja l       ON l.id = v.loja_id
GROUP BY l.nome
ORDER BY liquido DESC;

-- P5. Forma de pagamento por tipo de venda (com veiculo x so servicos).
SELECT CASE WHEN EXISTS (SELECT 1 FROM item_venda i WHERE i.venda_id = v.id AND i.veiculo_id IS NOT NULL)
            THEN 'Venda de veiculo' ELSE 'So servicos' END AS tipo_venda,
       v.forma_pagamento,
       COUNT(*) AS vendas
FROM venda v
GROUP BY 1, 2
ORDER BY 1, vendas DESC;

-- P6. Participacao dos servicos no faturamento, ano a ano.
SELECT EXTRACT(YEAR FROM v.data_hora)::int AS ano,
       SUM(CASE WHEN i.veiculo_id IS NOT NULL THEN i.quantidade * i.preco_unitario - COALESCE(i.desconto, 0) ELSE 0 END) AS veiculos,
       SUM(CASE WHEN i.servico_id IS NOT NULL THEN i.quantidade * i.preco_unitario - COALESCE(i.desconto, 0) ELSE 0 END) AS servicos,
       ROUND(100 * SUM(CASE WHEN i.servico_id IS NOT NULL THEN i.quantidade * i.preco_unitario - COALESCE(i.desconto, 0) ELSE 0 END)
                 / SUM(i.quantidade * i.preco_unitario - COALESCE(i.desconto, 0)), 2) AS pct_servicos
FROM venda v
JOIN item_venda i ON i.venda_id = v.id
GROUP BY 1
ORDER BY 1;

-- P7. De onde vem o cliente de cada loja (cidade do cliente x loja da venda).
SELECT l.nome AS loja, p.cidade AS cidade_cliente, COUNT(*) AS vendas,
       ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (PARTITION BY l.id), 1) AS pct_da_loja
FROM venda v
JOIN loja l   ON l.id = v.loja_id
JOIN pessoa p ON p.id = v.cliente_id
GROUP BY l.id, l.nome, p.cidade
HAVING COUNT(*) >= 20
ORDER BY l.nome, vendas DESC;


-- ---------------------------------------------------------------------
-- PARTE 3 - Pergunta 8: pre-analise para a mineracao
-- ---------------------------------------------------------------------

-- 3.1 Perfil RFM de cada cliente (Recencia, Frequencia, Valor).
--     Base para o k-means da Etapa 2. Data de referencia: ultima venda (31/10/2026).
--     Resumo em quartis para ver se ha grupos bem diferentes.
WITH rfm AS (
  SELECT v.cliente_id,
         DATE '2026-10-31' - MAX(v.data_hora)::date            AS recencia_dias,
         COUNT(DISTINCT v.id)                                   AS frequencia,
         SUM(i.quantidade * i.preco_unitario - COALESCE(i.desconto, 0))      AS valor
  FROM venda v
  JOIN item_venda i ON i.venda_id = v.id
  GROUP BY v.cliente_id
)
SELECT 'recencia (dias)' AS medida, MIN(recencia_dias)::numeric AS minimo,
       PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY recencia_dias)::numeric AS q1,
       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY recencia_dias)::numeric AS mediana,
       PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY recencia_dias)::numeric AS q3,
       MAX(recencia_dias)::numeric AS maximo
FROM rfm
UNION ALL
SELECT 'frequencia (vendas)', MIN(frequencia),
       PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY frequencia)::numeric,
       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY frequencia)::numeric,
       PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY frequencia)::numeric,
       MAX(frequencia)
FROM rfm
UNION ALL
SELECT 'valor (R$)', ROUND(MIN(valor), 2),
       ROUND(PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY valor)::numeric, 2),
       ROUND(PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY valor)::numeric, 2),
       ROUND(PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY valor)::numeric, 2),
       ROUND(MAX(valor), 2)
FROM rfm;

-- 3.2 Concentracao: quanto do faturamento vem dos 10% maiores clientes?
WITH valor_cliente AS (
  SELECT v.cliente_id, SUM(i.quantidade * i.preco_unitario - COALESCE(i.desconto, 0)) AS valor
  FROM venda v JOIN item_venda i ON i.venda_id = v.id
  GROUP BY v.cliente_id
), faixas AS (
  SELECT valor, NTILE(10) OVER (ORDER BY valor DESC) AS decil FROM valor_cliente
)
SELECT CASE WHEN decil = 1 THEN 'Top 10% dos clientes' ELSE 'Demais 90%' END AS grupo,
       COUNT(*) AS clientes,
       SUM(valor) AS faturamento,
       ROUND(100 * SUM(valor) / SUM(SUM(valor)) OVER (), 1) AS pct_faturamento
FROM faixas
GROUP BY 1
ORDER BY 1 DESC;

-- 3.3 Itens comprados juntos na mesma venda (previa do Apriori).
--     Cada venda e uma "cesta"; o veiculo entra pela categoria (VEICULO_SUV...).
--     suporte   = vendas com X e Y / total de vendas
--     confianca = vendas com X e Y / vendas com X
--     lift      = confianca / suporte de Y  (> 1: X aumenta a chance de Y)
--     Filtros: suporte >= 2% e confianca >= 40%.
WITH cesta AS (
  SELECT DISTINCT i.venda_id, s.nome AS item
  FROM item_venda i JOIN servico s ON s.id = i.servico_id
  UNION
  SELECT DISTINCT i.venda_id, 'VEICULO_' || UPPER(mo.categoria)
  FROM item_venda i
  JOIN veiculo ve ON ve.id = i.veiculo_id
  JOIN modelo mo  ON mo.id = ve.modelo_id
), total AS (
  SELECT COUNT(DISTINCT venda_id)::numeric AS n FROM cesta
), freq_item AS (
  SELECT item, COUNT(*) AS qtd FROM cesta GROUP BY item
), pares AS (
  SELECT a.item AS x, b.item AS y, COUNT(*) AS qtd
  FROM cesta a JOIN cesta b ON b.venda_id = a.venda_id AND b.item <> a.item
  GROUP BY a.item, b.item
)
SELECT p.x AS se_comprou, p.y AS entao_compra, p.qtd AS vendas,
       ROUND(p.qtd / t.n, 3)                              AS suporte,
       ROUND(p.qtd::numeric / fx.qtd, 3)                  AS confianca,
       ROUND((p.qtd::numeric / fx.qtd) / (fy.qtd / t.n), 2) AS lift
FROM pares p
JOIN freq_item fx ON fx.item = p.x
JOIN freq_item fy ON fy.item = p.y
CROSS JOIN total t
WHERE p.qtd / t.n >= 0.02
  AND p.qtd::numeric / fx.qtd >= 0.40
ORDER BY lift DESC, confianca DESC
LIMIT 15;


-- ---------------------------------------------------------------------
-- PARTE 4 - Consulta de referencia para comparar tempos (requisito 4.3.3)
-- ---------------------------------------------------------------------
-- Faturamento por ano, trimestre, categoria do veiculo e cidade da loja.
-- No OLTP sao 4 juncoes entre 5 tabelas (venda, item_venda, veiculo, modelo, loja).
-- A mesma pergunta sera escrita no estrela e no floco de neve; o tempo de
-- execucao das tres versoes vai para o relatorio (questao 9).
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT EXTRACT(YEAR FROM v.data_hora)    AS ano,
       EXTRACT(QUARTER FROM v.data_hora) AS trimestre,
       mo.categoria,
       l.cidade                          AS cidade_loja,
       SUM(i.preco_unitario - COALESCE(i.desconto, 0)) AS faturamento
FROM venda v
JOIN item_venda i ON i.venda_id = v.id
JOIN veiculo ve   ON ve.id = i.veiculo_id
JOIN modelo mo    ON mo.id = ve.modelo_id
JOIN loja l       ON l.id = v.loja_id
GROUP BY 1, 2, 3, 4;
