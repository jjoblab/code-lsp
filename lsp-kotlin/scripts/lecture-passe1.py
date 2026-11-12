#!/usr/bin/env python3
# Lectures chiffrées de la passe appareil 1 (moto g06, 12/11/2026) —
# calculs par code (jamais de tête), miroir des PV de la mission Java.
h1, h2 = 27163, 27129
s1, s2 = 212, 4891
f = {  # p50 / p95 (ms)
    "F1 membres": (3, 9),
    "F2 noms": (4, 13),
    "F3 signature": (1, 4),
    "F4 hover": (0, 2),
}
f5, f6 = (1346, 1531), (22, 32, 34)
c1_p95, c1_facteur = 6, 1.00
q1 = 97
# Les repères bac à sable (passe témoin, même kit, JDK 21 bureau) :
bac = {"S1": 127, "S2": 825, "H1": 2197, "H2": 2297,
       "F5": (132, 253), "F6": (1, 3, 26)}

print("== Marges vs les seuils du gabarit 07 / ADR 0124 ==")
print(f"S1  chaud   {s1:>6} ms  seuil < 3000   -> {'TENU' if s1 < 3000 else 'MANQUÉ'}  (marge ×{3000/max(s1,1):.1f})")
print(f"F1  membres {f['F1 membres'][1]:>6} ms  seuil < 100    -> {'TENU' if f['F1 membres'][1] < 100 else 'MANQUÉ'}")
print(f"F2  noms    {f['F2 noms'][1]:>6} ms  seuil < 100    -> {'TENU' if f['F2 noms'][1] < 100 else 'MANQUÉ'}")
print(f"F3  sign.   {f['F3 signature'][1]:>6} ms  seuil < 100    -> {'TENU' if f['F3 signature'][1] < 100 else 'MANQUÉ'}")
print(f"F4  hover   {f['F4 hover'][1]:>6} ms  seuil < 100    -> {'TENU' if f['F4 hover'][1] < 100 else 'MANQUÉ'}")
print(f"F5  géant   {f5[1]:>6} ms  seuil < 300    -> {'TENU' if f5[1] < 300 else 'MANQUÉ'}  (dépassement ×{f5[1]/300:.1f})")
print(f"F6  p99     {f6[2]:>6} ms  (régularité ; latence max 34 ms — aucune fige)")
print(f"C1  p95     {c1_p95:>6} ms  facteur ×{c1_facteur:.2f} (nominal {3} ms)")
print(f"Q1  A->B    {q1:>6} ms  seuil < 300    -> {'TENU' if q1 < 300 else 'MANQUÉ'}")
print(f"H1  profond {h1:>6} ms  seuil < 10000  -> {'TENU' if h1 < 10000 else 'MANQUÉ'}  (dépassement ×{h1/10000:.2f})")
print(f"H2  chaud   {h2:>6} ms  seuil < 2000   -> {'TENU' if h2 < 2000 else 'MANQUÉ'}  (dépassement ×{h2/2000:.2f})")
print()
print("== Facteurs bac à sable (JDK 21 bureau) -> appareil (moto g06) ==")
for nom, appareil, seuil in [("S1", s1, None), ("S2", s2, None), ("H1", h1, 10000), ("H2", h2, 2000)]:
    print(f"{nom}: {bac[nom]} ms -> {appareil} ms  (×{appareil/max(bac[nom],1):.2f})")
print(f"F5 p95: {bac['F5'][1]} ms -> {f5[1]} ms  (×{f5[1]/bac['F5'][1]:.2f})")
print(f"F6 p99: {bac['F6'][2]} ms -> {f6[2]} ms  (×{f6[2]/bac['F6'][2]:.2f})")
print()
print("== Lecture H (option 2 SANS démon) ==")
print(f"H2/H1 = {h2/max(h1,1):.3f} — le compilateur relance SON sous-processus")
print(f"à chaque appel : H2 == H1 (amorçage RE-payé à chaque requête).")
print(f"Delta H1-H2 = {h1-h2} ms — le potentiel mesuré du démon (option 3).")
