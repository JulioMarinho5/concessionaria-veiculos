-- Parte 4 - ETL: carga do modelo floco de neve (esquema dw_floco)
-- Hierarquias normalizadas: produto -> modelo -> marca e loja/cliente -> cidade -> estado.
-- Servicos apontam para a marca "Servico" e o modelo "Servico" (id_origem 0).

CREATE OR REPLACE PROCEDURE etl.carregar_floco()
LANGUAGE plpgsql AS $$
BEGIN
    -- Estado
    INSERT INTO dw_floco.dim_estado (sigla)
    SELECT DISTINCT e.estado
    FROM (SELECT estado FROM stg.cliente UNION SELECT estado FROM stg.loja) e
    WHERE NOT EXISTS (SELECT 1 FROM dw_floco.dim_estado d WHERE d.sigla = e.estado);

    -- Cidade
    INSERT INTO dw_floco.dim_cidade (nome, sk_estado)
    SELECT DISTINCT c.cidade, e.sk_estado
    FROM (SELECT cidade, estado FROM stg.cliente UNION SELECT cidade, estado FROM stg.loja) c
    JOIN dw_floco.dim_estado e ON e.sigla = c.estado
    WHERE NOT EXISTS (SELECT 1 FROM dw_floco.dim_cidade d
                      WHERE d.nome = c.cidade AND d.sk_estado = e.sk_estado);

    -- Marca
    UPDATE dw_floco.dim_marca d
       SET nome = s.marca
      FROM (SELECT DISTINCT marca_id, marca FROM stg.produto) s
     WHERE d.id_origem = s.marca_id AND d.nome IS DISTINCT FROM s.marca;

    INSERT INTO dw_floco.dim_marca (id_origem, nome)
    SELECT DISTINCT s.marca_id, s.marca
    FROM stg.produto s
    WHERE NOT EXISTS (SELECT 1 FROM dw_floco.dim_marca d WHERE d.id_origem = s.marca_id);

    -- Modelo
    UPDATE dw_floco.dim_modelo d
       SET nome = s.modelo, categoria = s.categoria, sk_marca = m.sk_marca
      FROM (SELECT DISTINCT modelo_id, modelo, categoria, marca_id FROM stg.produto) s
      JOIN dw_floco.dim_marca m ON m.id_origem = s.marca_id
     WHERE d.id_origem = s.modelo_id
       AND (d.nome, d.categoria, d.sk_marca) IS DISTINCT FROM (s.modelo, s.categoria, m.sk_marca);

    INSERT INTO dw_floco.dim_modelo (id_origem, nome, categoria, sk_marca)
    SELECT DISTINCT s.modelo_id, s.modelo, s.categoria, m.sk_marca
    FROM stg.produto s
    JOIN dw_floco.dim_marca m ON m.id_origem = s.marca_id
    WHERE NOT EXISTS (SELECT 1 FROM dw_floco.dim_modelo d WHERE d.id_origem = s.modelo_id);

    -- Produto
    UPDATE dw_floco.dim_produto d
       SET nome = s.nome, cor = s.cor, ano_fabricacao = s.ano_fabricacao, sk_modelo = mo.sk_modelo
      FROM stg.produto s
      JOIN dw_floco.dim_modelo mo ON mo.id_origem = s.modelo_id
     WHERE d.tipo_item = s.tipo_item AND d.id_origem = s.id
       AND (d.nome, d.cor, d.ano_fabricacao, d.sk_modelo)
           IS DISTINCT FROM (s.nome, s.cor, s.ano_fabricacao, mo.sk_modelo);

    INSERT INTO dw_floco.dim_produto (tipo_item, id_origem, nome, cor, ano_fabricacao, sk_modelo)
    SELECT s.tipo_item, s.id, s.nome, s.cor, s.ano_fabricacao, mo.sk_modelo
    FROM stg.produto s
    JOIN dw_floco.dim_modelo mo ON mo.id_origem = s.modelo_id
    WHERE NOT EXISTS (SELECT 1 FROM dw_floco.dim_produto d
                      WHERE d.tipo_item = s.tipo_item AND d.id_origem = s.id);

    -- Loja
    UPDATE dw_floco.dim_loja d
       SET nome = s.nome, sk_cidade = ci.sk_cidade
      FROM stg.loja s
      JOIN dw_floco.dim_estado e ON e.sigla = s.estado
      JOIN dw_floco.dim_cidade ci ON ci.nome = s.cidade AND ci.sk_estado = e.sk_estado
     WHERE d.id_origem = s.id
       AND (d.nome, d.sk_cidade) IS DISTINCT FROM (s.nome, ci.sk_cidade);

    INSERT INTO dw_floco.dim_loja (id_origem, nome, sk_cidade)
    SELECT s.id, s.nome, ci.sk_cidade
    FROM stg.loja s
    JOIN dw_floco.dim_estado e ON e.sigla = s.estado
    JOIN dw_floco.dim_cidade ci ON ci.nome = s.cidade AND ci.sk_estado = e.sk_estado
    WHERE NOT EXISTS (SELECT 1 FROM dw_floco.dim_loja d WHERE d.id_origem = s.id);

    -- Vendedor
    UPDATE dw_floco.dim_vendedor d
       SET nome = s.nome, matricula = s.matricula, cargo = s.cargo
      FROM stg.vendedor s
     WHERE d.id_origem = s.id
       AND (d.nome, d.matricula, d.cargo) IS DISTINCT FROM (s.nome, s.matricula, s.cargo);

    INSERT INTO dw_floco.dim_vendedor (id_origem, nome, matricula, cargo)
    SELECT s.id, s.nome, s.matricula, s.cargo
    FROM stg.vendedor s
    WHERE NOT EXISTS (SELECT 1 FROM dw_floco.dim_vendedor d WHERE d.id_origem = s.id);

    -- Forma de pagamento
    INSERT INTO dw_floco.dim_forma_pagamento (codigo, descricao)
    SELECT s.codigo, s.descricao
    FROM stg.forma_pagamento s
    WHERE NOT EXISTS (SELECT 1 FROM dw_floco.dim_forma_pagamento d WHERE d.codigo = s.codigo);

    -- Cliente, SCD tipo 2 pela cidade
    UPDATE dw_floco.dim_cliente d
       SET data_fim = current_date, atual = FALSE
      FROM stg.cliente s
      JOIN dw_floco.dim_estado e ON e.sigla = s.estado
      JOIN dw_floco.dim_cidade ci ON ci.nome = s.cidade AND ci.sk_estado = e.sk_estado
     WHERE d.id_origem = s.id AND d.atual
       AND d.sk_cidade <> ci.sk_cidade;

    UPDATE dw_floco.dim_cliente d
       SET nome = s.nome
      FROM stg.cliente s
     WHERE d.id_origem = s.id AND d.atual AND d.nome IS DISTINCT FROM s.nome;

    INSERT INTO dw_floco.dim_cliente (id_origem, nome, data_cadastro, data_inicio, data_fim, atual, sk_cidade)
    SELECT s.id, s.nome, s.data_cadastro,
           CASE WHEN EXISTS (SELECT 1 FROM dw_floco.dim_cliente h WHERE h.id_origem = s.id)
                THEN current_date
                ELSE COALESCE(s.data_cadastro, current_date) END,
           NULL, TRUE, ci.sk_cidade
    FROM stg.cliente s
    JOIN dw_floco.dim_estado e ON e.sigla = s.estado
    JOIN dw_floco.dim_cidade ci ON ci.nome = s.cidade AND ci.sk_estado = e.sk_estado
    WHERE NOT EXISTS (SELECT 1 FROM dw_floco.dim_cliente d WHERE d.id_origem = s.id AND d.atual);

    -- Fato
    INSERT INTO dw_floco.fato_venda
        (id_item_venda, sk_tempo, sk_produto, sk_cliente, sk_vendedor, sk_loja, sk_forma_pagamento,
         id_venda, quantidade, valor_bruto, desconto, valor_liquido)
    SELECT i.id_item_venda, t.sk_tempo, p.sk_produto, c.sk_cliente, v.sk_vendedor, l.sk_loja,
           f.sk_forma_pagamento, i.id_venda, i.quantidade, i.valor_bruto, i.desconto, i.valor_liquido
    FROM stg.item_venda i
    JOIN dw_floco.dim_tempo t            ON t.data = i.data_venda
    JOIN dw_floco.dim_produto p          ON p.tipo_item = i.tipo_item AND p.id_origem = i.produto_id
    JOIN dw_floco.dim_cliente c          ON c.id_origem = i.cliente_id AND c.atual
    JOIN dw_floco.dim_vendedor v         ON v.id_origem = i.vendedor_id
    JOIN dw_floco.dim_loja l             ON l.id_origem = i.loja_id
    JOIN dw_floco.dim_forma_pagamento f  ON f.codigo = i.forma_pagamento
    WHERE NOT EXISTS (SELECT 1 FROM dw_floco.fato_venda x WHERE x.id_item_venda = i.id_item_venda);
END;
$$;
