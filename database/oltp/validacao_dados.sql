-- =====================================================================
-- validacao_dados.sql - Validacao da carga do OLTP (Concessionaria)
-- Integrante 4 - Engenheiro de ETL
--
-- Rodar depois de database/oltp/carga_dados.sql (ou de carga/CargaDados.java):
--   psql -U postgres -d concessionaria -f validacao_dados.sql
--
-- Parte 1: contagens por tabela.
-- Parte 2: requisitos minimos do enunciado (item 4.2.6).
-- Parte 3: integridade e regras de negocio. Cada linha mostra quantos
--          registros violam a regra; o esperado e 0 em todas.
-- Parte 4: consultas de conferencia dos relacionamentos (amostras).
-- Somente SELECT: o script nao altera nada no banco.
-- =====================================================================


-- ---------------------------------------------------------------------
-- PARTE 1 - Linhas por tabela
-- ---------------------------------------------------------------------
SELECT 'pessoa'            AS tabela, COUNT(*) AS linhas FROM pessoa
UNION ALL SELECT 'cliente',           COUNT(*) FROM cliente
UNION ALL SELECT 'funcionario',       COUNT(*) FROM funcionario
UNION ALL SELECT 'documento_cliente', COUNT(*) FROM documento_cliente
UNION ALL SELECT 'loja',              COUNT(*) FROM loja
UNION ALL SELECT 'marca',             COUNT(*) FROM marca
UNION ALL SELECT 'modelo',            COUNT(*) FROM modelo
UNION ALL SELECT 'veiculo',           COUNT(*) FROM veiculo
UNION ALL SELECT 'servico',           COUNT(*) FROM servico
UNION ALL SELECT 'venda',             COUNT(*) FROM venda
UNION ALL SELECT 'item_venda',        COUNT(*) FROM item_venda;


-- ---------------------------------------------------------------------
-- PARTE 2 - Requisitos minimos do enunciado
-- ---------------------------------------------------------------------
SELECT requisito, minimo, encontrado,
       CASE WHEN encontrado >= minimo THEN 'OK' ELSE 'FALHOU' END AS status
FROM (
  SELECT 'Clientes'                         AS requisito, 200  AS minimo, (SELECT COUNT(*) FROM cliente) AS encontrado
  UNION ALL
  SELECT 'Itens (veiculos + servicos)',      100,  (SELECT COUNT(*) FROM veiculo) + (SELECT COUNT(*) FROM servico)
  UNION ALL
  SELECT 'Lojas',                            5,    (SELECT COUNT(*) FROM loja)
  UNION ALL
  SELECT 'Vendas',                           2000, (SELECT COUNT(*) FROM venda)
  UNION ALL
  SELECT 'Anos distintos com vendas',        2,    (SELECT COUNT(DISTINCT EXTRACT(YEAR FROM data_hora)) FROM venda)
) r;

-- Periodo coberto pelas vendas
SELECT MIN(data_hora) AS primeira_venda, MAX(data_hora) AS ultima_venda,
       MAX(data_hora)::date - MIN(data_hora)::date AS dias_cobertos
FROM venda;


-- ---------------------------------------------------------------------
-- PARTE 3 - Integridade e regras de negocio (esperado: 0 em tudo)
-- ---------------------------------------------------------------------
SELECT verificacao, problemas,
       CASE WHEN problemas = 0 THEN 'OK' ELSE 'VERIFICAR' END AS status
FROM (
  -- Heranca JOINED: toda pessoa e cliente OU funcionario (nunca os dois, nunca nenhum)
  SELECT '01 Pessoa sem subtipo (nem cliente nem funcionario)' AS verificacao,
         (SELECT COUNT(*) FROM pessoa p
           WHERE NOT EXISTS (SELECT 1 FROM cliente c WHERE c.id = p.id)
             AND NOT EXISTS (SELECT 1 FROM funcionario f WHERE f.id = p.id)) AS problemas
  UNION ALL
  SELECT '02 Pessoa que e cliente e funcionario ao mesmo tempo',
         (SELECT COUNT(*) FROM cliente c JOIN funcionario f ON f.id = c.id)
  UNION ALL
  SELECT '03 Cliente/funcionario sem linha em pessoa',
         (SELECT COUNT(*) FROM cliente c WHERE NOT EXISTS (SELECT 1 FROM pessoa p WHERE p.id = c.id))
       + (SELECT COUNT(*) FROM funcionario f WHERE NOT EXISTS (SELECT 1 FROM pessoa p WHERE p.id = f.id))

  -- Chaves estrangeiras (registros orfaos)
  UNION ALL
  SELECT '04 Funcionario sem loja valida',
         (SELECT COUNT(*) FROM funcionario f LEFT JOIN loja l ON l.id = f.loja_id WHERE l.id IS NULL)
  UNION ALL
  SELECT '05 Modelo sem marca valida',
         (SELECT COUNT(*) FROM modelo m LEFT JOIN marca ma ON ma.id = m.marca_id WHERE ma.id IS NULL)
  UNION ALL
  SELECT '06 Veiculo sem modelo valido',
         (SELECT COUNT(*) FROM veiculo v LEFT JOIN modelo m ON m.id = v.modelo_id WHERE m.id IS NULL)
  UNION ALL
  SELECT '07 Venda sem cliente, vendedor ou loja validos',
         (SELECT COUNT(*) FROM venda v
            LEFT JOIN cliente c     ON c.id = v.cliente_id
            LEFT JOIN funcionario f ON f.id = v.vendedor_id
            LEFT JOIN loja l        ON l.id = v.loja_id
          WHERE c.id IS NULL OR f.id IS NULL OR l.id IS NULL)
  UNION ALL
  SELECT '08 Item sem venda valida',
         (SELECT COUNT(*) FROM item_venda i LEFT JOIN venda v ON v.id = i.venda_id WHERE v.id IS NULL)
  UNION ALL
  SELECT '09 Item apontando para veiculo/servico inexistente',
         (SELECT COUNT(*) FROM item_venda i
            LEFT JOIN veiculo ve ON ve.id = i.veiculo_id
            LEFT JOIN servico s  ON s.id  = i.servico_id
          WHERE (i.veiculo_id IS NOT NULL AND ve.id IS NULL)
             OR (i.servico_id IS NOT NULL AND s.id IS NULL))

  -- @OneToOne Cliente <-> DocumentoCliente
  UNION ALL
  SELECT '10 Documento sem cliente valido',
         (SELECT COUNT(*) FROM documento_cliente d LEFT JOIN cliente c ON c.id = d.cliente_id WHERE c.id IS NULL)
  UNION ALL
  SELECT '11 Cliente com mais de um documento (quebra o 1:1)',
         (SELECT COUNT(*) FROM (SELECT cliente_id FROM documento_cliente
                                GROUP BY cliente_id HAVING COUNT(*) > 1) x)

  -- Regras do ItemVenda (as mesmas do @PrePersist/@PreUpdate da entidade)
  UNION ALL
  SELECT '12 Item com veiculo E servico, ou sem nenhum dos dois',
         (SELECT COUNT(*) FROM item_venda
           WHERE (veiculo_id IS NULL) = (servico_id IS NULL))
  UNION ALL
  SELECT '13 Item de veiculo com quantidade diferente de 1',
         (SELECT COUNT(*) FROM item_venda WHERE veiculo_id IS NOT NULL AND quantidade <> 1)
  UNION ALL
  SELECT '14 Item com quantidade <= 0 ou preco negativo',
         (SELECT COUNT(*) FROM item_venda WHERE quantidade <= 0 OR preco_unitario < 0)
  UNION ALL
  SELECT '15 Desconto negativo ou maior que o valor bruto',
         (SELECT COUNT(*) FROM item_venda
           WHERE COALESCE(desconto, 0) < 0
              OR COALESCE(desconto, 0) > quantidade * preco_unitario)

  -- Regras de negocio da concessionaria
  UNION ALL
  SELECT '16 Veiculo (chassi) vendido mais de uma vez',
         (SELECT COUNT(*) FROM (SELECT veiculo_id FROM item_venda WHERE veiculo_id IS NOT NULL
                                GROUP BY veiculo_id HAVING COUNT(*) > 1) x)
  UNION ALL
  SELECT '17 Venda sem nenhum item',
         (SELECT COUNT(*) FROM venda v WHERE NOT EXISTS (SELECT 1 FROM item_venda i WHERE i.venda_id = v.id))
  UNION ALL
  SELECT '18 Venda com mais de um veiculo',
         (SELECT COUNT(*) FROM (SELECT venda_id FROM item_venda WHERE veiculo_id IS NOT NULL
                                GROUP BY venda_id HAVING COUNT(*) > 1) x)
  UNION ALL
  SELECT '19 Vendedor que nao trabalha na loja da venda',
         (SELECT COUNT(*) FROM venda v JOIN funcionario f ON f.id = v.vendedor_id
           WHERE f.loja_id <> v.loja_id)
  UNION ALL
  SELECT '20 Venda de veiculo feita por quem nao e Vendedor',
         (SELECT COUNT(DISTINCT v.id) FROM venda v
            JOIN item_venda i   ON i.venda_id = v.id AND i.veiculo_id IS NOT NULL
            JOIN funcionario f  ON f.id = v.vendedor_id
          WHERE f.cargo <> 'Vendedor')
  UNION ALL
  SELECT '21 Venda anterior ao cadastro do cliente',
         (SELECT COUNT(*) FROM venda v JOIN cliente c ON c.id = v.cliente_id
           WHERE v.data_hora::date < c.data_cadastro)
  UNION ALL
  SELECT '22 Forma de pagamento fora do enum FormaPagamento',
         (SELECT COUNT(*) FROM venda
           WHERE forma_pagamento NOT IN ('A_VISTA', 'FINANCIAMENTO', 'CARTAO', 'CONSORCIO'))
  UNION ALL
  SELECT '23 Chassi com tamanho diferente de 17',
         (SELECT COUNT(*) FROM veiculo WHERE LENGTH(chassi) <> 17)
  UNION ALL
  SELECT '24 CPF repetido',
         (SELECT COUNT(*) FROM (SELECT cpf FROM pessoa GROUP BY cpf HAVING COUNT(*) > 1) x)

  -- Colunas usadas pelo ETL na carga incremental
  UNION ALL
  SELECT '25 Pessoa ou venda sem data_atualizacao (necessaria ao ETL)',
         (SELECT COUNT(*) FROM pessoa WHERE data_atualizacao IS NULL)
       + (SELECT COUNT(*) FROM venda  WHERE data_atualizacao IS NULL)
  UNION ALL
  SELECT '26 Cliente sem cidade/UF (necessaria ao SCD tipo 2)',
         (SELECT COUNT(*) FROM cliente c JOIN pessoa p ON p.id = c.id
           WHERE p.cidade IS NULL OR p.estado IS NULL)
) v
ORDER BY verificacao;


-- ---------------------------------------------------------------------
-- PARTE 4 - Conferencia dos relacionamentos (amostras)
-- ---------------------------------------------------------------------

-- 4.1 Heranca: quantas pessoas de cada subtipo e cargos dos funcionarios
SELECT 'Cliente' AS tipo, NULL AS cargo, COUNT(*) AS qtd FROM cliente
UNION ALL
SELECT 'Funcionario', cargo, COUNT(*) FROM funcionario GROUP BY cargo
ORDER BY tipo, cargo;

-- 4.2 Loja -> funcionarios (@OneToMany bidirecional)
SELECT l.id, l.nome, l.cidade, COUNT(f.id) AS funcionarios
FROM loja l LEFT JOIN funcionario f ON f.loja_id = l.id
GROUP BY l.id, l.nome, l.cidade
ORDER BY l.id;

-- 4.3 Cliente <-> DocumentoCliente (@OneToOne): clientes com e sem CNH
SELECT CASE WHEN d.id IS NULL THEN 'sem CNH' ELSE 'com CNH' END AS situacao,
       COUNT(*) AS clientes
FROM cliente c LEFT JOIN documento_cliente d ON d.cliente_id = c.id
GROUP BY 1;

-- 4.4 Marca -> Modelo -> Veiculo: estoque e vendidos por marca
SELECT ma.nome AS marca,
       COUNT(DISTINCT mo.id) AS modelos,
       COUNT(ve.id) AS veiculos,
       COUNT(i.id)  AS vendidos,
       COUNT(ve.id) - COUNT(i.id) AS em_estoque
FROM marca ma
JOIN modelo mo       ON mo.marca_id = ma.id
LEFT JOIN veiculo ve ON ve.modelo_id = mo.id
LEFT JOIN item_venda i ON i.veiculo_id = ve.id
GROUP BY ma.nome
ORDER BY vendidos DESC;

-- 4.5 Venda completa: as 5 primeiras vendas com cliente, vendedor, loja e itens
SELECT v.id AS venda, v.data_hora, v.forma_pagamento,
       pc.nome AS cliente, pf.nome AS vendedor, l.nome AS loja,
       COALESCE(ma.nome || ' ' || mo.nome || ' ' || ve.ano_fabricacao, s.nome) AS item,
       i.quantidade, i.preco_unitario, i.desconto,
       i.quantidade * i.preco_unitario - COALESCE(i.desconto, 0) AS valor_liquido
FROM venda v
JOIN pessoa pc ON pc.id = v.cliente_id
JOIN pessoa pf ON pf.id = v.vendedor_id
JOIN loja l    ON l.id  = v.loja_id
JOIN item_venda i    ON i.venda_id = v.id
LEFT JOIN veiculo ve ON ve.id = i.veiculo_id
LEFT JOIN modelo mo  ON mo.id = ve.modelo_id
LEFT JOIN marca ma   ON ma.id = mo.marca_id
LEFT JOIN servico s  ON s.id  = i.servico_id
WHERE v.id <= 5
ORDER BY v.id, i.id;

-- 4.6 Vendas e faturamento liquido por ano (confere a distribuicao no tempo)
SELECT EXTRACT(YEAR FROM v.data_hora) AS ano,
       COUNT(DISTINCT v.id) AS vendas,
       SUM(i.quantidade * i.preco_unitario - COALESCE(i.desconto, 0)) AS faturamento_liquido
FROM venda v JOIN item_venda i ON i.venda_id = v.id
GROUP BY 1
ORDER BY 1;

-- 4.7 Itens: veiculos x servicos
SELECT CASE WHEN veiculo_id IS NOT NULL THEN 'Veiculo' ELSE 'Servico' END AS tipo_item,
       COUNT(*) AS itens,
       SUM(quantidade * preco_unitario - COALESCE(desconto, 0)) AS valor_liquido
FROM item_venda
GROUP BY 1;
