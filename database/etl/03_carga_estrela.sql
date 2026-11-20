-- Parte 4 - ETL: carga do modelo estrela (esquema dw_estrela)
-- Dimensoes: insere o que e novo e atualiza atributos (tipo 1).
-- Cliente: SCD tipo 2 na mudanca de cidade ou estado.
-- Fato: so os itens que ainda nao estao no DW.

CREATE OR REPLACE PROCEDURE etl.carregar_estrela()
LANGUAGE plpgsql AS $$
BEGIN
    -- Loja
    UPDATE dw_estrela.dim_loja d
       SET nome = s.nome, cidade = s.cidade, estado = s.estado
      FROM stg.loja s
     WHERE d.id_origem = s.id
       AND (d.nome, d.cidade, d.estado) IS DISTINCT FROM (s.nome, s.cidade, s.estado);

    INSERT INTO dw_estrela.dim_loja (id_origem, nome, cidade, estado)
    SELECT s.id, s.nome, s.cidade, s.estado
    FROM stg.loja s
    WHERE NOT EXISTS (SELECT 1 FROM dw_estrela.dim_loja d WHERE d.id_origem = s.id);

    -- Vendedor
    UPDATE dw_estrela.dim_vendedor d
       SET nome = s.nome, matricula = s.matricula, cargo = s.cargo
      FROM stg.vendedor s
     WHERE d.id_origem = s.id
       AND (d.nome, d.matricula, d.cargo) IS DISTINCT FROM (s.nome, s.matricula, s.cargo);

    INSERT INTO dw_estrela.dim_vendedor (id_origem, nome, matricula, cargo)
    SELECT s.id, s.nome, s.matricula, s.cargo
    FROM stg.vendedor s
    WHERE NOT EXISTS (SELECT 1 FROM dw_estrela.dim_vendedor d WHERE d.id_origem = s.id);

    -- Forma de pagamento
    INSERT INTO dw_estrela.dim_forma_pagamento (codigo, descricao)
    SELECT s.codigo, s.descricao
    FROM stg.forma_pagamento s
    WHERE NOT EXISTS (SELECT 1 FROM dw_estrela.dim_forma_pagamento d WHERE d.codigo = s.codigo);

    -- Produto (veiculos e servicos)
    UPDATE dw_estrela.dim_produto d
       SET nome = s.nome, marca = s.marca, modelo = s.modelo, categoria = s.categoria,
           cor = s.cor, ano_fabricacao = s.ano_fabricacao
      FROM stg.produto s
     WHERE d.tipo_item = s.tipo_item AND d.id_origem = s.id
       AND (d.nome, d.marca, d.modelo, d.categoria, d.cor, d.ano_fabricacao)
           IS DISTINCT FROM (s.nome, s.marca, s.modelo, s.categoria, s.cor, s.ano_fabricacao);

    INSERT INTO dw_estrela.dim_produto (tipo_item, id_origem, nome, marca, modelo, categoria, cor, ano_fabricacao)
    SELECT s.tipo_item, s.id, s.nome, s.marca, s.modelo, s.categoria, s.cor, s.ano_fabricacao
    FROM stg.produto s
    WHERE NOT EXISTS (SELECT 1 FROM dw_estrela.dim_produto d
                      WHERE d.tipo_item = s.tipo_item AND d.id_origem = s.id);

    -- Cliente, SCD tipo 2: fecha a versao atual quando a cidade ou o estado mudam
    UPDATE dw_estrela.dim_cliente d
       SET data_fim = current_date, atual = FALSE
      FROM stg.cliente s
     WHERE d.id_origem = s.id AND d.atual
       AND (d.cidade, d.estado) IS DISTINCT FROM (s.cidade, s.estado);

    -- Nome e tratado como tipo 1 (corrige a versao atual)
    UPDATE dw_estrela.dim_cliente d
       SET nome = s.nome
      FROM stg.cliente s
     WHERE d.id_origem = s.id AND d.atual AND d.nome IS DISTINCT FROM s.nome;

    -- Abre a versao nova (ou a primeira, para cliente novo)
    INSERT INTO dw_estrela.dim_cliente (id_origem, nome, cidade, estado, data_cadastro, data_inicio, data_fim, atual)
    SELECT s.id, s.nome, s.cidade, s.estado, s.data_cadastro,
           CASE WHEN EXISTS (SELECT 1 FROM dw_estrela.dim_cliente h WHERE h.id_origem = s.id)
                THEN current_date
                ELSE COALESCE(s.data_cadastro, current_date) END,
           NULL, TRUE
    FROM stg.cliente s
    WHERE NOT EXISTS (SELECT 1 FROM dw_estrela.dim_cliente d WHERE d.id_origem = s.id AND d.atual);

    -- Fato: busca as chaves substitutas; cliente na versao atual
    INSERT INTO dw_estrela.fato_venda
        (id_item_venda, sk_tempo, sk_produto, sk_cliente, sk_vendedor, sk_loja, sk_forma_pagamento,
         id_venda, quantidade, valor_bruto, desconto, valor_liquido)
    SELECT i.id_item_venda, t.sk_tempo, p.sk_produto, c.sk_cliente, v.sk_vendedor, l.sk_loja,
           f.sk_forma_pagamento, i.id_venda, i.quantidade, i.valor_bruto, i.desconto, i.valor_liquido
    FROM stg.item_venda i
    JOIN dw_estrela.dim_tempo t            ON t.data = i.data_venda
    JOIN dw_estrela.dim_produto p          ON p.tipo_item = i.tipo_item AND p.id_origem = i.produto_id
    JOIN dw_estrela.dim_cliente c          ON c.id_origem = i.cliente_id AND c.atual
    JOIN dw_estrela.dim_vendedor v         ON v.id_origem = i.vendedor_id
    JOIN dw_estrela.dim_loja l             ON l.id_origem = i.loja_id
    JOIN dw_estrela.dim_forma_pagamento f  ON f.codigo = i.forma_pagamento
    WHERE NOT EXISTS (SELECT 1 FROM dw_estrela.fato_venda x WHERE x.id_item_venda = i.id_item_venda);
END;
$$;
