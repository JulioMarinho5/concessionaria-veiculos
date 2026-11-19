CREATE TABLE pessoa (
    id                BIGSERIAL     PRIMARY KEY,
    nome              VARCHAR(120)  NOT NULL,
    cpf               VARCHAR(14)   NOT NULL UNIQUE,
    telefone          VARCHAR(20),
    logradouro        VARCHAR(150),
    cidade            VARCHAR(80),
    estado            CHAR(2),
    data_atualizacao  TIMESTAMP     NOT NULL
);

CREATE TABLE cliente (
    id             BIGINT PRIMARY KEY REFERENCES pessoa(id),
    data_cadastro  DATE
);

CREATE TABLE loja (
    id          BIGSERIAL     PRIMARY KEY,
    nome        VARCHAR(100)  NOT NULL,
    logradouro  VARCHAR(150),
    cidade      VARCHAR(80),
    estado      CHAR(2)
);

CREATE TABLE funcionario (
    id         BIGINT       PRIMARY KEY REFERENCES pessoa(id),
    matricula  VARCHAR(20)  UNIQUE,
    cargo      VARCHAR(40),
    loja_id    BIGINT       NOT NULL REFERENCES loja(id)
);

CREATE TABLE documento_cliente (
    id            BIGSERIAL    PRIMARY KEY,
    cliente_id    BIGINT       NOT NULL UNIQUE REFERENCES cliente(id),
    numero_cnh    VARCHAR(11),
    validade_cnh  DATE
);

CREATE TABLE servico (
    id            BIGSERIAL     PRIMARY KEY,
    nome          VARCHAR(80)   NOT NULL,
    preco_tabela  NUMERIC(12,2)
);

CREATE TABLE marca (
    id    BIGSERIAL    PRIMARY KEY,
    nome  VARCHAR(60)  NOT NULL UNIQUE
);

CREATE TABLE modelo (
    id         BIGSERIAL    PRIMARY KEY,
    nome       VARCHAR(60)  NOT NULL,
    categoria  VARCHAR(30),
    marca_id   BIGINT       NOT NULL REFERENCES marca(id)
);

CREATE TABLE veiculo (
    id              BIGSERIAL     PRIMARY KEY,
    chassi          VARCHAR(17)   NOT NULL UNIQUE,
    cor             VARCHAR(30),
    ano_fabricacao  INT,
    preco_tabela    NUMERIC(12,2),
    modelo_id       BIGINT        NOT NULL REFERENCES modelo(id)
);

CREATE TABLE venda (
    id                BIGSERIAL    PRIMARY KEY,
    data_hora         TIMESTAMP    NOT NULL,
    forma_pagamento   VARCHAR(20)  NOT NULL,
    data_atualizacao  TIMESTAMP    NOT NULL,
    cliente_id        BIGINT       NOT NULL REFERENCES cliente(id),
    vendedor_id       BIGINT       NOT NULL REFERENCES funcionario(id),
    loja_id           BIGINT       NOT NULL REFERENCES loja(id)
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
