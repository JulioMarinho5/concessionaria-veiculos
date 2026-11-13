package com.github.projetobd2.concessionaria_veiculos.modelo;

import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "servico")
public class Servico {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "nome", nullable = false, length = 80)
	private String nome;

	@Column(name = "preco_tabela", precision = 12, scale = 2)
	private BigDecimal precoTabela;

	public Servico() {
	}

	public Servico(String nome, BigDecimal precoTabela) {
		this.nome = nome;
		this.precoTabela = precoTabela;
	}

	public Long getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public BigDecimal getPrecoTabela() {
		return precoTabela;
	}

	public void setPrecoTabela(BigDecimal precoTabela) {
		this.precoTabela = precoTabela;
	}

	@Override
	public String toString() {
		return String.format(new java.util.Locale("pt", "BR"), "#%d %s - R$ %,.2f", id, nome, precoTabela);
	}
}
