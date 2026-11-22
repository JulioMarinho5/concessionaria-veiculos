-- =====================================================================
-- comparacao_tempo.sql - Mesma consulta no OLTP, no estrela e no floco de neve
-- Integrante 5 - Analista de BI e Mineracao
--
-- Requisito 4.3.3 (comparar o tempo) e questao de fixacao 9 (numero de juncoes).
--
-- Como executar (depois do ETL, a partir da raiz do projeto):
--   psql -U postgres -d concessionaria -f database/olap/comparacao_tempo.sql
--
-- Pergunta: faturamento liquido de veiculos por ano, trimestre, categoria
-- e cidade da loja.
--   OLTP    : venda, item_venda, veiculo, modelo, loja        -> 4 juncoes
--   Estrela : fato, dim_tempo, dim_produto, dim_loja          -> 3 juncoes
--   Floco   : fato, dim_tempo, dim_produto, dim_modelo,
--             dim_loja, dim_cidade                            -> 5 juncoes
--
-- Passos: (0) ANALYZE; (1) confere que as tres versoes dao o mesmo resultado;
-- (2) mede cada versao 200 vezes (depois de 5 execucoes de aquecimento)
-- e mostra a mediana; (3) mostra o plano de execucao de cada uma.
-- A funcao de medicao fica em pg_temp: some ao fechar a conexao e nao
-- cria nada permanente no banco.
-- =====================================================================

-- Atualiza as estatisticas das tabelas para o otimizador escolher bons planos
-- nos tres modelos (num banco recem-carregado elas ainda nao existem e a
-- comparacao ficaria injusta).
ANALYZE;

-- ---------------------------------------------------------------------
-- 1) As tres versoes devem devolver exatamente as mesmas linhas
-- ---------------------------------------------------------------------
WITH oltp AS (
  SELECT EXTRACT(YEAR FROM v.data_hora)::int    AS ano,
         EXTRACT(QUARTER FROM v.data_hora)::int AS trimestre,
         mo.categoria,
         l.cidade,
         SUM(i.quantidade * i.preco_unitario - COALESCE(i.desconto, 0)) AS faturamento
  FROM venda v
  JOIN item_venda i ON i.venda_id = v.id
  JOIN veiculo ve   ON ve.id = i.veiculo_id
  JOIN modelo mo    ON mo.id = ve.modelo_id
  JOIN loja l       ON l.id = v.loja_id
  GROUP BY 1, 2, 3, 4
), estrela AS (
  SELECT t.ano::int, t.trimestre::int, p.categoria, l.cidade,
         SUM(f.valor_liquido) AS faturamento
  FROM dw_estrela.fato_venda f
  JOIN dw_estrela.dim_tempo t   ON t.sk_tempo = f.sk_tempo
  JOIN dw_estrela.dim_produto p ON p.sk_produto = f.sk_produto
  JOIN dw_estrela.dim_loja l    ON l.sk_loja = f.sk_loja
  WHERE p.tipo_item = 'VEICULO'
  GROUP BY 1, 2, 3, 4
), floco AS (
  SELECT t.ano::int, t.trimestre::int, mo.categoria, c.nome AS cidade,
         SUM(f.valor_liquido) AS faturamento
  FROM dw_floco.fato_venda f
  JOIN dw_floco.dim_tempo t   ON t.sk_tempo = f.sk_tempo
  JOIN dw_floco.dim_produto p ON p.sk_produto = f.sk_produto
  JOIN dw_floco.dim_modelo mo ON mo.sk_modelo = p.sk_modelo
  JOIN dw_floco.dim_loja l    ON l.sk_loja = f.sk_loja
  JOIN dw_floco.dim_cidade c  ON c.sk_cidade = l.sk_cidade
  WHERE p.tipo_item = 'VEICULO'
  GROUP BY 1, 2, 3, 4
)
SELECT (SELECT COUNT(*) FROM oltp)    AS linhas_oltp,
       (SELECT COUNT(*) FROM estrela) AS linhas_estrela,
       (SELECT COUNT(*) FROM floco)   AS linhas_floco,
       (SELECT COUNT(*) FROM (SELECT * FROM oltp EXCEPT SELECT * FROM estrela) d) AS diferencas_oltp_estrela,
       (SELECT COUNT(*) FROM (SELECT * FROM oltp EXCEPT SELECT * FROM floco) d)   AS diferencas_oltp_floco;


-- ---------------------------------------------------------------------
-- 2) Tempo de execucao: mediana de 200 execucoes de cada versao
-- ---------------------------------------------------------------------
CREATE FUNCTION pg_temp.medir(consulta TEXT, repeticoes INT DEFAULT 200)
RETURNS TABLE (mediana_ms NUMERIC, minimo_ms NUMERIC, maximo_ms NUMERIC)
LANGUAGE plpgsql AS $$
DECLARE
  inicio TIMESTAMPTZ;
  tempos NUMERIC[] := '{}';
BEGIN
  FOR i IN 1..5 LOOP            -- aquecimento (dados ja ficam em memoria)
    EXECUTE consulta;
  END LOOP;
  FOR i IN 1..repeticoes LOOP
    inicio := clock_timestamp();
    EXECUTE consulta;
    tempos := tempos || (EXTRACT(EPOCH FROM clock_timestamp() - inicio) * 1000)::numeric;
  END LOOP;
  RETURN QUERY
    SELECT ROUND(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x)::numeric, 3),
           ROUND(MIN(x), 3), ROUND(MAX(x), 3)
    FROM unnest(tempos) AS x;
END;
$$;

SELECT 'OLTP (4 juncoes)' AS modelo, m.*
FROM pg_temp.medir($q$
  SELECT EXTRACT(YEAR FROM v.data_hora), EXTRACT(QUARTER FROM v.data_hora), mo.categoria, l.cidade,
         SUM(i.quantidade * i.preco_unitario - COALESCE(i.desconto, 0))
  FROM venda v
  JOIN item_venda i ON i.venda_id = v.id
  JOIN veiculo ve   ON ve.id = i.veiculo_id
  JOIN modelo mo    ON mo.id = ve.modelo_id
  JOIN loja l       ON l.id = v.loja_id
  GROUP BY 1, 2, 3, 4
$q$) m
UNION ALL
SELECT 'Estrela (3 juncoes)', m.*
FROM pg_temp.medir($q$
  SELECT t.ano, t.trimestre, p.categoria, l.cidade, SUM(f.valor_liquido)
  FROM dw_estrela.fato_venda f
  JOIN dw_estrela.dim_tempo t   ON t.sk_tempo = f.sk_tempo
  JOIN dw_estrela.dim_produto p ON p.sk_produto = f.sk_produto
  JOIN dw_estrela.dim_loja l    ON l.sk_loja = f.sk_loja
  WHERE p.tipo_item = 'VEICULO'
  GROUP BY 1, 2, 3, 4
$q$) m
UNION ALL
SELECT 'Floco de neve (5 juncoes)', m.*
FROM pg_temp.medir($q$
  SELECT t.ano, t.trimestre, mo.categoria, c.nome, SUM(f.valor_liquido)
  FROM dw_floco.fato_venda f
  JOIN dw_floco.dim_tempo t   ON t.sk_tempo = f.sk_tempo
  JOIN dw_floco.dim_produto p ON p.sk_produto = f.sk_produto
  JOIN dw_floco.dim_modelo mo ON mo.sk_modelo = p.sk_modelo
  JOIN dw_floco.dim_loja l    ON l.sk_loja = f.sk_loja
  JOIN dw_floco.dim_cidade c  ON c.sk_cidade = l.sk_cidade
  WHERE p.tipo_item = 'VEICULO'
  GROUP BY 1, 2, 3, 4
$q$) m;


-- ---------------------------------------------------------------------
-- 3) Planos de execucao (uma execucao de cada, com tempos reais)
-- ---------------------------------------------------------------------
\echo '--- Plano: OLTP ---'
EXPLAIN (ANALYZE, COSTS OFF)
SELECT EXTRACT(YEAR FROM v.data_hora), EXTRACT(QUARTER FROM v.data_hora), mo.categoria, l.cidade,
       SUM(i.quantidade * i.preco_unitario - COALESCE(i.desconto, 0))
FROM venda v
JOIN item_venda i ON i.venda_id = v.id
JOIN veiculo ve   ON ve.id = i.veiculo_id
JOIN modelo mo    ON mo.id = ve.modelo_id
JOIN loja l       ON l.id = v.loja_id
GROUP BY 1, 2, 3, 4;

\echo '--- Plano: Estrela ---'
EXPLAIN (ANALYZE, COSTS OFF)
SELECT t.ano, t.trimestre, p.categoria, l.cidade, SUM(f.valor_liquido)
FROM dw_estrela.fato_venda f
JOIN dw_estrela.dim_tempo t   ON t.sk_tempo = f.sk_tempo
JOIN dw_estrela.dim_produto p ON p.sk_produto = f.sk_produto
JOIN dw_estrela.dim_loja l    ON l.sk_loja = f.sk_loja
WHERE p.tipo_item = 'VEICULO'
GROUP BY 1, 2, 3, 4;

\echo '--- Plano: Floco de neve ---'
EXPLAIN (ANALYZE, COSTS OFF)
SELECT t.ano, t.trimestre, mo.categoria, c.nome, SUM(f.valor_liquido)
FROM dw_floco.fato_venda f
JOIN dw_floco.dim_tempo t   ON t.sk_tempo = f.sk_tempo
JOIN dw_floco.dim_produto p ON p.sk_produto = f.sk_produto
JOIN dw_floco.dim_modelo mo ON mo.sk_modelo = p.sk_modelo
JOIN dw_floco.dim_loja l    ON l.sk_loja = f.sk_loja
JOIN dw_floco.dim_cidade c  ON c.sk_cidade = l.sk_cidade
WHERE p.tipo_item = 'VEICULO'
GROUP BY 1, 2, 3, 4;
