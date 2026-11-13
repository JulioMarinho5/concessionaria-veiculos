package com.github.projetobd2.concessionaria_veiculos.dao;

import java.io.Serializable;
import java.util.List;
import java.util.function.Supplier;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

/**
 * DAO generico com o CRUD comum a todas as entidades.
 *
 * Recebe o EntityManager pronto: quem chama decide quanto tempo ele vive
 * (uma tela, uma operacao, a carga inteira). Operacoes de escrita abrem e
 * fecham a propria transacao, com rollback em caso de erro.
 *
 * @param <T>  tipo da entidade
 * @param <ID> tipo da chave primaria
 */
public class GenericDAO<T, ID extends Serializable> {

	protected final EntityManager em;
	private final Class<T> classe;

	public GenericDAO(EntityManager em, Class<T> classe) {
		this.em = em;
		this.classe = classe;
	}

	/** INSERT: a entidade passa de new para managed. */
	public T salvar(T entidade) {
		return executarEmTransacao(() -> {
			em.persist(entidade);
			return entidade;
		});
	}

	/** UPDATE: copia o estado de uma entidade detached para uma managed. */
	public T atualizar(T entidade) {
		return executarEmTransacao(() -> em.merge(entidade));
	}

	/** DELETE: a entidade passa de managed para removed. */
	public boolean remover(ID id) {
		return executarEmTransacao(() -> {
			T entidade = em.find(classe, id);
			if (entidade == null) {
				return false;
			}
			em.remove(entidade);
			return true;
		});
	}

	public T buscarPorId(ID id) {
		return em.find(classe, id);
	}

	public List<T> listarTodos() {
		return em.createQuery("SELECT e FROM " + classe.getSimpleName() + " e", classe)
				.getResultList();
	}

	public long contar() {
		return em.createQuery("SELECT COUNT(e) FROM " + classe.getSimpleName() + " e", Long.class)
				.getSingleResult();
	}

	/**
	 * Controle de transacao: begin, executa, commit; se der erro, rollback e
	 * relanca a excecao. Se ja existir uma transacao ativa (ex.: carga de
	 * dados), apenas executa dentro dela.
	 */
	protected <R> R executarEmTransacao(Supplier<R> acao) {
		EntityTransaction tx = em.getTransaction();
		if (tx.isActive()) {
			return acao.get();
		}
		try {
			tx.begin();
			R resultado = acao.get();
			tx.commit();
			return resultado;
		} catch (RuntimeException e) {
			if (tx.isActive()) {
				tx.rollback();
			}
			// depois de um rollback o contexto pode ter objetos em estado invalido
			em.clear();
			throw e;
		}
	}
}
