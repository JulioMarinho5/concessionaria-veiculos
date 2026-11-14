package com.github.projetobd2.concessionaria_veiculos.modelo;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * Objeto de valor embutido (@Embeddable): nao tem tabela nem id proprios.
 * Suas colunas (logradouro, cidade, estado) ficam dentro das tabelas pessoa e loja.
 */
@Embeddable
public class Endereco {

	@Column(name = "logradouro", length = 150)
	private String logradouro;

	@Column(name = "cidade", length = 80)
	private String cidade;

	@Column(name = "estado", length = 2, columnDefinition = "char(2)")
	private String estado;

	public Endereco() {
	}

	public Endereco(String logradouro, String cidade, String estado) {
		this.logradouro = logradouro;
		this.cidade = cidade;
		this.estado = estado;
	}

	public String getLogradouro() {
		return logradouro;
	}

	public void setLogradouro(String logradouro) {
		this.logradouro = logradouro;
	}

	public String getCidade() {
		return cidade;
	}

	public void setCidade(String cidade) {
		this.cidade = cidade;
	}

	public String getEstado() {
		return estado;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

	@Override
	public String toString() {
		return logradouro + " - " + cidade + "/" + estado;
	}
}
