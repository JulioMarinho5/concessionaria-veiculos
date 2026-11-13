package com.github.projetobd2.concessionaria_veiculos.dao;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Root;

import com.github.projetobd2.concessionaria_veiculos.modelo.ItemVenda;
import com.github.projetobd2.concessionaria_veiculos.modelo.Loja;
import com.github.projetobd2.concessionaria_veiculos.modelo.Venda;

/**
 * Consultas de venda. "Faturamento" = soma do valor liquido dos itens:
 * quantidade * preco_unitario - desconto.
 */
public class VendaDAO extends GenericDAO<Venda, Long> {

	public VendaDAO(EntityManager em) {
		super(em, Venda.class);
	}

	/**
	 * JPQL 1 - Pergunta 1 (@NamedQuery "Venda.faturamentoPorAnoMes").
	 * Retorno: [ano, mes, numero de vendas, faturamento]
	 */
	public List<Object[]> faturamentoPorAnoMes() {
		return em.createNamedQuery("Venda.faturamentoPorAnoMes", Object[].class).getResultList();
	}

	/**
	 * JPQL 2 - Pergunta 2: marcas e modelos mais vendidos.
	 * Junção em cadeia ItemVenda -> Veiculo -> Modelo -> Marca; setMaxResults limita ao top N.
	 * Retorno: [marca, modelo, unidades vendidas, faturamento]
	 */
	public List<Object[]> modelosMaisVendidos(int topN) {
		return em.createQuery(
				"SELECT ma.nome, m.nome, COUNT(i), SUM(i.precoUnitario - COALESCE(i.desconto, 0)) "
				+ "FROM ItemVenda i JOIN i.veiculo ve JOIN ve.modelo m JOIN m.marca ma "
				+ "GROUP BY ma.nome, m.nome "
				+ "ORDER BY COUNT(i) DESC, ma.nome, m.nome", Object[].class)
				.setMaxResults(topN)
				.getResultList();
	}

	/**
	 * JPQL 3 - Pergunta 3 (@NamedQuery "Venda.rankingVendedores").
	 * HAVING com parametro nomeado: so vendedores que faturaram acima de :minimo.
	 * Retorno: [vendedor, loja, numero de vendas, faturamento]
	 */
	public List<Object[]> rankingVendedores(BigDecimal minimo) {
		return em.createNamedQuery("Venda.rankingVendedores", Object[].class)
				.setParameter("minimo", minimo)
				.getResultList();
	}

	/**
	 * JPQL 4 - Pergunta 4: qual loja fatura mais e quanto desconto da?
	 * Retorno: [loja, cidade, faturamento bruto, total de desconto, faturamento liquido, % desconto]
	 */
	public List<Object[]> faturamentoEDescontoPorLoja() {
		return em.createQuery(
				"SELECT l.nome, l.endereco.cidade, "
				+ "SUM(i.quantidade * i.precoUnitario), "
				+ "SUM(COALESCE(i.desconto, 0)), "
				+ "SUM(i.quantidade * i.precoUnitario - COALESCE(i.desconto, 0)), "
				+ "100.0 * SUM(COALESCE(i.desconto, 0)) / SUM(i.quantidade * i.precoUnitario) "
				+ "FROM Venda v JOIN v.loja l JOIN v.itens i "
				+ "GROUP BY l.id, l.nome, l.endereco.cidade "
				+ "ORDER BY SUM(i.quantidade * i.precoUnitario - COALESCE(i.desconto, 0)) DESC", Object[].class)
				.getResultList();
	}

	/**
	 * JPQL 5 - Pergunta 5: forma de pagamento mais usada (agrupa pelo atributo @Enumerated).
	 * Retorno: [FormaPagamento, numero de vendas]
	 */
	public List<Object[]> usoFormasPagamento() {
		return em.createQuery(
				"SELECT v.formaPagamento, COUNT(v) FROM Venda v "
				+ "GROUP BY v.formaPagamento ORDER BY COUNT(v) DESC", Object[].class)
				.getResultList();
	}

	/**
	 * JPQL 6 - Pergunta 6: quanto os servicos representam no faturamento?
	 * CASE dentro do SUM separa itens de servico e de veiculo.
	 * Retorno: [faturamento com veiculos, faturamento com servicos]
	 */
	public Object[] faturamentoVeiculosXServicos() {
		return em.createQuery(
				"SELECT "
				+ "SUM(CASE WHEN i.veiculo IS NOT NULL "
				+ "    THEN i.quantidade * i.precoUnitario - COALESCE(i.desconto, 0) ELSE 0 END), "
				+ "SUM(CASE WHEN i.servico IS NOT NULL "
				+ "    THEN i.quantidade * i.precoUnitario - COALESCE(i.desconto, 0) ELSE 0 END) "
				+ "FROM ItemVenda i", Object[].class)
				.getSingleResult();
	}

	/**
	 * JPQL 8 - JOIN FETCH: carrega a venda com cliente, vendedor, loja e todos os itens
	 * (e o veiculo/servico de cada item) em um unico SELECT, sem N+1.
	 */
	public Venda buscarComItens(Long id) {
		List<Venda> r = em.createQuery(
				"SELECT DISTINCT v FROM Venda v "
				+ "JOIN FETCH v.cliente JOIN FETCH v.vendedor JOIN FETCH v.loja "
				+ "JOIN FETCH v.itens i "
				+ "LEFT JOIN FETCH i.veiculo ve LEFT JOIN FETCH ve.modelo m LEFT JOIN FETCH m.marca "
				+ "LEFT JOIN FETCH i.servico "
				+ "WHERE v.id = :id", Venda.class)
				.setParameter("id", id)
				.getResultList();
		return r.isEmpty() ? null : r.get(0);
	}

	/** Ultimas vendas registradas (usada no menu). */
	public List<Venda> ultimasVendas(int quantidade) {
		return em.createQuery(
				"SELECT v FROM Venda v JOIN FETCH v.cliente JOIN FETCH v.loja ORDER BY v.dataHora DESC, v.id DESC",
				Venda.class)
				.setMaxResults(quantidade)
				.getResultList();
	}

	/**
	 * Criteria 2 - Faturamento por loja dentro de um periodo (agregacao com Criteria:
	 * multiselect + groupBy + orderBy).
	 * Retorno: [loja, numero de vendas, faturamento]
	 */
	public List<Object[]> faturamentoPorLojaNoPeriodo(LocalDateTime inicio, LocalDateTime fim) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
		Root<Venda> v = cq.from(Venda.class);
		Join<Venda, Loja> l = v.join("loja");
		Join<Venda, ItemVenda> i = v.join("itens");

		// quantidade * precoUnitario - COALESCE(desconto, 0)
		Expression<Number> bruto = cb.prod(i.<Integer>get("quantidade"), i.<BigDecimal>get("precoUnitario"));
		Expression<Number> liquido = cb.diff(bruto,
				cb.<Number>coalesce(i.<BigDecimal>get("desconto"), BigDecimal.ZERO));
		Expression<Number> faturamento = cb.sum(liquido);

		cq.multiselect(l.get("nome"), cb.countDistinct(v), faturamento)
		  .where(cb.between(v.<LocalDateTime>get("dataHora"), inicio, fim))
		  .groupBy(l.get("id"), l.get("nome"))
		  .orderBy(cb.desc(faturamento));

		return em.createQuery(cq).getResultList();
	}
}
