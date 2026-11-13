package com.github.projetobd2.concessionaria_veiculos.dao;

import java.util.List;

import javax.persistence.EntityManager;

import com.github.projetobd2.concessionaria_veiculos.modelo.Funcionario;

public class FuncionarioDAO extends GenericDAO<Funcionario, Long> {

	public FuncionarioDAO(EntityManager em) {
		super(em, Funcionario.class);
	}

	public Funcionario buscarPorMatricula(String matricula) {
		List<Funcionario> r = em.createQuery(
				"SELECT f FROM Funcionario f JOIN FETCH f.loja WHERE f.matricula = :matricula", Funcionario.class)
				.setParameter("matricula", matricula)
				.getResultList();
		return r.isEmpty() ? null : r.get(0);
	}

	public List<Funcionario> listarComLoja() {
		return em.createQuery(
				"SELECT f FROM Funcionario f JOIN FETCH f.loja l ORDER BY l.nome, f.cargo, f.nome", Funcionario.class)
				.getResultList();
	}
}
