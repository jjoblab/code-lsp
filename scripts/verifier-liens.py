#!/usr/bin/env python3
# ============================================================================
# verifier-liens.py — VÉRIFICATEUR DE LIENS ET DE RÉFÉRENCES DE CHEMINS (P-6)
# Outillage de contrôle de la Phase 2 de réorganisation (docs/REORGANISATION.md
# §6.1 — prompt de mission du 02/11/2026, exécution validée le 03/11/2026).
#
# Contrôle DEUX classes de références dans les fichiers Markdown suivis :
#
#   1. LIENS MARKDOWN (erreurs — « lien mort ») : [texte](cible) où la cible
#      est relative (ni http(s), ni mailto, ni ancre seule). La cible est
#      résolue par rapport au fichier, puis à la racine du dépôt (tentative
#      de repli — un lien qui résout par l'un des deux moyens n'est pas mort).
#
#   2. RÉFÉRENCES TEXTUELLES DE CHEMINS entre accents graves (`chemin/…`) :
#      tout jeton dont le PREMIER SEGMENT est une racine du dépôt (actuelle
#      ou antérieure à la réorganisation) est considéré comme un chemin de
#      dépôt et doit exister (fichier ou répertoire, système de fichiers).
#      Les faux positifs JVM/Kotlin-stdlib (java/lang/Object, kotlin/text/…)
#      sont filtrés : ce sont des noms de paquets, pas des chemins du dépôt.
#
# Les documents de PILOTAGE HISTORIQUES de la réorganisation sont EXCLUS :
# ils décrivent volontairement l'ancienne arborescence (tables de migration,
# photographie avant, plan) — leurs mentions d'anciens chemins sont exactes.
#
# Sortie : liste des liens morts (ERREUR) et des références à revoir
# (SUSPECT — chemin racine ancien introuvable, ou chemin ni suivi ni présent).
# Code de retour : 0 s'il n'y a AUCUN lien mort ; 1 sinon.
# Les SUSPECTS ne font pas échouer la vérification : ils sont listés pour
# revue (chaque cas est soit corrigé, soit justifié dans le bilan de Phase 2).
#
# Usage (depuis la racine du dépôt) :
#   python3 scripts/verifier-liens.py
# ============================================================================
import os
import re
import subprocess
import sys

# --- Racines ANCIENNES (avant réorganisation) : une référence pointant      ---
# --- dessus après les déplacements est une référence PÉRIMÉE à traiter.     ---
RACINES_ANCIENNES = {
    'java', 'kotlin', 'spike', 'spike-kotlin', 'index', 'model', 'testing',
    'ecj-art', 'bancs', 'docs', 'scripts', 'gradle', '.github', '.githooks',
}

# --- Paquets JVM / Kotlin stdlib : NOMBRES DE CLASSES, jamais des chemins    ---
# --- du dépôt (filtre anti-faux-positifs — liste standard des paquets).     ---
PAQUETS_JVM = (
    'applet awt beans io lang management math net nio rmi security sql '
    'text time util xml jdk javax invoke foreign module'.split()
)
PAQUETS_STDLIB = (
    'annotation annotations cmp collections comparisons concurrent contracts '
    'enums experimental io js jvm math native nodejs browser properties '
    'random ranges reflect sequences streams system text time time-instant '
    'uuid profun'.split()

)

# --- Documents de pilotage historiques : exclus (cf. en-tête).               ---
EXCLUS = {
    'docs/REORGANISATION.md',
    'docs/REORGANISATION-AVANT.md',
    'docs/MIGRATION-CHEMINS.md',
    'docs/REORGANISATION-BILAN-PHASE2.md',
}

LIEN_MD = re.compile(r'\[[^\]]*\]\(([^)\s]+)(?:\s+"[^"]*")?\)')
ACCENTS_GRAVES = re.compile(r'`([^`\n]+)`')
PONCTUATION_FINALE = '.,;:!?»)]}"\''
SEPARATEURS_ANCRE = '#?'


def racines_suivies(fichiers):
    """Ensemble des premiers segments des chemins suivis (racines du dépôt)."""
    return {f.split('/', 1)[0] for f in fichiers}


def est_paquet_jvm(jeton):
    """Vrai si le jeton ressemble à un nom de classe JVM (java/lang/…)."""
    parties = jeton.split('/')
    return (len(parties) >= 2 and parties[0] in ('java', 'javax', 'jdk')
            and parties[1] in PAQUETS_JVM)


def est_paquet_stdlib(jeton):
    """Vrai si le jeton ressemble à un paquet de la stdlib Kotlin."""
    parties = jeton.split('/')
    return (len(parties) >= 2 and parties[0] == 'kotlin'
            and parties[1] in PAQUETS_STDLIB)


def principal():
    ls = subprocess.run(
        ['git', '-c', 'core.quotepath=false', 'ls-files', '*.md', '*.MD'],
        capture_output=True, text=True, check=True,
    ).stdout.splitlines()
    fichiers = subprocess.run(
        ['git', '-c', 'core.quotepath=false', 'ls-files'],
        capture_output=True, text=True, check=True,
    ).stdout.splitlines()
    racines = racines_suivies(fichiers)

    liens_morts = []      # (fichier, ligne, cible) — ERREURS
    suspects = []         # (fichier, ligne, jeton) — À REVOIR
    nb_liens = nb_jetons = 0

    for md in ls:
        historique = md in EXCLUS
        try:
            with open(md, encoding='utf-8') as f:
                lignes = f.read().splitlines()
        except (OSError, UnicodeDecodeError):
            continue
        dossier = os.path.dirname(md)

        for numero, ligne in enumerate(lignes, 1):
            # --- 1. liens Markdown (erreurs) --------------------------------
            for cible in LIEN_MD.findall(ligne):
                if cible.startswith(('http://', 'https://', 'mailto:', '#')):
                    continue
                nb_liens += 1
                cible_nette = cible.split(SEPARATEUR_ANCRE)[0] if any(
                    s in cible for s in SEPARATEURS_ANCRE) else cible
                cible_nette = cible_nette.rstrip(PONCTUATION_FINALE)
                if not cible_nette:
                    continue
                candidats = ([os.path.normpath(os.path.join(dossier, cible_nette))]
                             if not cible_nette.startswith('/')
                             else []) + [cible_nette.lstrip('/')]
                if not any(os.path.exists(c) for c in candidats):
                    liens_morts.append((md, numero, cible))

            # --- 2. références de chemins entre accents graves ---------------
            if historique:
                continue  # tables et photographies : anciens chemins voulus
            for jeton in ACCENTS_GRAVES.findall(ligne):
                jeton = jeton.strip().rstrip(PONCTUATION_FINALE).strip()
                if '/' not in jeton or ' ' in jeton or not jeton:
                    continue
                tete = jeton.split('/', 1)[0]
                if tete not in racines and tete not in RACINES_ANCIENNES:
                    continue  # pas un chemin de dépôt (autre nature de jeton)
                if est_paquet_jvm(jeton) or est_paquet_stdlib(jeton):
                    continue  # nom de classe/paquet, pas un chemin du dépôt
                nb_jetons += 1
                cible = jeton.rstrip('/')
                # Glob tronqué (astérisque, points de suspension) : le préfixe
                # réel doit exister (ex. `lsp-java/docs/adr/0007-*.md`).
                prefixe = re.split(r'[*…]', cible)[0].rstrip('/-')
                if not (os.path.exists(jeton) or os.path.exists(cible)
                        or (prefixe and os.path.exists(prefixe))):
                    amateurs = ('ANCIENNE RACINE' if tete not in racines
                                else 'introuvable')
                    suspects.append((md, numero, jeton, amateurs))

    print(f'Fichiers .md suivis : {len(ls)} (exclus historiques : '
          f'{sum(1 for e in EXCLUS if e in ls)})')
    print(f'Liens Markdown contrôlés : {nb_liens} — MORTS : {len(liens_morts)}')
    for f, n, c in liens_morts:
        print(f'  ERREUR  {f}:{n}  lien mort → {c}')
    print(f'Références de chemins contrôlées : {nb_jetons} — À REVOIR : '
          f'{len(suspects)}')
    for f, n, j, nature in suspects:
        print(f'  SUSPECT {f}:{n}  {j}  ({nature})')

    if liens_morts:
        print('\nVERDICT : ÉCHEC — des liens morts doivent être corrigés (P-6).')
        return 1
    print('\nVERDICT : OK — aucun lien mort ; '
          + ('aucune référence à revoir.' if not suspects
             else f'{len(suspects)} référence(s) à revoir ci-dessus.'))
    return 0


if __name__ == '__main__':
    sys.exit(principal())
