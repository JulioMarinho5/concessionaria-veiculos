"""
mineracao.py - Mineracao de dados sobre o DW da concessionaria
Integrante 5 - Analista de BI e Mineracao

Requisito 4.3.4: duas tecnicas aplicadas aos dados do DW.
  1) Regras de associacao (Apriori) sobre os itens de cada venda.
  2) Agrupamento (k-means) dos clientes por recencia, frequencia e valor.

Entrada (gerada por exportar_base.sql, a partir do DW):
  base_cestas.csv   -> id_venda, item
  base_clientes.csv -> id_cliente, recencia_dias, frequencia, valor, compras_veiculo

Como executar (na pasta mineracao/):
  pip install pandas scikit-learn matplotlib
  python mineracao.py

Saidas (na mesma pasta):
  resultado_mineracao.txt   -> relatorio completo dos dois metodos
  regras_associacao.csv     -> todas as regras encontradas
  clientes_grupos.csv       -> cada cliente com o grupo do k-means
  grafico_cotovelo.png      -> metodo do cotovelo e silhueta (escolha do k)
  grafico_grupos.png        -> clientes por frequencia x valor, coloridos por grupo

O Apriori foi implementado aqui (poucas linhas, com pandas) para deixar claro
cada passo do algoritmo; o resultado e o mesmo da funcao apriori do mlxtend.
"""

from itertools import combinations
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from sklearn.cluster import KMeans
from sklearn.metrics import silhouette_score
from sklearn.preprocessing import StandardScaler

PASTA = Path(__file__).resolve().parent

# Parametros
SUPORTE_MIN = 0.02     # o conjunto aparece em pelo menos 2% das vendas
CONFIANCA_MIN = 0.40   # quando X aparece, Y aparece em pelo menos 40% das vezes
TAMANHO_MAX = 3        # conjuntos de ate 3 itens
K_TESTADOS = range(2, 9)
SEMENTE = 42           # deixa o k-means reproduzivel

saida = []


def escrever(texto=""):
    print(texto)
    saida.append(texto)


# =============================================================================
# 1) REGRAS DE ASSOCIACAO (APRIORI)
# =============================================================================
cestas = pd.read_csv(PASTA / "base_cestas.csv")

# Preparacao: matriz venda x item (True quando o item esta na venda)
matriz = pd.crosstab(cestas["id_venda"], cestas["item"]).astype(bool)
n_vendas = len(matriz)

escrever("=" * 78)
escrever("1) REGRAS DE ASSOCIACAO (APRIORI)")
escrever("=" * 78)
escrever(f"Base: {n_vendas} vendas (cestas), {matriz.shape[1]} itens distintos, "
         f"{len(cestas)} linhas venda-item")
escrever(f"Parametros: suporte minimo = {SUPORTE_MIN:.0%}, "
         f"confianca minima = {CONFIANCA_MIN:.0%}, conjuntos de ate {TAMANHO_MAX} itens")
escrever()


def suporte(itens):
    """Fracao das vendas que contem todos os itens do conjunto."""
    return matriz[list(itens)].all(axis=1).mean()


# Apriori: so combina conjuntos que ja sao frequentes (propriedade de anti-monotonia:
# se um conjunto nao e frequente, nenhum conjunto maior que o contenha sera).
frequentes = {}
nivel = [frozenset([i]) for i in matriz.columns if matriz[i].mean() >= SUPORTE_MIN]
for c in nivel:
    frequentes[c] = suporte(c)
tamanho = 1
while nivel and tamanho < TAMANHO_MAX:
    tamanho += 1
    candidatos = {a | b for a in nivel for b in nivel if len(a | b) == tamanho}
    candidatos = [c for c in candidatos
                  if all(frozenset(s) in frequentes for s in combinations(c, tamanho - 1))]
    nivel = []
    for c in candidatos:
        s = suporte(c)
        if s >= SUPORTE_MIN:
            frequentes[c] = s
            nivel.append(c)

por_tamanho = pd.Series([len(c) for c in frequentes]).value_counts().sort_index()
escrever("Conjuntos frequentes por tamanho: "
         + ", ".join(f"{k} item(ns): {v}" for k, v in por_tamanho.items()))

# Geracao das regras X -> Y a partir de cada conjunto frequente
regras = []
for conjunto, sup_xy in frequentes.items():
    if len(conjunto) < 2:
        continue
    for r in range(1, len(conjunto)):
        for x in combinations(sorted(conjunto), r):
            x = frozenset(x)
            y = conjunto - x
            confianca = sup_xy / frequentes[x]
            if confianca >= CONFIANCA_MIN:
                regras.append({
                    "se_comprou": " + ".join(sorted(x)),
                    "entao_compra": " + ".join(sorted(y)),
                    "vendas": round(sup_xy * n_vendas),
                    "suporte": sup_xy,
                    "confianca": confianca,
                    "lift": confianca / frequentes[y],
                })

regras = pd.DataFrame(regras).sort_values(
    ["lift", "confianca", "se_comprou", "entao_compra"],
    ascending=[False, False, True, True], kind="mergesort")
# Regra trivial: "Emplacamento" so existe em venda de veiculo, entao qualquer regra
# que ligue veiculo e emplacamento e obvia (e uma regra do negocio, nao um padrao).
regras["trivial"] = (regras["se_comprou"] + regras["entao_compra"]).str.contains("Emplacamento") \
    & (regras["se_comprou"] + regras["entao_compra"]).str.contains("Veiculo")
regras.round(4).to_csv(PASTA / "regras_associacao.csv", index=False)

uteis = regras[~regras["trivial"]]
escrever(f"Regras encontradas: {len(regras)} ({len(regras) - len(uteis)} triviais com "
         f"Emplacamento + Veiculo, {len(uteis)} uteis)")
escrever()
escrever("15 regras uteis com maior lift:")
with pd.option_context("display.width", 200, "display.max_colwidth", 60):
    tabela = uteis.head(15)[["se_comprou", "entao_compra", "vendas", "suporte", "confianca", "lift"]]
    escrever(tabela.to_string(index=False, formatters={
        "suporte": "{:.2%}".format, "confianca": "{:.1%}".format, "lift": "{:.2f}".format}))
escrever()


# =============================================================================
# 2) AGRUPAMENTO DE CLIENTES (K-MEANS) POR RECENCIA, FREQUENCIA E VALOR
# =============================================================================
clientes = pd.read_csv(PASTA / "base_clientes.csv")

escrever("=" * 78)
escrever("2) AGRUPAMENTO DE CLIENTES (K-MEANS)")
escrever("=" * 78)
escrever(f"Base: {len(clientes)} clientes com pelo menos uma compra")
escrever("Variaveis: recencia (dias), frequencia (vendas) e valor (R$ liquido)")

# Preparacao: frequencia e valor sao muito assimetricos (poucos clientes compram
# muito), entao usamos log(1 + x) antes de padronizar; sem isso, o valor em reais
# (na casa dos milhoes) dominaria a distancia do k-means.
X = pd.DataFrame({
    "recencia": clientes["recencia_dias"],
    "log_frequencia": np.log1p(clientes["frequencia"]),
    "log_valor": np.log1p(clientes["valor"]),
})
X_pad = StandardScaler().fit_transform(X)
escrever("Preparacao: log(1+x) em frequencia e valor, depois padronizacao (media 0, desvio 1)")
escrever()

# Escolha do k: metodo do cotovelo (inercia) e coeficiente de silhueta
inercias, silhuetas = [], []
for k in K_TESTADOS:
    km = KMeans(n_clusters=k, n_init=10, random_state=SEMENTE).fit(X_pad)
    inercias.append(km.inertia_)
    silhuetas.append(silhouette_score(X_pad, km.labels_))
escrever("k | inercia | silhueta")
for k, i, s in zip(K_TESTADOS, inercias, silhuetas):
    escrever(f"{k} | {i:7.1f} | {s:.3f}")

K = list(K_TESTADOS)[int(np.argmax(silhuetas))]
escrever(f"k escolhido: {K} (maior silhueta)")
escrever()

fig, ax1 = plt.subplots(figsize=(7, 4))
ax1.plot(list(K_TESTADOS), inercias, "o-", color="#3b6ea5")
ax1.set_xlabel("numero de grupos (k)")
ax1.set_ylabel("inercia (cotovelo)", color="#3b6ea5")
ax2 = ax1.twinx()
ax2.plot(list(K_TESTADOS), silhuetas, "s--", color="#c0504d")
ax2.set_ylabel("silhueta", color="#c0504d")
ax1.axvline(K, color="gray", linestyle=":")
ax1.set_title(f"Escolha do k: cotovelo e silhueta (k = {K})")
fig.tight_layout()
fig.savefig(PASTA / "grafico_cotovelo.png", dpi=120)

modelo = KMeans(n_clusters=K, n_init=10, random_state=SEMENTE).fit(X_pad)
clientes["grupo"] = modelo.labels_

# Nomeia os grupos pelo valor medio (do maior para o menor)
perfil = clientes.groupby("grupo").agg(
    clientes=("id_cliente", "count"),
    recencia_media_dias=("recencia_dias", "mean"),
    frequencia_media=("frequencia", "mean"),
    valor_medio=("valor", "mean"),
    valor_total=("valor", "sum"),
    compram_veiculo=("compras_veiculo", lambda s: (s > 0).mean()),
)
ordem = perfil["valor_medio"].sort_values(ascending=False).index
renomear = {g: f"G{i + 1}" for i, g in enumerate(ordem)}
clientes["grupo"] = clientes["grupo"].map(renomear)
perfil = perfil.rename(index=renomear).sort_index()
perfil["pct_clientes"] = perfil["clientes"] / perfil["clientes"].sum()
perfil["pct_faturamento"] = perfil["valor_total"] / perfil["valor_total"].sum()

escrever("Perfil de cada grupo (G1 = maior valor medio):")
with pd.option_context("display.width", 200):
    escrever(perfil[["clientes", "pct_clientes", "recencia_media_dias", "frequencia_media",
                     "valor_medio", "pct_faturamento", "compram_veiculo"]].to_string(formatters={
        "pct_clientes": "{:.1%}".format, "recencia_media_dias": "{:.0f}".format,
        "frequencia_media": "{:.1f}".format, "valor_medio": lambda v: "R$ " + f"{v:,.0f}".replace(",", "."),
        "pct_faturamento": "{:.2%}".format, "compram_veiculo": "{:.0%}".format}))
escrever()

clientes.sort_values(["grupo", "valor"], ascending=[True, False]).to_csv(
    PASTA / "clientes_grupos.csv", index=False)

fig, (ax, bx) = plt.subplots(1, 2, figsize=(12, 5), sharey=True)
cores = ["#3b6ea5", "#c0504d", "#9bbb59", "#8064a2", "#f79646", "#4bacc6", "#7f7f7f", "#000000"]
for i, (g, d) in enumerate(clientes.groupby("grupo")):
    ax.scatter(d["frequencia"], d["valor"], s=25, alpha=0.75, color=cores[i], label=g)
    bx.scatter(d["recencia_dias"], d["valor"], s=25, alpha=0.75, color=cores[i], label=g)
ax.set_xscale("log")
ax.set_yscale("log")
ax.set_xlabel("frequencia (numero de vendas, escala log)")
ax.set_ylabel("valor liquido em R$ (escala log)")
ax.set_title("Frequencia x valor")
bx.set_xlabel("recencia (dias desde a ultima compra)")
bx.set_title("Recencia x valor")
bx.legend(title="grupo")
fig.suptitle("Clientes coloridos pelo grupo do k-means")
fig.tight_layout()
fig.savefig(PASTA / "grafico_grupos.png", dpi=120)

(PASTA / "resultado_mineracao.txt").write_text("\n".join(saida) + "\n", encoding="utf-8")
