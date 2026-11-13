package com.github.projetobd2.concessionaria_veiculos.app;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import javax.persistence.EntityManager;

import com.github.projetobd2.concessionaria_veiculos.dao.ClienteDAO;
import com.github.projetobd2.concessionaria_veiculos.dao.JPAUtil;
import com.github.projetobd2.concessionaria_veiculos.dao.VeiculoDAO;
import com.github.projetobd2.concessionaria_veiculos.dao.VendaDAO;
import com.github.projetobd2.concessionaria_veiculos.modelo.Cliente;
import com.github.projetobd2.concessionaria_veiculos.modelo.ItemVenda;
import com.github.projetobd2.concessionaria_veiculos.modelo.Veiculo;
import com.github.projetobd2.concessionaria_veiculos.modelo.Venda;

/**
 * Executa e imprime as 10 consultas JPQL e as 3 consultas Criteria.
 * Pode ser rodada sozinha (Run As > Java Application) ou pelo menu.
 */
public class Relatorios {

	private final VendaDAO vendaDAO;
	private final ClienteDAO clienteDAO;
	private final VeiculoDAO veiculoDAO;

	public Relatorios(EntityManager em) {
		this.vendaDAO = new VendaDAO(em);
		this.clienteDAO = new ClienteDAO(em);
		this.veiculoDAO = new VeiculoDAO(em);
	}

	public static void main(String[] args) {
		EntityManager em = JPAUtil.getEntityManager();
		try {
			new Relatorios(em).imprimirTodos();
		} finally {
			em.close();
			JPAUtil.fechar();
		}
	}

	public void imprimirTodos() {
		jpql1FaturamentoPorAnoMes();
		jpql2ModelosMaisVendidos();
		jpql3RankingVendedores();
		jpql4FaturamentoPorLoja();
		jpql5FormasPagamento();
		jpql6VeiculosXServicos();
		jpql7ClientesPorCidade();
		jpql8VendaComItens(1L);
		jpql9VeiculosEmEstoque();
		jpql10ClientesPaginados(0, 10);
		criteria1FiltroVeiculos("Jeep", "SUV", 2025, null, new BigDecimal("220000"), true);
		criteria2FaturamentoLojaPeriodo(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
		criteria3ClientesPorEstado("BA", LocalDate.of(2026, 1, 1));
	}

	public void jpql1FaturamentoPorAnoMes() {
		titulo("JPQL 1 - Faturamento por ano e mes (@NamedQuery, GROUP BY)");
		System.out.printf("%-6s %-4s %8s %18s%n", "Ano", "Mes", "Vendas", "Faturamento");
		int anoAtual = -1;
		BigDecimal totalAno = BigDecimal.ZERO;
		for (Object[] r : vendaDAO.faturamentoPorAnoMes()) {
			int ano = ((Number) r[0]).intValue();
			if (anoAtual != -1 && ano != anoAtual) {
				System.out.printf("%-11s %8s %18s%n", "Total " + anoAtual, "", moeda(totalAno));
				totalAno = BigDecimal.ZERO;
			}
			anoAtual = ano;
			totalAno = totalAno.add(dec(r[3]));
			System.out.printf("%-6d %-4d %8d %18s%n", ano, ((Number) r[1]).intValue(), (Long) r[2], moeda(dec(r[3])));
		}
		System.out.printf("%-11s %8s %18s%n", "Total " + anoAtual, "", moeda(totalAno));
	}

	public void jpql2ModelosMaisVendidos() {
		titulo("JPQL 2 - Top 10 modelos mais vendidos (juncao Item > Veiculo > Modelo > Marca)");
		System.out.printf("%-12s %-15s %8s %18s%n", "Marca", "Modelo", "Unid.", "Faturamento");
		for (Object[] r : vendaDAO.modelosMaisVendidos(10)) {
			System.out.printf("%-12s %-15s %8d %18s%n", r[0], r[1], (Long) r[2], moeda(dec(r[3])));
		}
	}

	public void jpql3RankingVendedores() {
		BigDecimal minimo = new BigDecimal("5000000");
		titulo("JPQL 3 - Vendedores que faturaram mais de " + moeda(minimo) + " (@NamedQuery, HAVING)");
		System.out.printf("%-30s %-36s %7s %18s%n", "Vendedor", "Loja", "Vendas", "Faturamento");
		for (Object[] r : vendaDAO.rankingVendedores(minimo)) {
			System.out.printf("%-30s %-36s %7d %18s%n", r[0], r[1], (Long) r[2], moeda(dec(r[3])));
		}
	}

	public void jpql4FaturamentoPorLoja() {
		titulo("JPQL 4 - Faturamento e desconto por loja");
		System.out.printf("%-36s %18s %15s %18s %7s%n", "Loja", "Bruto", "Desconto", "Liquido", "% desc");
		for (Object[] r : vendaDAO.faturamentoEDescontoPorLoja()) {
			System.out.printf("%-36s %18s %15s %18s %6.2f%%%n", r[0], moeda(dec(r[2])),
					moeda(dec(r[3])), moeda(dec(r[4])), ((Number) r[5]).doubleValue());
		}
	}

	public void jpql5FormasPagamento() {
		titulo("JPQL 5 - Uso das formas de pagamento (GROUP BY em atributo @Enumerated)");
		for (Object[] r : vendaDAO.usoFormasPagamento()) {
			System.out.printf("%-15s %6d vendas%n", r[0], (Long) r[1]);
		}
	}

	public void jpql6VeiculosXServicos() {
		titulo("JPQL 6 - Participacao dos servicos no faturamento (SUM com CASE)");
		Object[] r = vendaDAO.faturamentoVeiculosXServicos();
		BigDecimal veiculos = dec(r[0]);
		BigDecimal servicos = dec(r[1]);
		BigDecimal total = veiculos.add(servicos);
		System.out.printf("Veiculos: %18s (%.1f%%)%n", moeda(veiculos), pct(veiculos, total));
		System.out.printf("Servicos: %18s (%.1f%%)%n", moeda(servicos), pct(servicos, total));
	}

	public void jpql7ClientesPorCidade() {
		titulo("JPQL 7 - Clientes por cidade (caminho pelo @Embeddable Endereco)");
		for (Object[] r : clienteDAO.clientesPorCidade()) {
			System.out.printf("%-25s %-3s %4d clientes%n", r[0], r[1], (Long) r[2]);
		}
	}

	public void jpql8VendaComItens(Long idVenda) {
		titulo("JPQL 8 - Venda #" + idVenda + " com itens (JOIN FETCH)");
		Venda v = vendaDAO.buscarComItens(idVenda);
		if (v == null) {
			System.out.println("Venda nao encontrada.");
			return;
		}
		imprimirVenda(v);
	}

	public static void imprimirVenda(Venda v) {
		System.out.printf("Venda #%d em %s - %s%n", v.getId(), v.getDataHora().toString().replace('T', ' '),
				v.getFormaPagamento());
		System.out.println("Cliente:  " + v.getCliente().getNome() + " (" + v.getCliente().getCpf() + ")");
		System.out.println("Vendedor: " + v.getVendedor().getNome() + " - " + v.getLoja().getNome());
		for (ItemVenda i : v.getItens()) {
			System.out.printf("  %-55s %2dx %14s  desc. %12s  = %14s%n", i.getDescricao(), i.getQuantidade(),
					moeda(i.getPrecoUnitario()), moeda(i.getDesconto()), moeda(i.calcularValorLiquido()));
		}
		System.out.printf("  TOTAL: %s%n", moeda(v.calcularTotal()));
	}

	public void jpql9VeiculosEmEstoque() {
		titulo("JPQL 9 - Veiculos em estoque (subconsulta NOT EXISTS) - primeiros 10");
		List<Veiculo> estoque = veiculoDAO.listarNaoVendidos();
		System.out.println(estoque.size() + " veiculos em estoque.");
		for (Veiculo v : estoque.subList(0, Math.min(10, estoque.size()))) {
			System.out.println("  " + v);
		}
	}

	public void jpql10ClientesPaginados(int pagina, int tamanho) {
		titulo("JPQL 10 - Clientes em ordem alfabetica, pagina " + (pagina + 1) + " (setFirstResult/setMaxResults)");
		for (Cliente c : clienteDAO.listarPaginado(pagina, tamanho)) {
			System.out.println("  " + c);
		}
	}

	public void criteria1FiltroVeiculos(String marca, String categoria, Integer anoMin, Integer anoMax,
			BigDecimal precoMax, boolean disponiveis) {
		titulo(String.format("Criteria 1 - Filtro dinamico de veiculos (marca=%s, categoria=%s, ano>=%s, "
				+ "ano<=%s, preco<=%s, so estoque=%s)", marca, categoria, anoMin, anoMax, precoMax, disponiveis));
		List<Veiculo> r = veiculoDAO.filtrar(marca, categoria, anoMin, anoMax, precoMax, disponiveis);
		System.out.println(r.size() + " veiculos encontrados.");
		for (Veiculo v : r.subList(0, Math.min(10, r.size()))) {
			System.out.println("  " + v);
		}
	}

	public void criteria2FaturamentoLojaPeriodo(LocalDate inicio, LocalDate fim) {
		titulo("Criteria 2 - Faturamento por loja de " + inicio + " a " + fim + " (multiselect + groupBy)");
		for (Object[] r : vendaDAO.faturamentoPorLojaNoPeriodo(inicio.atStartOfDay(), fim.atTime(23, 59, 59))) {
			System.out.printf("%-36s %6d vendas %18s%n", r[0], (Long) r[1], moeda(dec(r[2])));
		}
	}

	public void criteria3ClientesPorEstado(String uf, LocalDate desde) {
		titulo("Criteria 3 - Clientes de " + uf + " cadastrados desde " + desde);
		List<Cliente> r = clienteDAO.buscarPorEstadoCadastradosDesde(uf, desde);
		System.out.println(r.size() + " clientes.");
		for (Cliente c : r) {
			System.out.println("  " + c.getDataCadastro() + "  " + c);
		}
	}

	// ---------------------------------------------------------------- formato

	static void titulo(String t) {
		System.out.println();
		System.out.println("=== " + t);
	}

	/** Somas podem voltar como BigDecimal, Double ou Long conforme a expressao; normaliza. */
	static BigDecimal dec(Object valor) {
		if (valor == null) {
			return BigDecimal.ZERO;
		}
		return valor instanceof BigDecimal ? (BigDecimal) valor : new BigDecimal(valor.toString());
	}

	static String moeda(BigDecimal v) {
		return v == null ? "R$ 0,00" : String.format(new java.util.Locale("pt", "BR"), "R$ %,.2f", v);
	}

	private static double pct(BigDecimal parte, BigDecimal total) {
		return total.signum() == 0 ? 0 : parte.doubleValue() * 100.0 / total.doubleValue();
	}
}
