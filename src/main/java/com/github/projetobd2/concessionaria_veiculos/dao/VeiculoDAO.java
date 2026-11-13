package com.github.projetobd2.concessionaria_veiculos.dao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Fetch;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import com.github.projetobd2.concessionaria_veiculos.modelo.ItemVenda;
import com.github.projetobd2.concessionaria_veiculos.modelo.Marca;
import com.github.projetobd2.concessionaria_veiculos.modelo.Modelo;
import com.github.projetobd2.concessionaria_veiculos.modelo.Veiculo;

public class VeiculoDAO extends GenericDAO<Veiculo, Long> {

	public VeiculoDAO(EntityManager em) {
		super(em, Veiculo.class);
	}

	/** Busca um veiculo ja trazendo modelo e marca (para exibir sem LazyInitializationException). */
	public Veiculo buscarComModelo(Long id) {
		List<Veiculo> r = em.createQuery(
				"SELECT ve FROM Veiculo ve JOIN FETCH ve.modelo m JOIN FETCH m.marca WHERE ve.id = :id",
				Veiculo.class)
				.setParameter("id", id)
				.getResultList();
		return r.isEmpty() ? null : r.get(0);
	}

	/** Um veiculo (chassi) so pode ser vendido uma vez. */
	public boolean foiVendido(Long idVeiculo) {
		return em.createQuery("SELECT COUNT(i) FROM ItemVenda i WHERE i.veiculo.id = :id", Long.class)
				.setParameter("id", idVeiculo)
				.getSingleResult() > 0;
	}

	/**
	 * JPQL 9 - Subconsulta: veiculos em estoque (que nao aparecem em nenhum item de venda).
	 * JOIN FETCH em modelo e marca evita o problema N+1 ao imprimir a lista.
	 */
	public List<Veiculo> listarNaoVendidos() {
		return em.createQuery(
				"SELECT ve FROM Veiculo ve JOIN FETCH ve.modelo m JOIN FETCH m.marca ma "
				+ "WHERE NOT EXISTS (SELECT i FROM ItemVenda i WHERE i.veiculo = ve) "
				+ "ORDER BY ma.nome, m.nome, ve.anoFabricacao", Veiculo.class)
				.getResultList();
	}

	/**
	 * Criteria 1 - Filtros dinamicos: cada parametro e opcional (null = ignora).
	 * So entram no WHERE os filtros que o usuario preencheu.
	 */
	public List<Veiculo> filtrar(String marca, String categoria, Integer anoMinimo, Integer anoMaximo,
			BigDecimal precoMaximo, boolean somenteDisponiveis) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Veiculo> cq = cb.createQuery(Veiculo.class);
		Root<Veiculo> ve = cq.from(Veiculo.class);

		// fetch = JOIN FETCH: traz modelo e marca no mesmo SELECT (sem N+1 ao imprimir).
		// No Hibernate o Fetch tambem e um Join, entao da para usar o mesmo caminho nos filtros.
		Fetch<Veiculo, Modelo> fetchModelo = ve.fetch("modelo", JoinType.INNER);
		Fetch<Modelo, Marca> fetchMarca = fetchModelo.fetch("marca", JoinType.INNER);
		Join<Veiculo, Modelo> modelo = (Join<Veiculo, Modelo>) fetchModelo;
		Join<Modelo, Marca> marcaJoin = (Join<Modelo, Marca>) fetchMarca;

		List<Predicate> filtros = new ArrayList<>();
		if (marca != null && !marca.trim().isEmpty()) {
			filtros.add(cb.like(cb.lower(marcaJoin.<String>get("nome")), "%" + marca.trim().toLowerCase() + "%"));
		}
		if (categoria != null && !categoria.trim().isEmpty()) {
			filtros.add(cb.equal(cb.lower(modelo.<String>get("categoria")), categoria.trim().toLowerCase()));
		}
		if (anoMinimo != null) {
			filtros.add(cb.greaterThanOrEqualTo(ve.<Integer>get("anoFabricacao"), anoMinimo));
		}
		if (anoMaximo != null) {
			filtros.add(cb.lessThanOrEqualTo(ve.<Integer>get("anoFabricacao"), anoMaximo));
		}
		if (precoMaximo != null) {
			filtros.add(cb.lessThanOrEqualTo(ve.<BigDecimal>get("precoTabela"), precoMaximo));
		}
		if (somenteDisponiveis) {
			Subquery<Long> vendido = cq.subquery(Long.class);
			Root<ItemVenda> i = vendido.from(ItemVenda.class);
			vendido.select(i.<Long>get("id")).where(cb.equal(i.get("veiculo"), ve));
			filtros.add(cb.not(cb.exists(vendido)));
		}

		cq.select(ve)
		  .where(filtros.toArray(new Predicate[0]))
		  .orderBy(cb.asc(ve.get("precoTabela")));

		return em.createQuery(cq).getResultList();
	}
}
