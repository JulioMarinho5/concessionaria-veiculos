CREATE TABLE pessoa (
    id                BIGSERIAL     PRIMARY KEY,
    nome              VARCHAR       NOT NULL,
    cpf               VARCHAR       NOT NULL UNIQUE,
    telefone          VARCHAR,
    logradouro        VARCHAR,
    cidade            VARCHAR,
    estado            CHAR(2),
    data_atualizacao  TIMESTAMP
);

CREATE TABLE cliente (
    id             BIGINT PRIMARY KEY REFERENCES pessoa(id),
    data_cadastro  DATE
);

CREATE TABLE loja (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR   NOT NULL,
    logradouro  VARCHAR,
    cidade      VARCHAR,
    estado      CHAR(2)
);

CREATE TABLE funcionario (
    id         BIGINT  PRIMARY KEY REFERENCES pessoa(id),
    matricula  VARCHAR,
    cargo      VARCHAR,
    loja_id    BIGINT  NOT NULL REFERENCES loja(id)
);

CREATE TABLE documento_cliente (
    id            BIGSERIAL PRIMARY KEY,
    cliente_id    BIGINT    NOT NULL UNIQUE REFERENCES cliente(id),
    numero_cnh    VARCHAR,
    validade_cnh  DATE
);

CREATE TABLE servico (
    id            BIGSERIAL     PRIMARY KEY,
    nome          VARCHAR       NOT NULL,
    preco_tabela  NUMERIC(12,2)
);

CREATE TABLE marca (
    id    BIGSERIAL PRIMARY KEY,
    nome  VARCHAR   NOT NULL
);

CREATE TABLE modelo (
    id         BIGSERIAL PRIMARY KEY,
    nome       VARCHAR   NOT NULL,
    categoria  VARCHAR,
    marca_id   BIGINT    NOT NULL REFERENCES marca(id)
);

CREATE TABLE veiculo (
    id              BIGSERIAL     PRIMARY KEY,
    chassi          VARCHAR       NOT NULL UNIQUE,
    cor             VARCHAR,
    ano_fabricacao  INT,
    preco_tabela    NUMERIC(12,2),
    modelo_id       BIGINT        NOT NULL REFERENCES modelo(id)
);

CREATE TABLE venda (
    id                BIGSERIAL PRIMARY KEY,
    data_hora         TIMESTAMP NOT NULL,
    forma_pagamento   VARCHAR   NOT NULL,
    data_atualizacao  TIMESTAMP,
    cliente_id        BIGINT    NOT NULL REFERENCES cliente(id),
    vendedor_id       BIGINT    NOT NULL REFERENCES funcionario(id),
    loja_id           BIGINT    NOT NULL REFERENCES loja(id)
);

CREATE TABLE item_venda (
    id              BIGSERIAL     PRIMARY KEY,
    venda_id        BIGINT        NOT NULL REFERENCES venda(id),
    veiculo_id      BIGINT        REFERENCES veiculo(id),
    servico_id      BIGINT        REFERENCES servico(id),
    quantidade      INT           NOT NULL,
    preco_unitario  NUMERIC(12,2) NOT NULL,
    desconto        NUMERIC(12,2)
);
