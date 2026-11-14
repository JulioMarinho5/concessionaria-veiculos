package com.github.projetobd2.concessionaria_veiculos.modelo;

import java.time.LocalDate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

/**
 * Lado dono do @OneToOne com Cliente: aqui fica a FK cliente_id, marcada como
 * UNIQUE (cada cliente tem no maximo um documento).
 */
@Entity
@Table(name = "documento_cliente")
public class DocumentoCliente {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cliente_id", nullable = false, unique = true)
	private Cliente cliente;

	@Column(name = "numero_cnh", length = 11)
	private String numeroCnh;

	@Column(name = "validade_cnh")
	private LocalDate validadeCnh;

	public DocumentoCliente() {
	}

	public DocumentoCliente(String numeroCnh, LocalDate validadeCnh) {
		this.numeroCnh = numeroCnh;
		this.validadeCnh = validadeCnh;
	}

	public Long getId() {
		return id;
	}

	public Cliente getCliente() {
		return cliente;
	}

	void setCliente(Cliente cliente) {
		this.cliente = cliente;
	}

	public String getNumeroCnh() {
		return numeroCnh;
	}

	public void setNumeroCnh(String numeroCnh) {
		this.numeroCnh = numeroCnh;
	}

	public LocalDate getValidadeCnh() {
		return validadeCnh;
	}

	public void setValidadeCnh(LocalDate validadeCnh) {
		this.validadeCnh = validadeCnh;
	}
}
