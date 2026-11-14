package com.github.projetobd2.concessionaria_veiculos.dao;

import java.time.LocalDate;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import com.github.projetobd2.concessionaria_veiculos.modelo.Cliente;

public class ClienteDAO extends GenericDAO<Cliente, Long> {

	public ClienteDAO(EntityManager em) {
		super(em, Cliente.class);
	}

	/** Usa a @NamedQuery "Cliente.porCpf" declarada na entidade. */
	public Cliente buscarPorCpf(String cpf) {
		try {
			return em.createNamedQuery("Cliente.porCpf", Cliente.class)
					.setParameter("cpf", cpf)
					.getSingleResult();
		} catch (NoResultException e) {
			return null;
		}
	}

	public long contarCompras(Long idCliente) {
		return em.createQuery("SELECT COUNT(v) FROM Venda v WHERE v.cliente.id = :id", Long.class)
				.setParameter("id", idCliente)
				.getSingleResult();
	}

	/**
	 * JPQL 7 - Pergunta 7: de quais cidades vem mais clientes?
	 * Navega pelo @Embeddable (c.endereco.cidade) e agrupa.
	 * Retorno: [cidade, estado, quantidade de clientes]
	 */
	public List<Object[]> clientesPorCidade() {
		return em.createQuery(
				"SELECT c.endereco.cidade, c.endereco.estado, COUNT(c) "
				+ "FROM Cliente c "
				+ "GROUP BY c.endereco.cidade, c.endereco.estado "
				+ "ORDER BY COUNT(c) DESC, c.endereco.cidade", Object[].class)
				.getResultList();
	}

	/**
	 * JPQL 10 - Paginacao: lista os clientes em ordem alfabetica, uma pagina por vez.
	 * setFirstResult/setMaxResults viram OFFSET/LIMIT no SQL.
	 *
	 * @param pagina comeca em 0
	 */
	public List<Cliente> listarPaginado(int pagina, int tamanhoPagina) {
		return em.createQuery("SELECT c FROM Cliente c ORDER BY c.nome", Cliente.class)
				.setFirstResult(pagina * tamanhoPagina)
				.setMaxResults(tamanhoPagina)
				.getResultList();
	}

	/**
	 * Criteria 3 - Clientes de um estado cadastrados a partir de uma data,
	 * em ordem de cadastro (mais recentes primeiro).
	 */
	public List<Cliente> buscarPorEstadoCadastradosDesde(String estado, LocalDate desde) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Cliente> cq = cb.createQuery(Cliente.class);
		Root<Cliente> c = cq.from(Cliente.class);

		cq.select(c)
		  .where(cb.equal(c.get("endereco").get("estado"), estado),
				 cb.greaterThanOrEqualTo(c.<LocalDate>get("dataCadastro"), desde))
		  .orderBy(cb.desc(c.get("dataCadastro")), cb.asc(c.get("nome")));

		return em.createQuery(cq).getResultList();
	}
}
