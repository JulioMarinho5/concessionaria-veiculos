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
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

/**
 * Entidade intermediaria entre Venda e o que foi vendido (Veiculo ou Servico).
 * Substitui um @ManyToMany porque a associacao tem atributos proprios
 * (quantidade, preco unitario, desconto). Cada linha e um item vendido,
 * que e a granularidade da tabela fato do DW.
 */
@Entity
@Table(name = "item_venda")
public class ItemVenda {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/* Dono do @OneToMany bidirecional com Venda.itens. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "venda_id", nullable = false)
	private Venda venda;

	/* Opcional: preenchido quando o item e um veiculo. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "veiculo_id")
	private Veiculo veiculo;

	/* Opcional: preenchido quando o item e um servico. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "servico_id")
	private Servico servico;

	@Column(name = "quantidade", nullable = false)
	private Integer quantidade;

	@Column(name = "preco_unitario", nullable = false, precision = 12, scale = 2)
	private BigDecimal precoUnitario;

	@Column(name = "desconto", precision = 12, scale = 2)
	private BigDecimal desconto = BigDecimal.ZERO;

	public ItemVenda() {
	}

	public static ItemVenda deVeiculo(Veiculo veiculo, BigDecimal precoUnitario, BigDecimal desconto) {
		ItemVenda item = new ItemVenda();
		item.veiculo = veiculo;
		item.quantidade = 1;
		item.precoUnitario = precoUnitario;
		item.desconto = desconto;
		return item;
	}

	public static ItemVenda deServico(Servico servico, int quantidade, BigDecimal precoUnitario,
			BigDecimal desconto) {
		ItemVenda item = new ItemVenda();
		item.servico = servico;
		item.quantidade = quantidade;
		item.precoUnitario = precoUnitario;
		item.desconto = desconto;
		return item;
	}

	public BigDecimal calcularValorBruto() {
		return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
	}

	public BigDecimal calcularValorLiquido() {
		return calcularValorBruto().subtract(desconto == null ? BigDecimal.ZERO : desconto);
	}

	/**
	 * Regras do item, verificadas antes de inserir ou atualizar:
	 * - e veiculo OU servico, nunca os dois nem nenhum;
	 * - veiculo tem quantidade 1 (cada chassi e unico);
	 * - desconto entre zero e o valor bruto.
	 */
	@PrePersist
	@PreUpdate
	public void validar() {
		if ((veiculo == null) == (servico == null)) {
			throw new IllegalStateException("O item deve ter um veiculo ou um servico (apenas um dos dois).");
		}
		if (quantidade == null || quantidade <= 0) {
			throw new IllegalStateException("Quantidade deve ser maior que zero.");
		}
		if (veiculo != null && quantidade != 1) {
			throw new IllegalStateException("Item de veiculo deve ter quantidade 1.");
		}
		if (precoUnitario == null || precoUnitario.signum() < 0) {
			throw new IllegalStateException("Preco unitario invalido.");
		}
		if (desconto == null) {
			desconto = BigDecimal.ZERO;
		}
		if (desconto.signum() < 0 || desconto.compareTo(calcularValorBruto()) > 0) {
			throw new IllegalStateException("Desconto deve ficar entre zero e o valor bruto do item.");
		}
	}

	public Long getId() {
		return id;
	}

	public Venda getVenda() {
		return venda;
	}

	void setVenda(Venda venda) {
		this.venda = venda;
	}

	public Veiculo getVeiculo() {
		return veiculo;
	}

	public Servico getServico() {
		return servico;
	}

	public Integer getQuantidade() {
		return quantidade;
	}

	public BigDecimal getPrecoUnitario() {
		return precoUnitario;
	}

	public BigDecimal getDesconto() {
		return desconto;
	}

	public String getDescricao() {
		if (veiculo != null) {
			return veiculo.getModelo().getMarca().getNome() + " " + veiculo.getModelo().getNome()
					+ " " + veiculo.getAnoFabricacao() + " (" + veiculo.getChassi() + ")";
		}
		return servico.getNome();
	}
}
