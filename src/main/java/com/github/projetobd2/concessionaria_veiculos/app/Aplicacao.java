package com.github.projetobd2.concessionaria_veiculos.app;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;

import javax.persistence.EntityManager;

import com.github.projetobd2.concessionaria_veiculos.carga.CargaDados;
import com.github.projetobd2.concessionaria_veiculos.dao.ClienteDAO;
import com.github.projetobd2.concessionaria_veiculos.dao.FuncionarioDAO;
import com.github.projetobd2.concessionaria_veiculos.dao.GenericDAO;
import com.github.projetobd2.concessionaria_veiculos.dao.JPAUtil;
import com.github.projetobd2.concessionaria_veiculos.dao.VeiculoDAO;
import com.github.projetobd2.concessionaria_veiculos.dao.VendaDAO;
import com.github.projetobd2.concessionaria_veiculos.modelo.Cliente;
import com.github.projetobd2.concessionaria_veiculos.modelo.DocumentoCliente;
import com.github.projetobd2.concessionaria_veiculos.modelo.Endereco;
import com.github.projetobd2.concessionaria_veiculos.modelo.FormaPagamento;
import com.github.projetobd2.concessionaria_veiculos.modelo.Funcionario;
import com.github.projetobd2.concessionaria_veiculos.modelo.ItemVenda;
import com.github.projetobd2.concessionaria_veiculos.modelo.Modelo;
import com.github.projetobd2.concessionaria_veiculos.modelo.Servico;
import com.github.projetobd2.concessionaria_veiculos.modelo.Veiculo;
import com.github.projetobd2.concessionaria_veiculos.modelo.Venda;

/**
 * Interface de console: CRUD de Cliente e de Veiculo, registro de venda,
 * relatorios e carga de dados. Cada acao usa um EntityManager novo.
 */
public class Aplicacao {

	private final Scanner in = new Scanner(System.in, "UTF-8");

	public static void main(String[] args) {
		try {
			new Aplicacao().menuPrincipal();
		} finally {
			JPAUtil.fechar();
		}
	}

	private void menuPrincipal() {
		System.out.println("Conectando ao banco...");
		executar(em -> System.out.println("Conectado. " + new ClienteDAO(em).contar() + " clientes cadastrados."));
		while (true) {
			System.out.println();
			System.out.println("===== CONCESSIONARIA DE VEICULOS =====");
			System.out.println("1 - Clientes");
			System.out.println("2 - Veiculos");
			System.out.println("3 - Registrar venda");
			System.out.println("4 - Consultar venda");
			System.out.println("5 - Relatorios (consultas JPQL e Criteria)");
			System.out.println("6 - Gerar carga de dados (APAGA os dados atuais)");
			System.out.println("0 - Sair");
			switch (lerInt("Opcao: ")) {
			case 1: menuClientes(); break;
			case 2: menuVeiculos(); break;
			case 3: executar(this::registrarVenda); break;
			case 4: executar(this::consultarVenda); break;
			case 5: executar(em -> new Relatorios(em).imprimirTodos()); break;
			case 6:
				if (lerTexto("Isso apaga todos os dados. Digite SIM para continuar: ").equalsIgnoreCase("SIM")) {
					executar(em -> new CargaDados(em).executar());
				}
				break;
			case 0: return;
			default: System.out.println("Opcao invalida.");
			}
		}
	}

	// =============================================================== clientes

	private void menuClientes() {
		while (true) {
			System.out.println();
			System.out.println("--- CLIENTES ---");
			System.out.println("1 - Listar (paginado)");
			System.out.println("2 - Buscar por CPF");
			System.out.println("3 - Cadastrar");
			System.out.println("4 - Alterar telefone / endereco");
			System.out.println("5 - Remover");
			System.out.println("0 - Voltar");
			switch (lerInt("Opcao: ")) {
			case 1: executar(this::listarClientes); break;
			case 2: executar(this::buscarCliente); break;
			case 3: executar(this::cadastrarCliente); break;
			case 4: executar(this::alterarCliente); break;
			case 5: executar(this::removerCliente); break;
			case 0: return;
			default: System.out.println("Opcao invalida.");
			}
		}
	}

	private void listarClientes(EntityManager em) {
		ClienteDAO dao = new ClienteDAO(em);
		int tamanho = 15;
		long total = dao.contar();
		int paginas = (int) Math.max(1, (total + tamanho - 1) / tamanho);
		int pagina = 0;
		while (true) {
			System.out.printf("%nPagina %d de %d (%d clientes)%n", pagina + 1, paginas, total);
			for (Cliente c : dao.listarPaginado(pagina, tamanho)) {
				System.out.println("  " + c);
			}
			String op = lerTexto("[P]roxima, [A]nterior, [S]air: ").toUpperCase();
			if (op.startsWith("P") && pagina < paginas - 1) {
				pagina++;
			} else if (op.startsWith("A") && pagina > 0) {
				pagina--;
			} else if (op.startsWith("S")) {
				return;
			}
		}
	}

	private void buscarCliente(EntityManager em) {
		Cliente c = new ClienteDAO(em).buscarPorCpf(lerTexto("CPF (000.000.000-00): "));
		if (c == null) {
			System.out.println("Cliente nao encontrado.");
			return;
		}
		System.out.println(c);
		System.out.println("Telefone: " + c.getTelefone() + " | Cadastro: " + c.getDataCadastro()
				+ " | Ultima alteracao: " + c.getDataAtualizacao());
		DocumentoCliente d = c.getDocumento();
		System.out.println(d == null ? "Sem CNH cadastrada." : "CNH " + d.getNumeroCnh() + " valida ate " + d.getValidadeCnh());
		System.out.println("Compras: " + new ClienteDAO(em).contarCompras(c.getId()));
	}

	private void cadastrarCliente(EntityManager em) {
		ClienteDAO dao = new ClienteDAO(em);
		String cpf = lerTexto("CPF (000.000.000-00): ");
		if (dao.buscarPorCpf(cpf) != null) {
			System.out.println("Ja existe cliente com esse CPF.");
			return;
		}
		Cliente c = new Cliente(lerTexto("Nome: "), cpf, lerTexto("Telefone: "),
				new Endereco(lerTexto("Logradouro: "), lerTexto("Cidade: "), lerTexto("UF: ").toUpperCase()),
				LocalDate.now());
		String cnh = lerTexto("Numero da CNH (Enter para pular): ");
		if (!cnh.isEmpty()) {
			c.setDocumento(new DocumentoCliente(cnh, LocalDate.parse(lerTexto("Validade da CNH (AAAA-MM-DD): "))));
		}
		dao.salvar(c);
		System.out.println("Cliente cadastrado: " + c);
	}

	private void alterarCliente(EntityManager em) {
		ClienteDAO dao = new ClienteDAO(em);
		Cliente c = dao.buscarPorCpf(lerTexto("CPF do cliente: "));
		if (c == null) {
			System.out.println("Cliente nao encontrado.");
			return;
		}
		System.out.println(c);
		String tel = lerTexto("Novo telefone (Enter mantem " + c.getTelefone() + "): ");
		if (!tel.isEmpty()) {
			c.setTelefone(tel);
		}
		String log = lerTexto("Novo logradouro (Enter mantem): ");
		if (!log.isEmpty()) {
			c.getEndereco().setLogradouro(log);
		}
		String cidade = lerTexto("Nova cidade (Enter mantem " + c.getEndereco().getCidade() + "): ");
		if (!cidade.isEmpty()) {
			c.alterarCidade(cidade, lerTexto("Nova UF: ").toUpperCase());
		}
		dao.atualizar(c);
		System.out.println("Cliente atualizado: " + c);
	}

	private void removerCliente(EntityManager em) {
		ClienteDAO dao = new ClienteDAO(em);
		Cliente c = dao.buscarPorCpf(lerTexto("CPF do cliente: "));
		if (c == null) {
			System.out.println("Cliente nao encontrado.");
			return;
		}
		long compras = dao.contarCompras(c.getId());
		if (compras > 0) {
			System.out.println("Cliente tem " + compras + " compras e nao pode ser removido.");
			return;
		}
		if (lerTexto("Remover " + c.getNome() + "? (S/N): ").equalsIgnoreCase("S")) {
			dao.remover(c.getId());
			System.out.println("Cliente removido.");
		}
	}

	// =============================================================== veiculos

	private void menuVeiculos() {
		while (true) {
			System.out.println();
			System.out.println("--- VEICULOS ---");
			System.out.println("1 - Listar estoque (nao vendidos)");
			System.out.println("2 - Pesquisar com filtros");
			System.out.println("3 - Cadastrar");
			System.out.println("4 - Alterar preco / cor");
			System.out.println("5 - Remover");
			System.out.println("0 - Voltar");
			switch (lerInt("Opcao: ")) {
			case 1: executar(em -> new VeiculoDAO(em).listarNaoVendidos().forEach(v -> System.out.println("  " + v))); break;
			case 2: executar(this::pesquisarVeiculos); break;
			case 3: executar(this::cadastrarVeiculo); break;
			case 4: executar(this::alterarVeiculo); break;
			case 5: executar(this::removerVeiculo); break;
			case 0: return;
			default: System.out.println("Opcao invalida.");
			}
		}
	}

	private void pesquisarVeiculos(EntityManager em) {
		System.out.println("Deixe em branco para nao filtrar.");
		String marca = lerTexto("Marca: ");
		String categoria = lerTexto("Categoria (Hatch, Seda, SUV, Picape, Minivan): ");
		Integer anoMin = lerIntOpcional("Ano minimo: ");
		Integer anoMax = lerIntOpcional("Ano maximo: ");
		BigDecimal precoMax = lerDecimalOpcional("Preco maximo: ");
		boolean estoque = lerTexto("Somente em estoque? (S/N): ").equalsIgnoreCase("S");
		List<Veiculo> r = new VeiculoDAO(em).filtrar(marca, categoria, anoMin, anoMax, precoMax, estoque);
		r.forEach(v -> System.out.println("  " + v));
		System.out.println(r.size() + " veiculos encontrados.");
	}

	private void cadastrarVeiculo(EntityManager em) {
		List<Modelo> modelos = em.createQuery(
				"SELECT m FROM Modelo m JOIN FETCH m.marca ma ORDER BY ma.nome, m.nome", Modelo.class).getResultList();
		for (Modelo m : modelos) {
			System.out.printf("  %3d - %s %s (%s)%n", m.getId(), m.getMarca().getNome(), m.getNome(), m.getCategoria());
		}
		Modelo modelo = em.find(Modelo.class, (long) lerInt("Id do modelo: "));
		if (modelo == null) {
			System.out.println("Modelo nao encontrado.");
			return;
		}
		Veiculo v = new Veiculo(lerTexto("Chassi (17 caracteres): ").toUpperCase(), lerTexto("Cor: "),
				lerInt("Ano de fabricacao: "), lerDecimal("Preco de tabela: "), modelo);
		new VeiculoDAO(em).salvar(v);
		System.out.println("Veiculo cadastrado: " + v);
	}

	private void alterarVeiculo(EntityManager em) {
		VeiculoDAO dao = new VeiculoDAO(em);
		Veiculo v = dao.buscarComModelo((long) lerInt("Id do veiculo: "));
		if (v == null) {
			System.out.println("Veiculo nao encontrado.");
			return;
		}
		System.out.println(v);
		BigDecimal preco = lerDecimalOpcional("Novo preco (Enter mantem): ");
		if (preco != null) {
			v.setPrecoTabela(preco);
		}
		String cor = lerTexto("Nova cor (Enter mantem): ");
		if (!cor.isEmpty()) {
			v.setCor(cor);
		}
		dao.atualizar(v);
		System.out.println("Veiculo atualizado: " + v);
	}

	private void removerVeiculo(EntityManager em) {
		VeiculoDAO dao = new VeiculoDAO(em);
		Long id = (long) lerInt("Id do veiculo: ");
		if (dao.foiVendido(id)) {
			System.out.println("Veiculo ja foi vendido e nao pode ser removido.");
			return;
		}
		System.out.println(dao.remover(id) ? "Veiculo removido." : "Veiculo nao encontrado.");
	}

	// ================================================================= vendas

	private void registrarVenda(EntityManager em) {
		Cliente cliente = new ClienteDAO(em).buscarPorCpf(lerTexto("CPF do cliente: "));
		if (cliente == null) {
			System.out.println("Cliente nao encontrado. Cadastre-o antes.");
			return;
		}
		Funcionario vendedor = new FuncionarioDAO(em).buscarPorMatricula(lerTexto("Matricula do vendedor (ex.: F0002): ").toUpperCase());
		if (vendedor == null) {
			System.out.println("Funcionario nao encontrado.");
			return;
		}
		System.out.println("Cliente: " + cliente.getNome() + " | Vendedor: " + vendedor.getNome()
				+ " | Loja: " + vendedor.getLoja().getNome());

		FormaPagamento[] formas = FormaPagamento.values();
		for (int i = 0; i < formas.length; i++) {
			System.out.println("  " + (i + 1) + " - " + formas[i]);
		}
		FormaPagamento forma = formas[Math.max(1, Math.min(formas.length, lerInt("Forma de pagamento: "))) - 1];

		Venda venda = new Venda(LocalDateTime.now(), forma, cliente, vendedor, vendedor.getLoja());
		VeiculoDAO veiculoDAO = new VeiculoDAO(em);
		GenericDAO<Servico, Long> servicoDAO = new GenericDAO<>(em, Servico.class);

		while (true) {
			String tipo = lerTexto("Adicionar item: [V]eiculo, [S]ervico ou [F]inalizar: ").toUpperCase();
			if (tipo.startsWith("F")) {
				break;
			} else if (tipo.startsWith("V")) {
				Veiculo v = veiculoDAO.buscarComModelo((long) lerInt("Id do veiculo (veja Veiculos > Listar estoque): "));
				if (v == null || veiculoDAO.foiVendido(v.getId())) {
					System.out.println("Veiculo inexistente ou ja vendido.");
					continue;
				}
				BigDecimal desconto = lerDecimalOpcional("Desconto em R$ (Enter = 0): ");
				venda.adicionarItem(ItemVenda.deVeiculo(v, v.getPrecoTabela(), desconto == null ? BigDecimal.ZERO : desconto));
				System.out.println("  + " + v);
			} else if (tipo.startsWith("S")) {
				servicoDAO.listarTodos().forEach(s -> System.out.println("    " + s));
				Servico s = servicoDAO.buscarPorId((long) lerInt("Id do servico: "));
				if (s == null) {
					System.out.println("Servico nao encontrado.");
					continue;
				}
				int qtd = lerInt("Quantidade: ");
				BigDecimal desconto = lerDecimalOpcional("Desconto em R$ (Enter = 0): ");
				venda.adicionarItem(ItemVenda.deServico(s, qtd, s.getPrecoTabela(), desconto == null ? BigDecimal.ZERO : desconto));
				System.out.println("  + " + s.getNome());
			}
		}
		if (venda.getItens().isEmpty()) {
			System.out.println("Venda sem itens, cancelada.");
			return;
		}
		System.out.println("Total: " + Relatorios.moeda(venda.calcularTotal()));
		if (lerTexto("Confirmar venda? (S/N): ").equalsIgnoreCase("S")) {
			new VendaDAO(em).salvar(venda); // cascade ALL grava os itens junto
			System.out.println("Venda #" + venda.getId() + " registrada.");
		}
	}

	private void consultarVenda(EntityManager em) {
		VendaDAO dao = new VendaDAO(em);
		System.out.println("Ultimas vendas:");
		for (Venda v : dao.ultimasVendas(5)) {
			System.out.printf("  #%d %s - %s - %s%n", v.getId(), v.getDataHora().toLocalDate(),
					v.getCliente().getNome(), v.getLoja().getNome());
		}
		Venda v = dao.buscarComItens((long) lerInt("Id da venda: "));
		if (v == null) {
			System.out.println("Venda nao encontrada.");
		} else {
			Relatorios.imprimirVenda(v);
		}
	}

	// ============================================================ utilitarios

	/** Abre um EntityManager, executa a acao, mostra erros de forma legivel e fecha. */
	private void executar(Consumer<EntityManager> acao) {
		EntityManager em = JPAUtil.getEntityManager();
		try {
			acao.accept(em);
		} catch (RuntimeException e) {
			Throwable causa = e;
			while (causa.getCause() != null) {
				causa = causa.getCause();
			}
			System.out.println("ERRO: " + causa.getMessage());
		} finally {
			em.close();
		}
	}

	private String lerTexto(String prompt) {
		System.out.print(prompt);
		return in.hasNextLine() ? in.nextLine().trim() : "0";
	}

	private int lerInt(String prompt) {
		while (true) {
			String s = lerTexto(prompt);
			try {
				return Integer.parseInt(s);
			} catch (NumberFormatException e) {
				System.out.println("Digite um numero inteiro.");
			}
		}
	}

	private Integer lerIntOpcional(String prompt) {
		String s = lerTexto(prompt);
		try {
			return s.isEmpty() ? null : Integer.valueOf(s);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private BigDecimal lerDecimal(String prompt) {
		while (true) {
			BigDecimal v = lerDecimalOpcional(prompt);
			if (v != null) {
				return v;
			}
			System.out.println("Digite um valor (ex.: 1500,00).");
		}
	}

	/** Aceita 1500,50 ou 1500.50 ou 1.500,50. */
	private BigDecimal lerDecimalOpcional(String prompt) {
		String s = lerTexto(prompt);
		if (s.isEmpty()) {
			return null;
		}
		if (s.contains(",")) {
			s = s.replace(".", "").replace(",", ".");
		}
		try {
			return new BigDecimal(s);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
