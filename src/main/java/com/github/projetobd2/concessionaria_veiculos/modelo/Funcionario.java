package com.github.projetobd2.concessionaria_veiculos.modelo;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;

@Entity
@Table(name = "funcionario")
@PrimaryKeyJoinColumn(name = "id")
public class Funcionario extends Pessoa {

	@Column(name = "matricula", length = 20, unique = true)
	private String matricula;

	/** Ex.: Vendedor, Consultor de Servicos, Gerente. */
	@Column(name = "cargo", length = 40)
	private String cargo;

	/* Dono do relacionamento bidirecional com Loja.funcionarios. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "loja_id", nullable = false)
	private Loja loja;

	public Funcionario() {
	}

	public Funcionario(String nome, String cpf, String telefone, Endereco endereco,
			String matricula, String cargo) {
		setNome(nome);
		setCpf(cpf);
		setTelefone(telefone);
		setEndereco(endereco);
		this.matricula = matricula;
		this.cargo = cargo;
	}

	public String getMatricula() {
		return matricula;
	}

	public void setMatricula(String matricula) {
		this.matricula = matricula;
	}

	public String getCargo() {
		return cargo;
	}

	public void setCargo(String cargo) {
		this.cargo = cargo;
	}

	public Loja getLoja() {
		return loja;
	}

	void setLoja(Loja loja) {
		this.loja = loja;
	}

	@Override
	public String toString() {
		return String.format("#%d %s (%s, matr. %s)", getId(), getNome(), cargo, matricula);
	}
}
