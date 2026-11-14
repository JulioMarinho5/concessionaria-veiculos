package com.github.projetobd2.concessionaria_veiculos.dao;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 * Cria a EntityManagerFactory uma unica vez (objeto caro, le o persistence.xml
 * e monta os metadados) e entrega um EntityManager novo a cada uso (objeto
 * leve, representa uma unidade de trabalho e o contexto de persistencia).
 */
public final class JPAUtil {

	private static final String UNIDADE = "concessionariaPU";
	private static EntityManagerFactory emf;

	private JPAUtil() {
	}

	public static synchronized EntityManager getEntityManager() {
		if (emf == null) {
			emf = Persistence.createEntityManagerFactory(UNIDADE);
		}
		return emf.createEntityManager();
	}

	public static synchronized void fechar() {
		if (emf != null && emf.isOpen()) {
			emf.close();
		}
	}
}
