package com.github.projetobd2.concessionaria_veiculos.modelo;

import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * Um carro fisico (chassi unico). Por isso so pode aparecer em um ItemVenda.
 */
@Entity
@Table(name = "veiculo")
public class Veiculo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "chassi", nullable = false, unique = true, length = 17)
	private String chassi;

	@Column(name = "cor", length = 30)
	private String cor;

	@Column(name = "ano_fabricacao")
	private Integer anoFabricacao;

	@Column(name = "preco_tabela", precision = 12, scale = 2)
	private BigDecimal precoTabela;

	/*
	 * LAZY: listar veiculos nao precisa carregar modelo e marca. Quando
	 * precisa, as consultas usam JOIN FETCH (evita o problema N+1).
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "modelo_id", nullable = false)
	private Modelo modelo;

	public Veiculo() {
	}

	public Veiculo(String chassi, String cor, Integer anoFabricacao, BigDecimal precoTabela, Modelo modelo) {
		this.chassi = chassi;
		this.cor = cor;
		this.anoFabricacao = anoFabricacao;
		this.precoTabela = precoTabela;
		this.modelo = modelo;
	}

	public Long getId() {
		return id;
	}

	public String getChassi() {
		return chassi;
	}

	public void setChassi(String chassi) {
		this.chassi = chassi;
	}

	public String getCor() {
		return cor;
	}

	public void setCor(String cor) {
		this.cor = cor;
	}

	public Integer getAnoFabricacao() {
		return anoFabricacao;
	}

	public void setAnoFabricacao(Integer anoFabricacao) {
		this.anoFabricacao = anoFabricacao;
	}

	public BigDecimal getPrecoTabela() {
		return precoTabela;
	}

	public void setPrecoTabela(BigDecimal precoTabela) {
		this.precoTabela = precoTabela;
	}

	public Modelo getModelo() {
		return modelo;
	}

	public void setModelo(Modelo modelo) {
		this.modelo = modelo;
	}

	@Override
	public String toString() {
		return String.format(new java.util.Locale("pt", "BR"), "#%d %s %s %d %s - chassi %s - R$ %,.2f", id,
				modelo.getMarca().getNome(), modelo.getNome(), anoFabricacao, cor, chassi, precoTabela);
	}
}
