-- Parte 4 - ETL: geracao da dimensao Tempo nos dois modelos
-- Gera um dia por linha, do primeiro ao ultimo ano com vendas (ou o ano atual).
-- Chave substituta no formato AAAAMMDD.

CREATE OR REPLACE PROCEDURE etl.carregar_dim_tempo()
LANGUAGE plpgsql AS $$
DECLARE
    v_inicio DATE;
    v_fim    DATE;
    v_esquema TEXT;
BEGIN
    SELECT date_trunc('year', min(data_hora))::date,
           (date_trunc('year', greatest(max(data_hora), now())) + INTERVAL '1 year - 1 day')::date
      INTO v_inicio, v_fim
    FROM venda;

    IF v_inicio IS NULL THEN
        RETURN;
    END IF;

    FOREACH v_esquema IN ARRAY ARRAY['dw_estrela', 'dw_floco'] LOOP
        EXECUTE format($f$
            INSERT INTO %I.dim_tempo
            SELECT to_char(d, 'YYYYMMDD')::int,
                   d,
                   extract(day FROM d),
                   extract(month FROM d),
                   (ARRAY['Janeiro','Fevereiro','Março','Abril','Maio','Junho','Julho',
                          'Agosto','Setembro','Outubro','Novembro','Dezembro'])[extract(month FROM d)::int],
                   extract(quarter FROM d),
                   CASE WHEN extract(month FROM d) <= 6 THEN 1 ELSE 2 END,
                   extract(year FROM d),
                   (ARRAY['Segunda-feira','Terça-feira','Quarta-feira','Quinta-feira',
                          'Sexta-feira','Sábado','Domingo'])[extract(isodow FROM d)::int]
            FROM generate_series($1, $2, INTERVAL '1 day') AS g(d0),
                 LATERAL (SELECT d0::date AS d) x
            WHERE NOT EXISTS (SELECT 1 FROM %I.dim_tempo t WHERE t.data = d)
        $f$, v_esquema, v_esquema)
        USING v_inicio, v_fim;
    END LOOP;
END;
$$;
