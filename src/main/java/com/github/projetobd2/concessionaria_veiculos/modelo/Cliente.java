package com.github.projetobd2.concessionaria_veiculos.modelo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;

@Entity
@Table(name = "cliente")
@PrimaryKeyJoinColumn(name = "id")
@NamedQueries({
	@NamedQuery(name = "Cliente.porCpf",
		query = "SELECT c FROM Cliente c WHERE c.cpf = :cpf")
})
public class Cliente extends Pessoa {

	@Column(name = "data_cadastro")
	private LocalDate dataCadastro;

	/*
	 * Lado inverso do @OneToOne (a FK cliente_id fica em documento_cliente).
	 * cascade ALL + orphanRemoval: o documento nao existe sem o cliente, entao
	 * e salvo e removido junto com ele.
	 */
	@OneToOne(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
	private DocumentoCliente documento;

	/*
	 * Lado inverso de Venda.cliente. Sem cascade: venda tem ciclo de vida
	 * proprio e nao deve ser apagada junto com o cliente. LAZY (padrao do
	 * @OneToMany) para nao carregar o historico inteiro a cada consulta.
	 */
	@OneToMany(mappedBy = "cliente")
	private List<Venda> vendas = new ArrayList<>();

	public Cliente() {
	}

	public Cliente(String nome, String cpf, String telefone, Endereco endereco, LocalDate dataCadastro) {
		setNome(nome);
		setCpf(cpf);
		setTelefone(telefone);
		setEndereco(endereco);
		this.dataCadastro = dataCadastro;
	}

	/**
	 * Mudanca de cidade: e o caso usado pelo ETL para demonstrar SCD tipo 2
	 * (o @PreUpdate de Pessoa atualiza data_atualizacao).
	 */
	public void alterarCidade(String cidade, String estado) {
		getEndereco().setCidade(cidade);
		getEndereco().setEstado(estado);
	}

	/** Mantem os dois lados do @OneToOne sincronizados. */
	public void setDocumento(DocumentoCliente documento) {
		if (this.documento != null) {
			this.documento.setCliente(null);
		}
		this.documento = documento;
		if (documento != null) {
			documento.setCliente(this);
		}
	}

	public DocumentoCliente getDocumento() {
		return documento;
	}

	public LocalDate getDataCadastro() {
		return dataCadastro;
	}

	public void setDataCadastro(LocalDate dataCadastro) {
		this.dataCadastro = dataCadastro;
	}

	public List<Venda> getVendas() {
		return vendas;
	}

	@Override
	public String toString() {
		return String.format("#%d %s (CPF %s) - %s", getId(), getNome(), getCpf(), getEndereco());
	}
}
