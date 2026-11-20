-- Parte 4 - ETL: execucao completa com contagem de linhas antes e depois
--
-- Ordem de instalacao (uma vez):
--   database/dw/schema-estrela.sql
--   database/dw/schema-floco-de-neve.sql
--   database/etl/01_staging.sql ... 05_executar_etl.sql
-- Depois, para cada nova carga (por exemplo, apos registrar uma venda na aplicacao):
--   psql -U postgres -d concessionaria -f database/etl/05_executar_etl.sql

CREATE OR REPLACE FUNCTION etl.contar_linhas()
RETURNS TABLE (tabela TEXT, linhas BIGINT)
LANGUAGE plpgsql AS $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT table_schema || '.' || table_name AS nome
        FROM information_schema.tables
        WHERE table_schema IN ('dw_estrela', 'dw_floco') AND table_type = 'BASE TABLE'
        ORDER BY table_schema, table_name
    LOOP
        tabela := r.nome;
        EXECUTE 'SELECT count(*) FROM ' || r.nome INTO linhas;
        RETURN NEXT;
    END LOOP;
END;
$$;

CREATE OR REPLACE PROCEDURE etl.executar_etl()
LANGUAGE plpgsql AS $$
DECLARE
    v_execucao INT;
BEGIN
    SELECT COALESCE(max(execucao), 0) + 1 INTO v_execucao FROM etl.log_carga;

    INSERT INTO etl.log_carga (execucao, tabela, linhas_antes)
    SELECT v_execucao, tabela, linhas FROM etl.contar_linhas();

    CALL etl.carregar_staging();
    CALL etl.carregar_dim_tempo();
    CALL etl.carregar_estrela();
    CALL etl.carregar_floco();

    UPDATE etl.log_carga g
       SET linhas_depois = c.linhas
      FROM etl.contar_linhas() c
     WHERE g.execucao = v_execucao AND g.tabela = c.tabela;

    -- Avanca a marca d'agua da carga incremental
    UPDATE etl.controle
       SET ultima_atualizacao = greatest(ultima_atualizacao,
                                         COALESCE((SELECT max(data_atualizacao) FROM stg.item_venda),
                                                  ultima_atualizacao))
     WHERE processo = 'venda';
END;
$$;

CALL etl.executar_etl();

\echo 'Contagem de linhas antes e depois da carga'
SELECT tabela, linhas_antes, linhas_depois, linhas_depois - linhas_antes AS carregadas
FROM etl.log_carga
WHERE execucao = (SELECT max(execucao) FROM etl.log_carga)
ORDER BY tabela;
