package com.github.projetobd2.concessionaria_veiculos.modelo;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table(name = "marca")
public class Marca {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "nome", nullable = false, unique = true, length = 60)
	private String nome;

	/* cascade PERSIST: cadastrar a marca ja salva os modelos dela. */
	@OneToMany(mappedBy = "marca", cascade = CascadeType.PERSIST)
	private List<Modelo> modelos = new ArrayList<>();

	public Marca() {
	}

	public Marca(String nome) {
		this.nome = nome;
	}

	public Modelo adicionarModelo(String nome, String categoria) {
		Modelo modelo = new Modelo(nome, categoria, this);
		modelos.add(modelo);
		return modelo;
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

	public List<Modelo> getModelos() {
		return modelos;
	}
}
