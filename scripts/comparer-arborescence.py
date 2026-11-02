#!/usr/bin/env python3
# ============================================================================
# comparer-arborescence.py — COMPARATEUR D'ARBORESCENCE AVANT/APRÈS (Phase 2)
# Outillage de contrôle de la réorganisation (docs/REORGANISATION.md §6.1 —
# l'arborescence « avant » de référence est la photographie
# docs/REORGANISATION-AVANT.md, branche integration/fusion).
#
# Prouve que la réorganisation n'a NI PERDU NI MODIFIÉ un seul fichier :
#   - le MULTI-ENSEMBLE des empreintes de blobs (SHA-1 de contenu git) est
#     IDENTIQUE entre <ref_avant> et HEAD : tout blob présent avant existe
#     encore après (les déplacements purs git mv conservent le blob) ;
#   - les fichiers AJOUTÉS en Phase 2 (outillage, squelette lsp-xml, bilan)
#     sont listés et comparés à une liste d'ajouts attendus ;
#   - les disparitions de blobs (contenu perdu) sont une ÉCHEC.
#
# L'appariement par empreinte identifie chaque déplacement : le même blob
# sous un nouveau chemin est un DÉPLACEMENT PUR, pas une perte.
#
# Usage (depuis la racine du dépôt, arbre de travail propre) :
#   python3 scripts/comparer-arborescence.py <ref_avant> [ajout_attendu …]
# Exemple :
#   python3 scripts/comparer-arborescence.py integration/fusion \
#       scripts/verifier-liens.py lsp-xml/README.md
# ============================================================================
import subprocess
import sys
from collections import Counter


def empreintes(ref):
    """{chemin: empreinte_blob} pour tous les fichiers suivis à <ref>."""
    sortie = subprocess.run(
        ['git', 'ls-tree', '-r', ref],
        capture_output=True, text=True, check=True,
    ).stdout
    resultat = {}
    for ligne in sortie.splitlines():
        meta, chemin = ligne.split('\t', 1)
        empreinte = meta.split(' ')[2]
        resultat[chemin] = empreinte
    return resultat


def principal():
    if len(sys.argv) < 2:
        print('Usage : comparer-arborescence.py <ref_avant> [ajout_attendu …]',
              file=sys.stderr)
        return 2
    ref_avant = sys.argv[1]
    ajouts_attendus = set(sys.argv[2:])

    propre = subprocess.run(['git', 'diff', '--quiet', 'HEAD']).returncode == 0
    if not propre:
        print('ERREUR : arbre de travail non propre — commiter avant la '
              'comparaison (la mesure se fait sur HEAD).', file=sys.stderr)
        return 2

    avant = empreintes(ref_avant)
    apres = empreintes('HEAD')

    multiset_avant = Counter(avant.values())
    multiset_apres = Counter(apres.values())
    perdus = multiset_avant - multiset_apres   # blobs existant avant, plus là
    gagnes = multiset_apres - multiset_avant   # blobs nouveaux (ajouts de contenu)

    # Ajouts de FICHIERS (chemins présents après, pas avant).
    chemins_ajoutes = sorted(set(apres) - set(avant))
    # Déplacements purs : chemin disparu dont le blob existe encore ailleurs.
    deplacements = sorted(
        (a, b) for a in avant if a not in apres
        for b in apres if apres[b] == avant[a] and b not in avant
    )

    inattendus = sorted(c for c in chemins_ajoutes if c not in ajouts_attendus)
    manquants = sorted(a for a in ajouts_attendus if a not in chemins_ajoutes)

    print(f'Fichiers suivis : {len(avant)} avant → {len(apres)} après '
          f'({len(chemins_ajoutes)} ajout(s), '
          f'{len(set(avant) - set(apres))} déplacé(s)/retiré(s)).')
    print(f'Déplacements purs identifiés (blob conservé) : {len(deplacements)}')
    print(f'Blobs de contenu PERDUS : {sum(perdus.values())}')
    for empreinte, n in perdus.items():
        chemins = [c for c, e in avant.items() if e == empreinte]
        print(f'  PERDU ×{n} {empreinte[:12]} ← {", ".join(chemins[:3])}')
    print(f'Ajouts : {len(chemins_ajoutes)}')
    for c in chemins_ajoutes:
        marque = '' if c in ajouts_attendus else '  (INATTENDU)'
        print(f'  + {c}{marque}')
    for c in manquants:
        print(f'  ! attendu mais absent : {c}')

    echec = bool(perdus) or bool(inattendus) or bool(manquants)
    print('\nVERDICT : ' + ('ÉCHEC — perte ou écart d’ajouts (cf. ci-dessus).'
                           if echec else
                           'OK — aucun contenu perdu ; ajouts conformes.'))
    return 1 if echec else 0


if __name__ == '__main__':
    sys.exit(principal())
