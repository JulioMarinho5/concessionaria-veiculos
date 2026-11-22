-- =====================================================================
-- exportar_base.sql - Exporta do DW (modelo estrela) as bases da mineracao
-- Integrante 5 - Analista de BI e Mineracao
--
-- Como executar (depois do ETL, a partir da RAIZ do projeto, pelo psql):
--   psql -U postgres -d concessionaria -f mineracao/exportar_base.sql
-- O comando \copy grava os arquivos no computador de quem roda o psql,
-- nos caminhos abaixo (relativos a pasta onde o psql foi aberto).
--
-- Gera dois arquivos CSV:
--   mineracao/base_cestas.csv   -> uma linha por (venda, item): base do Apriori
--   mineracao/base_clientes.csv -> uma linha por cliente com R, F e V: base do k-means
-- =====================================================================

-- 1) Cestas: cada venda e uma "cesta" de itens.
--    Servico entra pelo nome; veiculo entra pela categoria ("Veiculo SUV"),
--    porque o modelo exato quase nunca se repete e nao gera regra util.
\copy (SELECT DISTINCT f.id_venda, CASE WHEN p.tipo_item = 'VEICULO' THEN 'Veiculo ' || p.categoria ELSE p.nome END AS item FROM dw_estrela.fato_venda f JOIN dw_estrela.dim_produto p ON p.sk_produto = f.sk_produto ORDER BY 1, 2) TO 'mineracao/base_cestas.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

-- 2) Clientes: Recencia (dias desde a ultima compra, ate a ultima data com venda
--    no DW), Frequencia (numero de vendas) e Valor (faturamento liquido).
--    O cliente e identificado pela chave do OLTP (id_origem), juntando as
--    versoes do SCD tipo 2 de um mesmo cliente.
\copy (WITH ref AS (SELECT MAX(t.data) AS data_ref FROM dw_estrela.fato_venda f JOIN dw_estrela.dim_tempo t ON t.sk_tempo = f.sk_tempo) SELECT c.id_origem AS id_cliente, (SELECT data_ref FROM ref) - MAX(t.data) AS recencia_dias, COUNT(DISTINCT f.id_venda) AS frequencia, SUM(f.valor_liquido) AS valor, COUNT(DISTINCT f.id_venda) FILTER (WHERE p.tipo_item = 'VEICULO') AS compras_veiculo FROM dw_estrela.fato_venda f JOIN dw_estrela.dim_cliente c ON c.sk_cliente = f.sk_cliente JOIN dw_estrela.dim_tempo t ON t.sk_tempo = f.sk_tempo JOIN dw_estrela.dim_produto p ON p.sk_produto = f.sk_produto GROUP BY c.id_origem ORDER BY 1) TO 'mineracao/base_clientes.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
