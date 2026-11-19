CREATE SCHEMA dw_estrela;

CREATE TABLE dw_estrela.dim_tempo (
    sk_tempo    INT          PRIMARY KEY,
    data        DATE         NOT NULL,
    dia         SMALLINT     NOT NULL,
    mes         SMALLINT     NOT NULL,
    nome_mes    VARCHAR(15)  NOT NULL,
    trimestre   SMALLINT     NOT NULL,
    semestre    SMALLINT     NOT NULL,
    ano         SMALLINT     NOT NULL,
    dia_semana  VARCHAR(15)  NOT NULL
);

CREATE TABLE dw_estrela.dim_produto (
    sk_produto      SERIAL       PRIMARY KEY,
    tipo_item       VARCHAR(10)  NOT NULL,
    id_origem       BIGINT       NOT NULL,
    nome            VARCHAR(80)  NOT NULL,
    marca           VARCHAR(60)  NOT NULL,
    modelo          VARCHAR(60)  NOT NULL,
    categoria       VARCHAR(30)  NOT NULL,
    cor             VARCHAR(30),
    ano_fabricacao  INT
);

CREATE TABLE dw_estrela.dim_cliente (
    sk_cliente      SERIAL        PRIMARY KEY,
    id_origem       BIGINT        NOT NULL,
    nome            VARCHAR(120)  NOT NULL,
    cidade          VARCHAR(80)   NOT NULL,
    estado          CHAR(2)       NOT NULL,
    data_cadastro   DATE,
    data_inicio     DATE          NOT NULL,
    data_fim        DATE,
    atual           BOOLEAN       NOT NULL
);

CREATE TABLE dw_estrela.dim_loja (
    sk_loja    SERIAL        PRIMARY KEY,
    id_origem  BIGINT        NOT NULL,
    nome       VARCHAR(100)  NOT NULL,
    cidade     VARCHAR(80)   NOT NULL,
    estado     CHAR(2)       NOT NULL
);

CREATE TABLE dw_estrela.dim_vendedor (
    sk_vendedor  SERIAL        PRIMARY KEY,
    id_origem    BIGINT        NOT NULL,
    nome         VARCHAR(120)  NOT NULL,
    matricula    VARCHAR(20),
    cargo        VARCHAR(40)
);

CREATE TABLE dw_estrela.dim_forma_pagamento (
    sk_forma_pagamento  SERIAL       PRIMARY KEY,
    codigo              VARCHAR(20)  NOT NULL,
    descricao           VARCHAR(30)  NOT NULL
);

CREATE TABLE dw_estrela.fato_venda (
    id_item_venda       BIGINT         PRIMARY KEY,
    sk_tempo            INT            NOT NULL REFERENCES dw_estrela.dim_tempo(sk_tempo),
    sk_produto          INT            NOT NULL REFERENCES dw_estrela.dim_produto(sk_produto),
    sk_cliente          INT            NOT NULL REFERENCES dw_estrela.dim_cliente(sk_cliente),
    sk_vendedor         INT            NOT NULL REFERENCES dw_estrela.dim_vendedor(sk_vendedor),
    sk_loja             INT            NOT NULL REFERENCES dw_estrela.dim_loja(sk_loja),
    sk_forma_pagamento  INT            NOT NULL REFERENCES dw_estrela.dim_forma_pagamento(sk_forma_pagamento),
    id_venda            BIGINT         NOT NULL,
    quantidade          INT            NOT NULL,
    valor_bruto         NUMERIC(12,2)  NOT NULL,
    desconto            NUMERIC(12,2)  NOT NULL,
    valor_liquido       NUMERIC(12,2)  NOT NULL
);
