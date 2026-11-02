#!/usr/bin/env python3
# ============================================================================
# comparer-arborescence.py — COMPARATEUR D'ARBORESCENCE AVANT/APRÈS (Phase 2)
# Outillage de contrôle de la réorganisation (docs/REORGANISATION.md §6.1) —
# face à la photographie docs/REORGANISATION-AVANT.md.
#
# Compare <ref_avant> et HEAD via l'appariement de renoms de git :
#   - R100 : DÉPLACEMENT PUR (blob identique — git mv, similarité 100 %) ;
#   - R<100 : déplacement AVEC CORRECTIFS de références (attendu en Phase 2 :
#     les mêmes commits qui déplacent corrigent les chemins fonctionnels —
#     plan §1 : « un type de déplacement par commit, corrections dans le
#     même commit ou commits séparés ») ;
#   - A : AJOUT (contenu neuf) — doit figurer dans la liste des ajouts
#     attendus (outillage, squelette lsp-xml, bilan) ;
#   - D : PERTE (aucun successeur) — ÉCHEC.
#
# L'intégrité de CONTENU des fichiers sensibles n'est PAS perdue pour autant :
# elle est prouvée séparément — baselines SHA-256 (§4 AVANT), vendoring
# octet pour octet (28/28 R100), digeste structurel (P-1,
# verifier-equivalence-digeste.sh). Ce comparateur garantit l'INVENTAIRE :
# rien ne disparaît sans successeur, rien n'apparaît sans être attendu.
#
# Usage (depuis la racine, arbre propre) :
#   python3 scripts/comparer-arborescence.py <ref_avant> [ajout_attendu …]
# ============================================================================
import subprocess
import sys


def principal():
    if len(sys.argv) < 2:
        print('Usage : comparer-arborescence.py <ref_avant> [ajout_attendu …]',
              file=sys.stderr)
        return 2
    ref_avant = sys.argv[1]
    # Arguments simples = ajouts attendus ; « ancien=>nouveau » = paire
    # déplacement-avec-réécriture attendue (D+A sous le seuil de similarité :
    # re-signature du digeste, réécriture dense de chemins d'un registre).
    ajouts_attendus = set()
    paires_attendues = {}
    for argument in sys.argv[2:]:
        if '=>' in argument:
            ancien, nouveau = argument.split('=>', 1)
            paires_attendues[ancien] = nouveau
        else:
            ajouts_attendus.add(argument)

    propre = subprocess.run(['git', 'diff', '--quiet', 'HEAD']).returncode == 0
    if not propre:
        print('ERREUR : arbre de travail non propre — commiter avant la '
              'comparaison (la mesure se fait sur HEAD).', file=sys.stderr)
        return 2

    statut = subprocess.run(
        ['git', 'diff', '--name-status', '-M50%', ref_avant, 'HEAD'],
        capture_output=True, text=True, check=True).stdout

    purs, avec_correctifs, ajouts, pertes = [], [], [], []
    for ligne in statut.splitlines():
        champs = ligne.split('\t')
        code = champs[0]
        if code.startswith('R'):
            cible = (int(code[1:]) if code[1:].isdigit() else 100)
            (purs if cible == 100 else avec_correctifs).append(
                (champs[1], champs[2]))
        elif code == 'A':
            ajouts.append(champs[1])
        elif code == 'D':
            pertes.append(champs[1])
        elif code.startswith('C'):
            ajouts.append(champs[2])   # copie = contenu dupliqué, à justifier
        else:  # M : modifié à l'identique de chemin (attendu : fichiers racine)
            pass

    # Paires attendues : le D et le A s'annulent mutuellement.
    pertes_reelles = [p for p in pertes if p not in paires_attendues]
    ajoutes_reels = [a for a in ajouts if a not in paires_attendues.values()]
    for ancien, nouveau in paires_attendues.items():
        if ancien in pertes and nouveau in ajouts:
            print(f'  ~ paire attendue (réécriture) : {ancien} => {nouveau}')
        elif ancien in pertes or nouveau in ajouts:
            print(f'  ! paire incomplète : {ancien} => {nouveau}')

    inattendus = sorted(a for a in ajoutes_reels if a not in ajouts_attendus)
    manquants = sorted(a for a in ajouts_attendus if a not in ajoutes_reels)

    print(f'Déplacements PURS (R100, blob identique)      : {len(purs)}')
    print(f'Déplacements AVEC correctifs (R50–99, refs)   : {len(avec_correctifs)}')
    print(f'Ajouts (contenu neuf)                         : {len(ajoutes_reels)}')
    for a in sorted(ajoutes_reels):
        marque = '' if a in ajouts_attendus else '  (INATTENDU)'
        print(f'  + {a}{marque}')
    print(f'PERTES (aucun successeur)                     : {len(pertes_reelles)}')
    for p in pertes_reelles[:20]:
        print(f'  - {p}')
    if len(pertes_reelles) > 20:
        print(f'  … et {len(pertes_reelles) - 20} autre(s)')
    for m in manquants:
        print(f'  ! attendu mais absent : {m}')

    echec = bool(pertes_reelles) or bool(inattendus) or bool(manquants)
    print('\nVERDICT : ' + ('ÉCHEC — perte ou ajout inattendu (cf. ci-dessus).'
                           if echec else
                           'OK — inventaire complet : aucun fichier perdu, '
                           'ajouts conformes à la liste attendue.'))
    return 1 if echec else 0


if __name__ == '__main__':
    sys.exit(principal())
