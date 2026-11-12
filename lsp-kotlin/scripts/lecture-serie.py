#!/usr/bin/env python3
# Série protocolaire 3 passes (moto g06, 12/11/2026 17:07-17:25) :
# médianes + facteurs vs seuils — calculs par code, jamais de tête.
# La passe 16:59 (déjà consignée) devient la PASSE TÉMOIN, hors médiane.
from statistics import median

# (passe, heure, S1, S2, F5p50, F5p95, Q1, H1, H2, C1_p95, C1_facteur,
#  F1p95, F2p95, F3p95, F4p95, F6p99, F1p50, F2p50, F6p50, F6p95)
P = [
    ("P1 17:07", "17:07:37", 379, 4567, 1383, 1669, 108, 27308, 24809, 11, 2.00, 10, 13, 2, 2, 40, 4, 7, 22, 29),
    ("P2 17:11", "17:11:14", 249, 3373, 1220, 1411,  72, 18985, 18956,  5, 0.67,  6,  9, 2, 1, 28, 2, 4, 19, 24),
    ("P3 17:25", "17:25:17", 197, 2493, 1151, 1242,  40, 18647, 18736,  7, 1.00,  5,  7, 2, 2, 25, 2, 4, 19, 23),
]
temoin = ("T 16:59", "16:59:24", 212, 4891, 1346, 1531, 97, 27163, 27129, 6, 1.00, 9, 13, 4, 2, 34, 3, 4, 22, 32)

def serie(i):  # colonne i des 3 passes
    return [p[i] for p in P]

print("== Espacements (heure appareil, en-tête du script) ==")
print("P1->P2 : 3 min 37 s ; P2->P3 : 14 min 03 s — tous >= 30 s (protocole tenu)")
print()
print("== Médianes des 3 passes (la série officielle) vs seuils ==")
lignes = [
    ("S1 chaud (ms)", 2, 3000, "<"), ("S2 froid (ms)", 3, None, ""),
    ("F1 p95 (ms)", 11, 100, "<"), ("F2 p95 (ms)", 12, 100, "<"),
    ("F3 p95 (ms)", 13, 100, "<"), ("F4 p95 (ms)", 14, 100, "<"),
    ("F5 p50 (ms)", 4, None, ""), ("F5 p95 (ms)", 5, 300, "<"),
    ("F6 p99 (ms)", 15, None, ""), ("C1 p95 (ms)", 9, None, ""),
    ("Q1 (ms)", 6, 300, "<"), ("H1 (ms)", 7, 10000, "<"), ("H2 (ms)", 8, 2000, "<"),
]
for nom, i, seuil, sens in lignes:
    vals = serie(i)
    med = median(vals)
    m = f"  seuil {sens} {seuil}  -> {'TENU' if med < seuil else 'MANQUÉ'}" if seuil else ""
    print(f"{nom:16} {vals}  médiane {med:.0f}{m}")
print(f"C1 facteur       {[p[10] for p in P]}  médiane {median(serie(10)):.2f}")
print(f"F6 p50/p95       médiane {median(serie(17)):.0f} / {median(serie(19)):.0f}")
print(f"F1 p50           médiane {median(serie(16)):.0f}")
print()
print("== Facteurs de dépassement/tenue (pire passe de la série) ==")
worst = {
    "F5 p95": max(serie(5)) / 300, "H1": max(serie(7)) / 10000, "H2": max(serie(8)) / 2000,
    "S1": 3000 / max(serie(2)), "Q1": 300 / max(serie(6)),
    "F1 p95": 100 / max(serie(11)), "F4 p95": 100 / max(serie(14)),
}
for k, v in worst.items():
    print(f"{k:8} pire passe : facteur ×{v:.1f}")
print()
print("== H2 vs H1 (la cause structurelle, passe par passe) ==")
for p in P:
    print(f"{p[0]} : H1 {p[7]} / H2 {p[8]}  -> H2/H1 = {p[8]/p[7]:.3f}  (delta {p[8]-p[7]:+d} ms)")
print(f"Témoin  : H1 {temoin[7]} / H2 {temoin[8]}  -> H2/H1 = {temoin[8]/temoin[7]:.3f}")
print()
print("== Tendance thermique H (P1->P3) et stabilité des mesures STABLES ==")
print(f"H1 : {P[0][7]} -> {P[2][7]} ms ({(P[0][7]-P[2][7])/1000:.1f} s plus rapide en P3, "
      f"-{(P[0][7]-P[2][7])/P[0][7]*100:.0f}%)")
print(f"S2 : {P[0][3]} -> {P[2][3]} ms (même tendance, -{(P[0][3]-P[2][3])/P[0][3]*100:.0f}%)")
print(f"F1 p95 : {P[0][11]} -> {P[2][11]} ms ; F5 p95 : {P[0][5]} -> {P[2][5]} ms")
print(f"Dispersion F5 p95 : min {min(serie(5))} / max {max(serie(5))} = ×{max(serie(5))/min(serie(5)):.2f}")
print(f"Dispersion H1     : min {min(serie(7))} / max {max(serie(7))} = ×{max(serie(7))/min(serie(7)):.2f}")
print()
print("== Meilleure passe (plafond de ce que l'appareil sait faire à froid) ==")
print(f"H1 min {min(serie(7))} ms (P3) : encore ×{min(serie(7))/10000:.2f} la cible de 10 s")
print(f"H2 min {min(serie(8))} ms (P2) : encore ×{min(serie(8))/2000:.2f} la cible de 2 s")
print()
print("== Médiane vs passe témoin 16:59 (déjà consignée au dépôt) ==")
for nom, i in [("S1", 2), ("F5 p95", 5), ("H1", 7), ("H2", 8)]:
    print(f"{nom:8} témoin {temoin[i]} / médiane série {median(serie(i)):.0f}  "
          f"({(median(serie(i))-temoin[i])/max(temoin[i],1)*100:+.0f}%)")
