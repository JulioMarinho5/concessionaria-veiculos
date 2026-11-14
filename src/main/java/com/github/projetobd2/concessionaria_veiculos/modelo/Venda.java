package com.github.projetobd2.concessionaria_veiculos.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

@Entity
@Table(name = "venda")
@NamedQueries({
	/* Pergunta 1: quanto faturamos por ano e mes? (agregacao com GROUP BY) */
	@NamedQuery(name = "Venda.faturamentoPorAnoMes",
		query = "SELECT YEAR(v.dataHora), MONTH(v.dataHora), COUNT(DISTINCT v), "
			+ "SUM(i.quantidade * i.precoUnitario - COALESCE(i.desconto, 0)) "
			+ "FROM Venda v JOIN v.itens i "
			+ "GROUP BY YEAR(v.dataHora), MONTH(v.dataHora) "
			+ "ORDER BY YEAR(v.dataHora), MONTH(v.dataHora)"),

	/* Pergunta 3: qual vendedor mais vende? (GROUP BY + HAVING + parametro nomeado) */
	@NamedQuery(name = "Venda.rankingVendedores",
		query = "SELECT f.nome, l.nome, COUNT(DISTINCT v), "
			+ "SUM(i.quantidade * i.precoUnitario - COALESCE(i.desconto, 0)) "
			+ "FROM Venda v JOIN v.vendedor f JOIN v.loja l JOIN v.itens i "
			+ "GROUP BY f.id, f.nome, l.nome "
			+ "HAVING SUM(i.quantidade * i.precoUnitario - COALESCE(i.desconto, 0)) > :minimo "
			+ "ORDER BY SUM(i.quantidade * i.precoUnitario - COALESCE(i.desconto, 0)) DESC")
})
public class Venda {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "data_hora", nullable = false)
	private LocalDateTime dataHora;

	@Enumerated(EnumType.STRING)
	@Column(name = "forma_pagamento", nullable = false, length = 20)
	private FormaPagamento formaPagamento;

	@Column(name = "data_atualizacao", nullable = false)
	private LocalDateTime dataAtualizacao;

	/*
	 * Os tres @ManyToOne sao LAZY: quem lista vendas nem sempre precisa de
	 * cliente, vendedor e loja. Quando precisa, a consulta faz JOIN FETCH.
	 * Sem cascade: cliente, vendedor e loja ja existem antes da venda.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cliente_id", nullable = false)
	private Cliente cliente;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vendedor_id", nullable = false)
	private Funcionario vendedor;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "loja_id", nullable = false)
	private Loja loja;

	/*
	 * @OneToMany bidirecional com ItemVenda.venda (composicao no UML).
	 * cascade ALL: salvar/remover a venda salva/remove os itens.
	 * orphanRemoval: item retirado da lista e apagado do banco.
	 */
	@OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ItemVenda> itens = new ArrayList<>();

	public Venda() {
	}

	public Venda(LocalDateTime dataHora, FormaPagamento formaPagamento, Cliente cliente,
			Funcionario vendedor, Loja loja) {
		this.dataHora = dataHora;
		this.formaPagamento = formaPagamento;
		this.cliente = cliente;
		this.vendedor = vendedor;
		this.loja = loja;
	}

	public void adicionarItem(ItemVenda item) {
		itens.add(item);
		item.setVenda(this);
	}

	public void removerItem(ItemVenda item) {
		itens.remove(item);
		item.setVenda(null);
	}

	/** Soma dos valores liquidos dos itens. */
	public BigDecimal calcularTotal() {
		BigDecimal total = BigDecimal.ZERO;
		for (ItemVenda item : itens) {
			total = total.add(item.calcularValorLiquido());
		}
		return total;
	}

	@PrePersist
	@PreUpdate
	public void atualizarDataAlteracao() {
		this.dataAtualizacao = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public LocalDateTime getDataHora() {
		return dataHora;
	}

	public void setDataHora(LocalDateTime dataHora) {
		this.dataHora = dataHora;
	}

	public FormaPagamento getFormaPagamento() {
		return formaPagamento;
	}

	public void setFormaPagamento(FormaPagamento formaPagamento) {
		this.formaPagamento = formaPagamento;
	}

	public LocalDateTime getDataAtualizacao() {
		return dataAtualizacao;
	}

	public Cliente getCliente() {
		return cliente;
	}

	public void setCliente(Cliente cliente) {
		this.cliente = cliente;
	}

	public Funcionario getVendedor() {
		return vendedor;
	}

	public void setVendedor(Funcionario vendedor) {
		this.vendedor = vendedor;
	}

	public Loja getLoja() {
		return loja;
	}

	public void setLoja(Loja loja) {
		this.loja = loja;
	}

	public List<ItemVenda> getItens() {
		return itens;
	}
}
