-- Parte 4 - ETL: area de staging, controle da carga incremental e limpeza dos dados
-- Le o OLTP (esquema public) e grava os dados ja padronizados no esquema stg.

CREATE SCHEMA IF NOT EXISTS etl;
CREATE SCHEMA IF NOT EXISTS stg;

-- Marca d'agua da carga incremental: ultima venda.data_atualizacao ja levada ao DW
CREATE TABLE IF NOT EXISTS etl.controle (
    processo            VARCHAR(30)  PRIMARY KEY,
    ultima_atualizacao  TIMESTAMP    NOT NULL
);
INSERT INTO etl.controle VALUES ('venda', '1900-01-01') ON CONFLICT DO NOTHING;

-- Contagem de linhas antes e depois de cada execucao
CREATE TABLE IF NOT EXISTS etl.log_carga (
    execucao       INT          NOT NULL,
    data_execucao  TIMESTAMP    NOT NULL DEFAULT now(),
    tabela         VARCHAR(60)  NOT NULL,
    linhas_antes   BIGINT       NOT NULL,
    linhas_depois  BIGINT,
    PRIMARY KEY (execucao, tabela)
);

-- Padronizacao de texto: tira espacos nas pontas e repetidos; vazio vira nulo
CREATE OR REPLACE FUNCTION etl.limpar(t TEXT) RETURNS TEXT
LANGUAGE sql IMMUTABLE AS $$
    SELECT NULLIF(trim(regexp_replace(t, '\s+', ' ', 'g')), '')
$$;

-- Nomes de pessoas e cidades: limpar + iniciais maiusculas, preposicoes em minusculas
CREATE OR REPLACE FUNCTION etl.nome_proprio(t TEXT) RETURNS TEXT
LANGUAGE sql IMMUTABLE AS $$
    SELECT replace(replace(replace(replace(replace(replace(
               initcap(etl.limpar(t)),
               ' De ', ' de '), ' Da ', ' da '), ' Do ', ' do '),
               ' Dos ', ' dos '), ' Das ', ' das '), ' E ', ' e ')
$$;

CREATE TABLE IF NOT EXISTS stg.cliente (
    id             BIGINT,
    nome           VARCHAR(120),
    cidade         VARCHAR(80),
    estado         CHAR(2),
    data_cadastro  DATE
);

CREATE TABLE IF NOT EXISTS stg.loja (
    id      BIGINT,
    nome    VARCHAR(100),
    cidade  VARCHAR(80),
    estado  CHAR(2)
);

CREATE TABLE IF NOT EXISTS stg.vendedor (
    id         BIGINT,
    nome       VARCHAR(120),
    matricula  VARCHAR(20),
    cargo      VARCHAR(40)
);

CREATE TABLE IF NOT EXISTS stg.produto (
    tipo_item       VARCHAR(10),
    id              BIGINT,
    nome            VARCHAR(80),
    marca_id        BIGINT,
    marca           VARCHAR(60),
    modelo_id       BIGINT,
    modelo          VARCHAR(60),
    categoria       VARCHAR(30),
    cor             VARCHAR(30),
    ano_fabricacao  INT
);

CREATE TABLE IF NOT EXISTS stg.forma_pagamento (
    codigo     VARCHAR(20),
    descricao  VARCHAR(30)
);

CREATE TABLE IF NOT EXISTS stg.item_venda (
    id_item_venda     BIGINT,
    id_venda          BIGINT,
    data_venda        DATE,
    data_atualizacao  TIMESTAMP,
    cliente_id        BIGINT,
    vendedor_id       BIGINT,
    loja_id           BIGINT,
    forma_pagamento   VARCHAR(20),
    tipo_item         VARCHAR(10),
    produto_id        BIGINT,
    quantidade        INT,
    valor_bruto       NUMERIC(12,2),
    desconto          NUMERIC(12,2),
    valor_liquido     NUMERIC(12,2)
);

CREATE OR REPLACE PROCEDURE etl.carregar_staging()
LANGUAGE plpgsql AS $$
DECLARE
    v_ultima TIMESTAMP;
BEGIN
    TRUNCATE stg.cliente, stg.loja, stg.vendedor, stg.produto,
             stg.forma_pagamento, stg.item_venda;

    -- Clientes: nulos de cidade e estado viram "Nao Informada" e "NI"
    INSERT INTO stg.cliente
    SELECT p.id,
           COALESCE(etl.nome_proprio(p.nome), 'Não Informado'),
           COALESCE(etl.nome_proprio(p.cidade), 'Não Informada'),
           COALESCE(upper(etl.limpar(p.estado)), 'NI'),
           c.data_cadastro
    FROM cliente c
    JOIN pessoa p ON p.id = c.id;

    INSERT INTO stg.loja
    SELECT l.id,
           COALESCE(etl.limpar(l.nome), 'Não Informada'),
           COALESCE(etl.nome_proprio(l.cidade), 'Não Informada'),
           COALESCE(upper(etl.limpar(l.estado)), 'NI')
    FROM loja l;

    INSERT INTO stg.vendedor
    SELECT f.id,
           COALESCE(etl.nome_proprio(p.nome), 'Não Informado'),
           upper(etl.limpar(f.matricula)),
           COALESCE(etl.nome_proprio(f.cargo), 'Não Informado')
    FROM funcionario f
    JOIN pessoa p ON p.id = f.id;

    -- Veiculos
    INSERT INTO stg.produto
    SELECT 'VEICULO', v.id,
           left(etl.limpar(ma.nome) || ' ' || etl.limpar(mo.nome), 80),
           ma.id, etl.limpar(ma.nome),
           mo.id, etl.limpar(mo.nome),
           COALESCE(etl.limpar(mo.categoria), 'Não Informada'),
           COALESCE(etl.nome_proprio(v.cor), 'Não Informada'),
           v.ano_fabricacao
    FROM veiculo v
    JOIN modelo mo ON mo.id = v.modelo_id
    JOIN marca ma ON ma.id = mo.marca_id;

    -- Servicos: membro "Servico" (marca e modelo de id 0)
    INSERT INTO stg.produto
    SELECT 'SERVICO', s.id,
           COALESCE(etl.limpar(s.nome), 'Serviço Não Informado'),
           0, 'Serviço', 0, 'Serviço', 'Serviço', NULL, NULL
    FROM servico s;

    INSERT INTO stg.forma_pagamento
    SELECT DISTINCT upper(etl.limpar(forma_pagamento)),
           initcap(replace(lower(etl.limpar(forma_pagamento)), '_', ' '))
    FROM venda
    WHERE etl.limpar(forma_pagamento) IS NOT NULL;

    -- Itens: so as vendas novas ou alteradas desde a ultima carga (incremental)
    SELECT ultima_atualizacao INTO v_ultima FROM etl.controle WHERE processo = 'venda';

    INSERT INTO stg.item_venda
    SELECT i.id, v.id, v.data_hora::date, v.data_atualizacao,
           v.cliente_id, v.vendedor_id, v.loja_id,
           upper(etl.limpar(v.forma_pagamento)),
           CASE WHEN i.veiculo_id IS NOT NULL THEN 'VEICULO' ELSE 'SERVICO' END,
           COALESCE(i.veiculo_id, i.servico_id),
           i.quantidade,
           -- medidas derivadas
           i.quantidade * i.preco_unitario,
           COALESCE(i.desconto, 0),
           i.quantidade * i.preco_unitario - COALESCE(i.desconto, 0)
    FROM venda v
    JOIN item_venda i ON i.venda_id = v.id
    WHERE v.data_atualizacao > v_ultima
      AND COALESCE(i.veiculo_id, i.servico_id) IS NOT NULL;
END;
$$;
