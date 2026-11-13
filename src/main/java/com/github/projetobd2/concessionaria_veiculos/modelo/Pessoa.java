package com.github.projetobd2.concessionaria_veiculos.modelo;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

/**
 * Raiz da hierarquia de heranca Pessoa -> Cliente / Funcionario.
 *
 * Estrategia JOINED: a tabela pessoa guarda os campos comuns e as tabelas
 * cliente e funcionario guardam so os campos proprios, com id que e PK e FK
 * para pessoa.id (igual ao modelo relacional do Arquiteto). Fica normalizado
 * e sem colunas nulas; o custo e um JOIN a mais ao carregar um Cliente.
 */
@Entity
@Table(name = "pessoa")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Pessoa {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "nome", nullable = false, length = 120)
	private String nome;

	@Column(name = "cpf", nullable = false, unique = true, length = 14)
	private String cpf;

	@Column(name = "telefone", length = 20)
	private String telefone;

	@Embedded
	private Endereco endereco = new Endereco();

	@Column(name = "data_atualizacao", nullable = false)
	private LocalDateTime dataAtualizacao;

	/**
	 * Toda insercao ou alteracao grava a data/hora. O ETL usa esta coluna
	 * para a carga incremental (pega so o que mudou desde a ultima execucao).
	 */
	@PrePersist
	@PreUpdate
	public void atualizarDataAlteracao() {
		this.dataAtualizacao = LocalDateTime.now();
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

	public String getCpf() {
		return cpf;
	}

	public void setCpf(String cpf) {
		this.cpf = cpf;
	}

	public String getTelefone() {
		return telefone;
	}

	public void setTelefone(String telefone) {
		this.telefone = telefone;
	}

	public Endereco getEndereco() {
		return endereco;
	}

	public void setEndereco(Endereco endereco) {
		this.endereco = endereco;
	}

	public LocalDateTime getDataAtualizacao() {
		return dataAtualizacao;
	}
}
