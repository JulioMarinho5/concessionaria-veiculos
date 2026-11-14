package com.github.projetobd2.concessionaria_veiculos.modelo;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table(name = "loja")
public class Loja {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "nome", nullable = false, length = 100)
	private String nome;

	@Embedded
	private Endereco endereco = new Endereco();

	/*
	 * @OneToMany bidirecional com Funcionario.loja (mappedBy = lado inverso).
	 * cascade PERSIST: ao salvar uma loja nova, os funcionarios adicionados
	 * nela tambem sao salvos. Sem REMOVE: fechar uma loja nao apaga o
	 * historico dos funcionarios que venderam nela.
	 */
	@OneToMany(mappedBy = "loja", cascade = CascadeType.PERSIST)
	private List<Funcionario> funcionarios = new ArrayList<>();

	public Loja() {
	}

	public Loja(String nome, Endereco endereco) {
		this.nome = nome;
		this.endereco = endereco;
	}

	/** Mantem os dois lados do relacionamento sincronizados. */
	public void adicionarFuncionario(Funcionario funcionario) {
		funcionarios.add(funcionario);
		funcionario.setLoja(this);
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

	public Endereco getEndereco() {
		return endereco;
	}

	public void setEndereco(Endereco endereco) {
		this.endereco = endereco;
	}

	public List<Funcionario> getFuncionarios() {
		return funcionarios;
	}

	@Override
	public String toString() {
		return "#" + id + " " + nome + " - " + endereco.getCidade() + "/" + endereco.getEstado();
	}
}
