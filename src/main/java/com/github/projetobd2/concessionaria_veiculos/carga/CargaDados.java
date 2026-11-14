package com.github.projetobd2.concessionaria_veiculos.carga;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.persistence.EntityManager;

import com.github.projetobd2.concessionaria_veiculos.dao.JPAUtil;
import com.github.projetobd2.concessionaria_veiculos.modelo.Cliente;
import com.github.projetobd2.concessionaria_veiculos.modelo.DocumentoCliente;
import com.github.projetobd2.concessionaria_veiculos.modelo.Endereco;
import com.github.projetobd2.concessionaria_veiculos.modelo.FormaPagamento;
import com.github.projetobd2.concessionaria_veiculos.modelo.Funcionario;
import com.github.projetobd2.concessionaria_veiculos.modelo.ItemVenda;
import com.github.projetobd2.concessionaria_veiculos.modelo.Loja;
import com.github.projetobd2.concessionaria_veiculos.modelo.Marca;
import com.github.projetobd2.concessionaria_veiculos.modelo.Modelo;
import com.github.projetobd2.concessionaria_veiculos.modelo.Servico;
import com.github.projetobd2.concessionaria_veiculos.modelo.Veiculo;
import com.github.projetobd2.concessionaria_veiculos.modelo.Venda;

/**
 * Gera dados ficticios, mas coerentes, para o OLTP:
 * 5 lojas, 30 funcionarios, 200 clientes, 7 marcas, 32 modelos, 15 servicos,
 * cerca de 1.000 veiculos e 2.000 vendas entre jan/2024 e out/2026.
 *
 * A semente do Random e fixa: todo integrante que rodar a carga gera
 * exatamente os mesmos dados.
 *
 * Padroes colocados de proposito para o OLAP e a mineracao terem o que achar:
 * - sazonalidade (dezembro forte, janeiro/fevereiro fracos) e crescimento ano a ano;
 * - clientes com perfis diferentes (poucos clientes frequentes e de alto valor);
 * - servicos comprados juntos (pelicula + polimento, pneus + alinhamento, revisao + oleo);
 * - cliente tende a comprar na loja da sua regiao.
 */
public class CargaDados {

	private static final LocalDate INICIO = LocalDate.of(2024, 1, 2);
	private static final LocalDate FIM = LocalDate.of(2026, 10, 31);
	private static final int TOTAL_CLIENTES = 200;
	private static final int TOTAL_VENDAS = 2000;
	private static final int VEICULOS_EM_ESTOQUE = 100;

	private final Random rnd = new Random(2026);
	private final EntityManager em;

	private final Set<String> cpfsUsados = new HashSet<>();
	private final Set<String> chassisUsados = new HashSet<>();
	private int proximaMatricula = 1;

	private final List<Loja> lojas = new ArrayList<>();
	private final List<Integer> pesoLojas = new ArrayList<>();
	private final Map<Loja, List<Funcionario>> vendedoresPorLoja = new HashMap<>();
	private final Map<Loja, List<Funcionario>> consultoresPorLoja = new HashMap<>();
	private final Map<Funcionario, Integer> desempenhoVendedor = new HashMap<>();
	private final Map<String, Loja> lojaDaRegiao = new HashMap<>();

	private final List<Cliente> clientes = new ArrayList<>();
	private final List<Integer> pesoClientes = new ArrayList<>();

	private final List<Modelo> modelos = new ArrayList<>();
	private final List<Integer> pesoModelos = new ArrayList<>();
	private final Map<Modelo, Integer> precoBaseModelo = new HashMap<>();
	private final Map<String, String> wmiDaMarca = new HashMap<>();

	private final Map<String, Servico> servicos = new LinkedHashMap<>();

	public CargaDados(EntityManager em) {
		this.em = em;
	}

	public static void main(String[] args) {
		EntityManager em = JPAUtil.getEntityManager();
		try {
			new CargaDados(em).executar();
		} finally {
			em.close();
			JPAUtil.fechar();
		}
	}

	/** Apaga tudo e gera os dados novamente, em uma unica transacao. */
	public void executar() {
		long inicio = System.currentTimeMillis();
		em.getTransaction().begin();
		try {
			limparBanco();
			criarLojasEFuncionarios();
			criarClientes();
			criarMarcasEModelos();
			criarServicos();
			criarVendas();
			criarEstoque();
			em.getTransaction().commit();
		} catch (RuntimeException e) {
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			throw e;
		}
		em.clear();
		System.out.printf("%nCarga concluida em %.1f s.%n", (System.currentTimeMillis() - inicio) / 1000.0);
		imprimirContagens(em);
	}

	/** Conta as linhas de cada tabela (serve de evidencia da carga). */
	public static void imprimirContagens(EntityManager em) {
		String[] tabelas = { "pessoa", "cliente", "funcionario", "documento_cliente", "loja", "marca",
				"modelo", "veiculo", "servico", "venda", "item_venda" };
		System.out.println("Linhas por tabela:");
		for (String t : tabelas) {
			Number n = (Number) em.createNativeQuery("SELECT COUNT(*) FROM " + t).getSingleResult();
			System.out.printf("  %-18s %6d%n", t, n.longValue());
		}
	}

	private void limparBanco() {
		em.createNativeQuery("TRUNCATE TABLE item_venda, venda, documento_cliente, veiculo, servico, "
				+ "modelo, marca, cliente, funcionario, pessoa, loja RESTART IDENTITY CASCADE").executeUpdate();
	}

	// ------------------------------------------------------------------ lojas

	private void criarLojasEFuncionarios() {
		Loja paralela = novaLoja("Rota Veiculos Paralela", "Av. Luis Viana Filho, 7200", "Salvador", 30, 5);
		Loja iguatemi = novaLoja("Rota Veiculos Iguatemi", "Av. Tancredo Neves, 1500", "Salvador", 25, 4);
		Loja lauro = novaLoja("Rota Veiculos Lauro de Freitas", "Estrada do Coco, 3100", "Lauro de Freitas", 15, 3);
		Loja feira = novaLoja("Rota Veiculos Feira de Santana", "Av. Joao Durval Carneiro, 2800", "Feira de Santana", 18, 4);
		Loja conquista = novaLoja("Rota Veiculos Vitoria da Conquista", "Av. Olivia Flores, 950", "Vitoria da Conquista", 12, 3);

		lojaDaRegiao.put("Salvador", paralela);
		lojaDaRegiao.put("Simoes Filho", iguatemi);
		lojaDaRegiao.put("Lauro de Freitas", lauro);
		lojaDaRegiao.put("Camacari", lauro);
		lojaDaRegiao.put("Feira de Santana", feira);
		lojaDaRegiao.put("Alagoinhas", feira);
		lojaDaRegiao.put("Santo Antonio de Jesus", feira);
		lojaDaRegiao.put("Vitoria da Conquista", conquista);
		lojaDaRegiao.put("Ilheus", conquista);
		lojaDaRegiao.put("Itabuna", conquista);
	}

	private Loja novaLoja(String nome, String logradouro, String cidade, int peso, int qtdVendedores) {
		Loja loja = new Loja(nome, new Endereco(logradouro, cidade, "BA"));
		vendedoresPorLoja.put(loja, new ArrayList<>());
		consultoresPorLoja.put(loja, new ArrayList<>());

		loja.adicionarFuncionario(novoFuncionario(cidade, "Gerente"));
		for (int i = 0; i < qtdVendedores; i++) {
			Funcionario f = novoFuncionario(cidade, "Vendedor");
			loja.adicionarFuncionario(f);
			vendedoresPorLoja.get(loja).add(f);
			desempenhoVendedor.put(f, 2 + rnd.nextInt(7)); // vendedores com desempenho diferente
		}
		for (int i = 0; i < 2; i++) {
			Funcionario f = novoFuncionario(cidade, "Consultor de Servicos");
			loja.adicionarFuncionario(f);
			consultoresPorLoja.get(loja).add(f);
		}
		em.persist(loja); // cascade PERSIST salva os funcionarios
		lojas.add(loja);
		pesoLojas.add(peso);
		return loja;
	}

	private Funcionario novoFuncionario(String cidade, String cargo) {
		Endereco end = new Endereco(logradouro(), cidade, "BA");
		Funcionario f = new Funcionario(nomeCompleto(), gerarCpf(), telefone(cidade), end,
				String.format("F%04d", proximaMatricula++), cargo);
		return f;
	}

	// --------------------------------------------------------------- clientes

	private static final Object[][] CIDADES = {
		// cidade, UF, peso
		{ "Salvador", "BA", 45 }, { "Lauro de Freitas", "BA", 10 }, { "Camacari", "BA", 7 },
		{ "Simoes Filho", "BA", 3 }, { "Feira de Santana", "BA", 12 }, { "Alagoinhas", "BA", 3 },
		{ "Santo Antonio de Jesus", "BA", 3 }, { "Vitoria da Conquista", "BA", 8 }, { "Ilheus", "BA", 3 },
		{ "Itabuna", "BA", 3 }, { "Aracaju", "SE", 2 }, { "Recife", "PE", 1 } };

	private void criarClientes() {
		List<Integer> pesosCidade = new ArrayList<>();
		for (Object[] c : CIDADES) {
			pesosCidade.add((Integer) c[2]);
		}
		for (int i = 0; i < TOTAL_CLIENTES; i++) {
			Object[] cidade = CIDADES[sortear(pesosCidade)];
			String nomeCidade = (String) cidade[0];

			// 60% ja eram clientes em 2023; o restante entra ao longo do periodo
			LocalDate cadastro = rnd.nextDouble() < 0.6
					? dataEntre(LocalDate.of(2023, 1, 2), LocalDate.of(2023, 12, 20))
					: dataEntre(LocalDate.of(2024, 1, 2), LocalDate.of(2026, 9, 30));

			Cliente c = new Cliente(nomeCompleto(), gerarCpf(), telefone(nomeCidade),
					new Endereco(logradouro(), nomeCidade, (String) cidade[1]), cadastro);

			if (rnd.nextDouble() < 0.75) {
				c.setDocumento(new DocumentoCliente(digitos(11),
						LocalDate.of(2026 + rnd.nextInt(9), 1 + rnd.nextInt(12), 1 + rnd.nextInt(28))));
			}
			em.persist(c); // cascade ALL salva o documento

			// perfis: 10% frequentes, 30% medios, 60% ocasionais
			double p = rnd.nextDouble();
			clientes.add(c);
			pesoClientes.add(p < 0.10 ? 12 : p < 0.40 ? 4 : 1);
		}
	}

	// ----------------------------------------------------------------- marcas

	private void criarMarcasEModelos() {
		Object[][] catalogo = {
			// marca, WMI do chassi, modelos {nome, categoria, preco base em 2024}
			{ "Fiat", "9BD", new Object[][] { { "Mobi", "Hatch", 72000 }, { "Argo", "Hatch", 88000 },
				{ "Cronos", "Seda", 98000 }, { "Strada", "Picape", 105000 }, { "Pulse", "SUV", 115000 },
				{ "Fastback", "SUV", 135000 }, { "Toro", "Picape", 160000 } } },
			{ "Volkswagen", "9BW", new Object[][] { { "Polo", "Hatch", 95000 }, { "Saveiro", "Picape", 100000 },
				{ "Virtus", "Seda", 115000 }, { "Nivus", "SUV", 130000 }, { "T-Cross", "SUV", 150000 },
				{ "Amarok", "Picape", 300000 } } },
			{ "Chevrolet", "9BG", new Object[][] { { "Onix", "Hatch", 90000 }, { "Onix Plus", "Seda", 100000 },
				{ "Spin", "Minivan", 120000 }, { "Montana", "Picape", 130000 }, { "Tracker", "SUV", 135000 },
				{ "S10", "Picape", 250000 } } },
			{ "Toyota", "9BR", new Object[][] { { "Yaris", "Hatch", 110000 }, { "Corolla", "Seda", 160000 },
				{ "Corolla Cross", "SUV", 170000 }, { "Hilux", "Picape", 290000 } } },
			{ "Hyundai", "9BH", new Object[][] { { "HB20", "Hatch", 85000 }, { "HB20S", "Seda", 95000 },
				{ "Creta", "SUV", 140000 } } },
			{ "Jeep", "988", new Object[][] { { "Renegade", "SUV", 130000 }, { "Compass", "SUV", 190000 },
				{ "Commander", "SUV", 230000 } } },
			{ "Honda", "93H", new Object[][] { { "City", "Seda", 120000 }, { "HR-V", "SUV", 160000 },
				{ "Civic", "Seda", 260000 } } } };

		int[] pesoMarca = { 22, 20, 20, 12, 12, 8, 6 };
		for (int m = 0; m < catalogo.length; m++) {
			Marca marca = new Marca((String) catalogo[m][0]);
			wmiDaMarca.put(marca.getNome(), (String) catalogo[m][1]);
			Object[][] lista = (Object[][]) catalogo[m][2];
			for (Object[] mod : lista) {
				Modelo modelo = marca.adicionarModelo((String) mod[0], (String) mod[1]);
				int preco = (Integer) mod[2];
				precoBaseModelo.put(modelo, preco);
				modelos.add(modelo);
				// modelos mais baratos vendem mais; marca mais popular vende mais
				pesoModelos.add(pesoMarca[m] * 1_000_000 / preco / lista.length * 10);
			}
			em.persist(marca); // cascade PERSIST salva os modelos
		}
	}

	private void criarServicos() {
		Object[][] lista = {
			{ "Revisao 10.000 km", 650 }, { "Revisao 20.000 km", 890 }, { "Revisao 30.000 km", 1250 },
			{ "Troca de oleo e filtro", 380 }, { "Alinhamento e balanceamento", 220 },
			{ "Troca de pneus (jogo)", 2400 }, { "Troca de pastilhas de freio", 600 },
			{ "Pelicula (insulfilm)", 900 }, { "Polimento e cristalizacao", 750 },
			{ "Higienizacao interna", 350 }, { "Emplacamento e documentacao", 1100 },
			{ "Instalacao de alarme", 1200 }, { "Instalacao de multimidia", 1800 },
			{ "Kit tapetes e acessorios", 450 }, { "Garantia estendida 2 anos", 3500 } };
		for (Object[] s : lista) {
			Servico servico = new Servico((String) s[0], BigDecimal.valueOf((Integer) s[1]).setScale(2));
			em.persist(servico);
			servicos.put(servico.getNome(), servico);
		}
	}

	// ----------------------------------------------------------------- vendas

	private void criarVendas() {
		List<LocalDateTime> datas = new ArrayList<>();
		while (datas.size() < TOTAL_VENDAS) {
			LocalDate d = dataEntre(INICIO, FIM);
			if (d.getDayOfWeek() == DayOfWeek.SUNDAY) {
				continue;
			}
			// aceita a data com probabilidade proporcional a sazonalidade e ao crescimento
			if (rnd.nextDouble() * 1.6 > fatorMes(d.getMonthValue()) * fatorAno(d.getYear())) {
				continue;
			}
			datas.add(d.atTime(8 + rnd.nextInt(11), rnd.nextInt(60)));
		}
		Collections.sort(datas); // ids das vendas em ordem cronologica

		for (LocalDateTime data : datas) {
			Cliente cliente = sortearCliente(data.toLocalDate());
			Loja loja = escolherLoja(cliente);
			boolean vendaDeVeiculo = rnd.nextDouble() < 0.45;
			Venda venda = vendaDeVeiculo
					? montarVendaDeVeiculo(data, cliente, loja)
					: montarVendaDeServico(data, cliente, loja);
			em.persist(venda); // cascade ALL salva os itens
		}
	}

	private Venda montarVendaDeVeiculo(LocalDateTime data, Cliente cliente, Loja loja) {
		FormaPagamento forma = sortear(new FormaPagamento[] { FormaPagamento.FINANCIAMENTO,
				FormaPagamento.A_VISTA, FormaPagamento.CONSORCIO, FormaPagamento.CARTAO }, 45, 22, 18, 15);
		Funcionario vendedor = sortearVendedor(loja);
		Venda venda = new Venda(data, forma, cliente, vendedor, loja);

		int ano = data.getYear();
		int anoFabricacao = (data.getMonthValue() <= 3 && rnd.nextDouble() < 0.4) ? ano - 1 : ano;
		Modelo modelo = modelos.get(sortear(pesoModelos));
		Veiculo veiculo = novoVeiculo(modelo, anoFabricacao, ano);
		em.persist(veiculo);

		BigDecimal preco = veiculo.getPrecoTabela();
		BigDecimal desconto = BigDecimal.ZERO;
		if (rnd.nextDouble() < 0.6) {
			double pct = forma == FormaPagamento.A_VISTA ? 0.03 + rnd.nextDouble() * 0.05 : 0.01 + rnd.nextDouble() * 0.05;
			desconto = arredondar(preco.multiply(BigDecimal.valueOf(pct)), 50);
		}
		venda.adicionarItem(ItemVenda.deVeiculo(veiculo, preco, desconto));

		String categoria = modelo.getCategoria();
		boolean grande = categoria.equals("SUV") || categoria.equals("Picape");
		if (rnd.nextDouble() < 0.85) {
			// 10% das vezes o emplacamento sai como cortesia (desconto total)
			adicionarServico(venda, "Emplacamento e documentacao", ano, rnd.nextDouble() < 0.10 ? 1.0 : 0.0);
		}
		boolean pelicula = rnd.nextDouble() < (grande ? 0.70 : 0.45);
		if (pelicula) {
			adicionarServico(venda, "Pelicula (insulfilm)", ano, descontoServico());
		}
		if (rnd.nextDouble() < (pelicula ? 0.45 : 0.10)) {
			adicionarServico(venda, "Polimento e cristalizacao", ano, descontoServico());
		}
		if (rnd.nextDouble() < 0.30) {
			adicionarServico(venda, "Kit tapetes e acessorios", ano, descontoServico());
		}
		if (rnd.nextDouble() < (grande ? 0.35 : 0.15)) {
			adicionarServico(venda, "Instalacao de alarme", ano, descontoServico());
		}
		if (rnd.nextDouble() < (categoria.equals("Hatch") ? 0.25 : 0.08)) {
			adicionarServico(venda, "Instalacao de multimidia", ano, descontoServico());
		}
		if (rnd.nextDouble() < (forma == FormaPagamento.FINANCIAMENTO ? 0.35 : 0.10)) {
			adicionarServico(venda, "Garantia estendida 2 anos", ano, descontoServico());
		}
		return venda;
	}

	private Venda montarVendaDeServico(LocalDateTime data, Cliente cliente, Loja loja) {
		FormaPagamento forma = rnd.nextDouble() < 0.62 ? FormaPagamento.CARTAO : FormaPagamento.A_VISTA;
		List<Funcionario> consultores = consultoresPorLoja.get(loja);
		Funcionario consultor = consultores.get(rnd.nextInt(consultores.size()));
		Venda venda = new Venda(data, forma, cliente, consultor, loja);
		int ano = data.getYear();

		int tipo = sortear(java.util.Arrays.asList(40, 15, 15, 10, 15, 5));
		switch (tipo) {
		case 0: // revisao programada
			String[] revisoes = { "Revisao 10.000 km", "Revisao 20.000 km", "Revisao 30.000 km" };
			adicionarServico(venda, revisoes[rnd.nextInt(3)], ano, descontoServico());
			if (rnd.nextDouble() < 0.70) {
				adicionarServico(venda, "Troca de oleo e filtro", ano, descontoServico());
			}
			if (rnd.nextDouble() < 0.30) {
				adicionarServico(venda, "Alinhamento e balanceamento", ano, descontoServico());
			}
			break;
		case 1: // so oleo
			adicionarServico(venda, "Troca de oleo e filtro", ano, descontoServico());
			break;
		case 2: // pneus
			adicionarServico(venda, "Troca de pneus (jogo)", ano, descontoServico());
			if (rnd.nextDouble() < 0.85) {
				adicionarServico(venda, "Alinhamento e balanceamento", ano, descontoServico());
			}
			break;
		case 3: // freios
			adicionarServico(venda, "Troca de pastilhas de freio", ano, descontoServico());
			if (rnd.nextDouble() < 0.20) {
				adicionarServico(venda, "Alinhamento e balanceamento", ano, descontoServico());
			}
			break;
		case 4: // estetica
			adicionarServico(venda, "Higienizacao interna", ano, descontoServico());
			if (rnd.nextDouble() < 0.60) {
				adicionarServico(venda, "Polimento e cristalizacao", ano, descontoServico());
			}
			break;
		default: // acessorios
			adicionarServico(venda, rnd.nextBoolean() ? "Instalacao de alarme" : "Instalacao de multimidia",
					ano, descontoServico());
			if (rnd.nextDouble() < 0.40) {
				adicionarServico(venda, "Pelicula (insulfilm)", ano, descontoServico());
			}
		}
		return venda;
	}

	/** Veiculos novos que ficam no estoque (nao vendidos). */
	private void criarEstoque() {
		for (int i = 0; i < VEICULOS_EM_ESTOQUE; i++) {
			Modelo modelo = modelos.get(sortear(pesoModelos));
			int ano = rnd.nextDouble() < 0.8 ? 2026 : 2025;
			em.persist(novoVeiculo(modelo, ano, 2026));
		}
	}

	private Veiculo novoVeiculo(Modelo modelo, int anoFabricacao, int anoPreco) {
		String cor = sortear(new String[] { "Branco", "Prata", "Preto", "Cinza", "Vermelho", "Azul" },
				30, 22, 20, 18, 6, 4);
		double reajuste = Math.pow(1.05, anoPreco - 2024) * (0.97 + rnd.nextDouble() * 0.06);
		BigDecimal preco = arredondar(BigDecimal.valueOf(precoBaseModelo.get(modelo) * reajuste), 100);
		return new Veiculo(gerarChassi(modelo.getMarca().getNome(), anoFabricacao), cor, anoFabricacao, preco, modelo);
	}

	private void adicionarServico(Venda venda, String nome, int ano, double pctDesconto) {
		Servico s = servicos.get(nome);
		BigDecimal preco = s.getPrecoTabela().multiply(BigDecimal.valueOf(Math.pow(1.05, ano - 2024)))
				.setScale(2, RoundingMode.HALF_UP);
		BigDecimal desconto = preco.multiply(BigDecimal.valueOf(pctDesconto)).setScale(2, RoundingMode.HALF_UP);
		venda.adicionarItem(ItemVenda.deServico(s, 1, preco, desconto));
	}

	/** 25% dos servicos saem com 5% a 15% de desconto. */
	private double descontoServico() {
		return rnd.nextDouble() < 0.25 ? 0.05 + rnd.nextInt(3) * 0.05 : 0.0;
	}

	private Cliente sortearCliente(LocalDate data) {
		for (int tentativa = 0; tentativa < 100; tentativa++) {
			Cliente c = clientes.get(sortear(pesoClientes));
			if (!c.getDataCadastro().isAfter(data)) {
				return c;
			}
		}
		// garante um cliente ja cadastrado na data
		for (Cliente c : clientes) {
			if (!c.getDataCadastro().isAfter(data)) {
				return c;
			}
		}
		throw new IllegalStateException("Nenhum cliente cadastrado em " + data);
	}

	/** 70% das vezes o cliente compra na loja da sua regiao. */
	private Loja escolherLoja(Cliente c) {
		Loja daRegiao = lojaDaRegiao.get(c.getEndereco().getCidade());
		if (daRegiao != null && rnd.nextDouble() < 0.70) {
			if (daRegiao == lojaDaRegiao.get("Salvador") && rnd.nextBoolean()) {
				return lojaDaRegiao.get("Simoes Filho"); // Salvador tem duas lojas
			}
			return daRegiao;
		}
		return lojas.get(sortear(pesoLojas));
	}

	private Funcionario sortearVendedor(Loja loja) {
		List<Funcionario> vendedores = vendedoresPorLoja.get(loja);
		List<Integer> pesos = new ArrayList<>();
		for (Funcionario f : vendedores) {
			pesos.add(desempenhoVendedor.get(f));
		}
		return vendedores.get(sortear(pesos));
	}

	// ------------------------------------------------------------- utilitarios

	private static double fatorMes(int mes) {
		double[] f = { 0.75, 0.80, 0.95, 0.95, 1.00, 1.00, 1.10, 1.00, 0.95, 1.00, 1.10, 1.35 };
		return f[mes - 1];
	}

	private static double fatorAno(int ano) {
		return ano == 2024 ? 0.90 : ano == 2025 ? 1.00 : 1.15;
	}

	private int sortear(List<Integer> pesos) {
		int total = 0;
		for (int p : pesos) {
			total += p;
		}
		int r = rnd.nextInt(total);
		for (int i = 0; i < pesos.size(); i++) {
			r -= pesos.get(i);
			if (r < 0) {
				return i;
			}
		}
		return pesos.size() - 1;
	}

	private <T> T sortear(T[] opcoes, Integer... pesos) {
		return opcoes[sortear(java.util.Arrays.asList(pesos))];
	}

	private LocalDate dataEntre(LocalDate inicio, LocalDate fim) {
		long dias = ChronoUnit.DAYS.between(inicio, fim);
		return inicio.plusDays((long) (rnd.nextDouble() * (dias + 1)));
	}

	private static BigDecimal arredondar(BigDecimal valor, int multiplo) {
		BigDecimal m = BigDecimal.valueOf(multiplo);
		return valor.divide(m, 0, RoundingMode.HALF_UP).multiply(m).setScale(2);
	}

	private static final String[] NOMES = { "Ana", "Beatriz", "Camila", "Daniela", "Eduarda", "Fernanda",
		"Gabriela", "Helena", "Isabela", "Juliana", "Larissa", "Mariana", "Natalia", "Patricia", "Raquel",
		"Sabrina", "Tatiane", "Vanessa", "Yasmin", "Luana", "Carla", "Priscila", "Joana", "Leticia",
		"Adriano", "Bruno", "Carlos", "Diego", "Eduardo", "Felipe", "Gustavo", "Henrique", "Igor", "Joao",
		"Lucas", "Marcos", "Nelson", "Otavio", "Pedro", "Rafael", "Sergio", "Tiago", "Vinicius", "Wagner",
		"Matheus", "Leonardo", "Rodrigo", "Caio", "Danilo", "Renato" };

	private static final String[] SOBRENOMES = { "Silva", "Santos", "Oliveira", "Souza", "Lima", "Pereira",
		"Costa", "Ferreira", "Almeida", "Carvalho", "Ribeiro", "Gomes", "Martins", "Araujo", "Barbosa",
		"Rocha", "Dias", "Nascimento", "Moreira", "Cardoso", "Teixeira", "Mendes", "Freitas", "Vieira",
		"Nunes", "Conceicao", "Bispo", "Reis", "Andrade", "Brito", "Pinto", "Queiroz", "Matos", "Sampaio" };

	private static final String[] RUAS = { "Rua das Flores", "Rua Chile", "Av. Sete de Setembro",
		"Rua da Graca", "Rua Barao de Cotegipe", "Av. Oceanica", "Rua do Imperador", "Rua Rui Barbosa",
		"Av. Getulio Vargas", "Rua Castro Alves", "Travessa Sao Jose", "Rua Duque de Caxias",
		"Av. Juracy Magalhaes", "Rua Jose Bonifacio", "Rua Tiradentes", "Av. Antonio Carlos Magalhaes" };

	private String nomeCompleto() {
		String s1 = SOBRENOMES[rnd.nextInt(SOBRENOMES.length)];
		String s2;
		do {
			s2 = SOBRENOMES[rnd.nextInt(SOBRENOMES.length)];
		} while (s2.equals(s1));
		return NOMES[rnd.nextInt(NOMES.length)] + " " + s1 + " " + s2;
	}

	private String logradouro() {
		return RUAS[rnd.nextInt(RUAS.length)] + ", " + (10 + rnd.nextInt(1990));
	}

	private String telefone(String cidade) {
		String ddd;
		switch (cidade) {
		case "Feira de Santana": case "Alagoinhas": case "Santo Antonio de Jesus": ddd = "75"; break;
		case "Vitoria da Conquista": ddd = "77"; break;
		case "Ilheus": case "Itabuna": ddd = "73"; break;
		case "Aracaju": ddd = "79"; break;
		case "Recife": ddd = "81"; break;
		default: ddd = "71";
		}
		return "(" + ddd + ") 9" + digitos(4) + "-" + digitos(4);
	}

	private String digitos(int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++) {
			sb.append(rnd.nextInt(10));
		}
		return sb.toString();
	}

	/** CPF com digitos verificadores validos e sem repeticao. */
	private String gerarCpf() {
		String cpf;
		do {
			int[] d = new int[11];
			for (int i = 0; i < 9; i++) {
				d[i] = rnd.nextInt(10);
			}
			int soma = 0;
			for (int i = 0; i < 9; i++) {
				soma += d[i] * (10 - i);
			}
			d[9] = soma % 11 < 2 ? 0 : 11 - soma % 11;
			soma = 0;
			for (int i = 0; i < 10; i++) {
				soma += d[i] * (11 - i);
			}
			d[10] = soma % 11 < 2 ? 0 : 11 - soma % 11;
			cpf = String.format("%d%d%d.%d%d%d.%d%d%d-%d%d", d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7],
					d[8], d[9], d[10]);
		} while (!cpfsUsados.add(cpf));
		return cpf;
	}

	/** Chassi de 17 caracteres: WMI da marca + 14 caracteres (sem I, O e Q). */
	private String gerarChassi(String marca, int ano) {
		String letras = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";
		String anoCod = String.valueOf("ABCDEFGHJKLMNPRSTVWXY123456789".charAt((ano - 2010) % 30));
		String chassi;
		do {
			StringBuilder sb = new StringBuilder(wmiDaMarca.get(marca));
			for (int i = 0; i < 6; i++) {
				sb.append(letras.charAt(rnd.nextInt(letras.length())));
			}
			sb.append(anoCod);
			for (int i = 0; i < 7; i++) {
				sb.append(i < 1 ? letras.charAt(rnd.nextInt(letras.length())) : (char) ('0' + rnd.nextInt(10)));
			}
			chassi = sb.toString();
		} while (!chassisUsados.add(chassi));
		return chassi;
	}
}
