#!/usr/bin/env bash
#
# check_live_records.sh — integridade dos registros vivos da lane (docs/development):
#   A) contagem viva nos READMEs == classificador canonico (autoridade e o script,
#      nunca a prosa: numero cravado em dois lugares e promessa de drift);
#   B) paridade EN<->PT dos IDs de decisao em DECISIONS.md (o registro de governanca
#      nao pode ter secao so num idioma) e zero heading duplicado no mesmo arquivo.
#   C) numeracao de secoes (N.) em DECISIONS.md: mesmo conjunto de numeros E mesmo
#      nivel de heading nos dois idiomas (achou a secao 7 PT rebaixada a H2 vs H1 no EN).
#   D) a lista "Pending (condition 3)" do README sec.0 == o conjunto de loose docs que
#      o gate realmente marca (`ls docs/development/*.md` menos o ALLOWLIST do gate).
#      Fecha a divergencia silenciosa: o registro humano (README) nao pode discordar
#      da medicao (gate) — nem listar menos, nem listar a mais.
#   E) roadmap EG: mesmo conjunto de linhas EG-N E mesmo estado fechado/aberto EN<->PT,
#      pela MESMA regra do gate (linha contem DONE|FEITO). O gate de release so le o
#      roadmap EN: se o PT divergir, ninguem ve — e a condicao 6 do release depende disso.
#   F) numeracao/nivel de secoes (N.) em TODOS os pares de docs/development (nao so
#      DECISIONS): um doc EN com secao H1 e o PT com H2 e drift de leitura.
#
# A classe (A) ja driftou duas vezes em 21/09; a classe (B) apareceu quando a lane
# irma adicionou 3 decisoes so no EN e um merge deixou um heading orfao + duplicado
# no PT (achados 21/09 e corrigidos na mesma rodada).
#
# AUSENCIA e FALHA, nao passe livre: se a prosa/regex mudar de forma, este gate tem
# de ser atualizado JUNTO (regra anti-neutering da lane).
#
# Uso: scripts/check_live_records.sh            # rc!=0 em drift
#      scripts/check_live_records.sh --selftest # casos bons e ruins plantados
# Env (teste): LR_EN, LR_PT, LR_COUNT, DEC_EN, DEC_PT, LR_DOCDIR, LR_GATE
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

EN="${LR_EN:-docs/development/README.md}"
PT="${LR_PT:-docs/development/README.pt_BR.md}"
DEN="${DEC_EN:-docs/development/DECISIONS.md}"
DPT="${DEC_PT:-docs/development/DECISIONS.pt_BR.md}"
GATE="${LR_GATE:-scripts/check_release_050_gate.sh}"
# Parte D so roda na corrida real (ou quando o teste aponta LR_DOCDIR de proposito):
# fixtures antigas setam LR_EN e nao tem README/dir reais, entao DOCDIR fica vazio.
if [ -n "${LR_EN:-}" ]; then DOCDIR="${LR_DOCDIR-}"; else DOCDIR="${LR_DOCDIR:-docs/development}"; fi
RM_EN="${LR_RM_EN:-docs/development/roadmap.md}"
RM_PT="${LR_RM_PT:-docs/development/roadmap.pt_BR.md}"
if [ -n "${LR_EN:-}" ]; then RM_ON="${LR_RM_ON-}"; else RM_ON=1; fi
MDP="${LR_MDPAIRS-}"; [ -n "${LR_EN:-}" ] || MDP="${LR_MDPAIRS:-docs/development}"

if [ "${LR_COUNT:-}" != "" ]; then
    COUNT="$LR_COUNT"
else
    COUNT="$(bash scripts/check_known_bugs_status.sh \
        | sed -nE 's/^EN open[^(]*\(([0-9]+)\).*/\1/p' | head -1)"
fi

if [ "${1:-}" = "--selftest" ]; then
    T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
    mk() { printf '%s\n' "$2" > "$T/$1"; }
    # A) contagem: bom, errado, ausente
    mk r_en.md 'x **19 items in the open queue** y'; mk r_pt.md 'x **19 itens na fila aberta** y'
    mk d_en.md '## D-PROPERTY — x';               mk d_pt.md '## D-PROPERTY — x'
    if ! LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: caso bom devia passar"; exit 1; fi
    mk r_en.md 'x **18 items in the open queue** y'
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: contagem errada passou"; exit 1; fi
    mk r_en.md 'sem contagem aqui'
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: ausencia de contagem passou (neutering)"; exit 1; fi
    # B) paridade/id duplicado
    mk r_en.md 'x **19 items in the open queue** y'
    mk d_en.md '## D-ONLY-EN — x'; mk d_pt.md '## D-OUTRA — x'
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: decisao so no EN passou"; exit 1; fi
    mk d_en.md '## D-DUP — a
## D-DUP — a'; mk d_pt.md '## D-DUP — a'
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: heading duplicado passou"; exit 1; fi
    mk d_en.md '# 1. X'; mk d_pt.md '## 1. X'   # mesmo numero, nivel divergente
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: nivel de secao divergente passou"; exit 1; fi
    # D) README sec.0 pending <-> loose do gate (docdir/allowlist controlados)
    D="$T/dd"; mkdir -p "$D"
    printf 'ALLOWLIST="README.md README.pt_BR.md"\n' > "$T/gate.sh"
    : > "$D/work.md"
    cat > "$D/README.md" << 'EOF'
x **19 items in the open queue**
- **Pending (the release gate's condition 3):** `work.md`
- **Living records:** x
EOF
    cat > "$D/README.pt_BR.md" << 'EOF'
x **19 itens na fila aberta**
- **Pendentes (condição 3 do gate de release):** `work.md`
- **Registros vivos:** x
EOF
    mk d_en.md '# 1. X'; mk d_pt.md '# 1. X'
    if ! LR_EN="$D/README.md" LR_PT="$D/README.pt_BR.md" LR_DOCDIR="$D" LR_GATE="$T/gate.sh" \
         DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: README pending == loose do gate devia passar"; exit 1; fi
    cat > "$D/README.md" << 'EOF'
x **19 items in the open queue**
- **Pending (the release gate's condition 3):** `outro.md`
- **Living records:** x
EOF
    if LR_EN="$D/README.md" LR_PT="$D/README.pt_BR.md" LR_DOCDIR="$D" LR_GATE="$T/gate.sh" \
         DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: README pending divergente do gate passou"; exit 1; fi
    # E) roadmap EG: estado fechado/aberto EN<->PT (regra DONE|FEITO do gate)
    cat > "$T/rm_en.md" << 'EOF'
| EG-1 | x | DONE |
| EG-2 | y | OPEN |
EOF
    cat > "$T/rm_pt.md" << 'EOF'
| EG-1 | x | FEITO |
| EG-2 | y | ABERTO |
EOF
    if ! LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" \
         LR_COUNT=19 LR_RM_EN="$T/rm_en.md" LR_RM_PT="$T/rm_pt.md" LR_RM_ON=1 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: EG EN<->PT em paridade devia passar"; exit 1; fi
    cat > "$T/rm_pt.md" << 'EOF'
| EG-1 | x | ABERTO |
| EG-2 | y | ABERTO |
EOF
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" \
         LR_COUNT=19 LR_RM_EN="$T/rm_en.md" LR_RM_PT="$T/rm_pt.md" LR_RM_ON=1 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: EG divergente EN<->PT passou"; exit 1; fi
    # F) numeracao/nivel em todos os pares EN<->PT do dir
    D2="$T/dp"; mkdir -p "$D2"
    printf '## 1. X\n' > "$D2/g.md"; printf '## 1. X\n' > "$D2/g.pt_BR.md"; printf '## 2. Y\n' > "$D2/h.md"
    if ! LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" \
         LR_COUNT=19 LR_MDPAIRS="$D2" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: pares EN<->PT em paridade deviam passar"; exit 1; fi
    printf '# 1. X\n' > "$D2/g.pt_BR.md"   # nivel divergente (H1 vs H2)
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" \
         LR_COUNT=19 LR_MDPAIRS="$D2" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: nivel de secao divergente entre pares passou"; exit 1; fi
    echo "SELFTEST OK: contagem + paridade + duplicata + numeracao + pending<->gate + EG + pares"
    exit 0
fi

[ -n "${COUNT:-}" ] || { echo "FALHA: nao extrai a contagem da autoridade (formato mudou?)"; exit 1; }

python3 - "$EN" "$PT" "$COUNT" "$DEN" "$DPT" "$GATE" "$DOCDIR" "$RM_EN" "$RM_PT" "$RM_ON" "$MDP" << 'PYEOF'
import re, sys, os, glob
en, pt, count, den, dpt, gate, docdir = sys.argv[1:8]
rme, rmpt, rmon = sys.argv[8:11]
mdp = sys.argv[11] if len(sys.argv) > 11 else ""
bad = 0

# ---- A) contagem viva x autoridade -----------------------------------------
pat = re.compile(r"\*\*([0-9]+) (?:items?|live|itens?|vivos?)\b[^*]*\*\*")
seen = 0
for path in (en, pt):
    try:
        text = open(path, encoding="utf-8").read()
    except OSError:
        print(f"FALHA: nao consigo ler {path}"); bad = 1; continue
    hits = pat.findall(text)
    if not hits:
        print(f"FALHA: nenhuma declaracao de contagem viva em {path} "
              "(regex nao casa mais — atualize o gate junto com a prosa)")
        bad = 1; continue
    seen += len(hits)
    for n in hits:
        if n != count:
            print(f"DRIFT: {path} declara '{n}' mas a autoridade conta '{count}'"); bad = 1

# ---- B) DECISIONS: paridade EN<->PT + heading duplicado --------------------
IDPAT = re.compile(r"(?m)^#{2,3} (D-[A-Z0-9][A-Z0-9.-]*|R[0-9][A-Z0-9-]*)")
TEMPLATE = {"D-XXXX"}
sets = {}
for lang, path in (("EN", den), ("PT", dpt)):
    try:
        text = open(path, encoding="utf-8").read()
    except OSError:
        print(f"FALHA: nao consigo ler {path}"); bad = 1; continue
    ids = {m for m in IDPAT.findall(text) if m not in TEMPLATE}
    sets[lang] = ids
    heads = re.findall(r"(?m)^## .*$", text)  # so nivel 2: ### Context/Decision repetem de proposito
    dups = sorted({h for h in heads if heads.count(h) > 1})
    for h in dups:
        print(f"DUPLICATA: heading de secao repetido em {path}: {h[:70]}"); bad = 1
if sets.get("EN") is not None and sets.get("PT") is not None:
    only_en = sorted(sets["EN"] - sets["PT"])
    only_pt = sorted(sets["PT"] - sets["EN"])
    for d in only_en:
        print(f"PARIDADE: decisao so no EN (sem espelho PT): {d}"); bad = 1
    for d in only_pt:
        print(f"PARIDADE: decisao so no PT (sem espelho EN): {d}"); bad = 1

# ---- C) DECISIONS: numeracao de secoes (N.) — numero E nivel iguais EN<->PT
NUMPAT = re.compile(r"(?m)^(#{1,6}) ([0-9]+)\. ")
lvl = {}
for lang, path in (("EN", den), ("PT", dpt)):
    try:
        text = open(path, encoding="utf-8").read()
    except OSError:
        continue
    lvl[lang] = {n: len(h) for h, n in NUMPAT.findall(text)}
if "EN" in lvl and "PT" in lvl:
    for n in sorted(set(lvl["EN"]) | set(lvl["PT"]), key=int):
        a, b = lvl["EN"].get(n), lvl["PT"].get(n)
        if a != b:
            print(f"NUMERACAO: secao {n}. tem nivel EN={a} PT={b} (numeracao e nivel devem bater)")
            bad = 1

# ---- D) README sec.0 "Pending (cond. 3)" <-> loose set medido pelo gate ----
if docdir:
    try:
        gtext = open(gate, encoding="utf-8").read()
    except OSError:
        print(f"FALHA: nao consigo ler o gate {gate}"); bad = 1; gtext = ""
    m = re.search(r'ALLOWLIST="([^"]*)"', gtext)
    if not m:
        print(f"FALHA: ALLOWLIST nao encontrado em {gate} (formato mudou — atualize o gate)")
        bad = 1
    else:
        allow = set(m.group(1).split())
        try:
            on_disk = os.listdir(docdir)
        except OSError:
            print(f"FALHA: nao consigo listar {docdir}"); bad = 1; on_disk = []
        loose = sorted(f for f in on_disk
                       if f.endswith(".md") and not f.endswith(".pt_BR.md") and f not in allow)
        for lang, path, marker in (
                ("EN", en, "Pending (the release gate's condition 3)"),
                ("PT", pt, "Pendentes (condi\u00e7\u00e3o 3 do gate de release)")):
            try:
                lines = open(path, encoding="utf-8").read().splitlines()
            except OSError:
                print(f"FALHA: nao consigo ler {path}"); bad = 1; continue
            idx = next((i for i, l in enumerate(lines) if marker in l), None)
            if idx is None:
                print(f"FALHA: {path} sem a lista de pendentes da cond. 3 "
                      "(prosa mudou — atualize o gate junto com o README)"); bad = 1; continue
            bullet = []
            for l in lines[idx:]:
                if bullet and (not l.strip() or l.lstrip().startswith("- ")):
                    break
                bullet.append(l)
            named = sorted(set(re.findall(r"`([A-Za-z0-9._-]+\.md)`", "\n".join(bullet))))
            missing = [d for d in loose if d not in named]
            stale = [d for d in named if d not in loose]
            if missing:
                print(f"DRIFT ({lang}): loose marcado pelo gate mas ausente do README sec.0: {missing}")
                bad = 1
            if stale:
                print(f"DRIFT ({lang}): README sec.0 lista como pendente mas o gate nao marca: {stale}")
                bad = 1

# ---- E) roadmap EG: estado fechado/aberto EN<->PT (regra do gate) -----------
if rmon:
    def eg_closed(path):
        try:
            text = open(path, encoding="utf-8").read()
        except OSError:
            return None
        d = {}
        for line in text.splitlines():
            m = re.match(r"\|\s*(EG-[0-9]+)\s*\|", line)
            if m:
                d[m.group(1)] = bool(re.search(r"DONE|FEITO", line))
        return d
    key = lambda x: int(x.split("-")[1])
    a, b = eg_closed(rme), eg_closed(rmpt)
    if not a or not b:
        print("FALHA: tabela EG ilegivel/ausente no roadmap (EN ou PT) — prosa mudou?")
        bad = 1
    else:
        only_a = sorted(set(a) - set(b), key=key)
        only_b = sorted(set(b) - set(a), key=key)
        if only_a or only_b:
            print(f"PARIDADE (EG): linhas so no EN={only_a} so no PT={only_b}"); bad = 1
        for k in sorted(set(a) & set(b), key=key):
            if a[k] != b[k]:
                print(f"PARIDADE (EG): {k} fechado EN={a[k]} PT={b[k]} "
                      "(mesma regra DONE|FEITO do gate)"); bad = 1

# ---- F) TODOS os pares EN<->PT: numeracao/nivel de secoes (N.) -------------
if mdp:
    NP = re.compile(r"(?m)^(#{1,6}) ([0-9]+)[.)] ")
    def numbered(path):
        try:
            text = open(path, encoding="utf-8").read()
        except OSError:
            return None
        return {n: len(h) for h, n in NP.findall(text)}
    for enp in sorted(glob.glob(os.path.join(mdp, "*.md"))):
        if enp.endswith(".pt_BR.md"):
            continue
        ptp = enp[:-3] + ".pt_BR.md"
        if not os.path.exists(ptp):
            continue
        a, b = numbered(enp), numbered(ptp)
        if a is None or b is None:
            continue
        for n in sorted(set(a) | set(b), key=int):
            if a.get(n) != b.get(n):
                print(f"NUMERACAO: {os.path.basename(enp)} secao {n}. "
                      f"nivel EN={a.get(n)} PT={b.get(n)} (numeracao e nivel devem bater)")
                bad = 1

if not bad:
    print(f"OK: contagem viva {count} consistente ({seen} declaracoes); "
          f"DECISIONS EN<->PT com {len(sets.get('EN', ()))} IDs em paridade, 0 duplicatas, "
          "numeracao/nivel em paridade"
          + ("; pendentes sec.0 == loose do gate" if docdir else "")
          + ("; roadmap EG EN<->PT em paridade" if rmon else "")
          + ("; numeracao/nivel de todos os pares EN<->PT" if mdp else ""))
sys.exit(1 if bad else 0)
PYEOF
