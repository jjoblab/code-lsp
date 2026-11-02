#!/usr/bin/env python3
# ============================================================================
# generer-migration-chemins.py — génère docs/MIGRATION-CHEMINS.md
# (Phase 1 du plan de réorganisation — prompt de mission du 02/11/2026).
#
# Classification de CHAQUE fichier suivi (git ls-files) en une seule classe :
#   socle · java · kotlin · xml · transverse
# et production de la table ancien chemin → nouveau chemin.
#
# Les fichiers ambigus portent une décision D-x (cf. docs/REORGANISATION.md
# §Décisions) avec la RECOMMANDATION de l'agent — la destination n'est
# DÉFINITIVE qu'après validation du propriétaire.
#
# Usage (depuis la racine du dépôt) :
#   python3 scripts/generer-migration-chemins.py
# Écrit docs/MIGRATION-CHEMINS.md (écrase) et imprime le résumé.
# ============================================================================
import subprocess
import sys
from collections import Counter

# ---------------------------------------------------------------------------
# Décisions ouvertes (à valider par le propriétaire) — recommandation agent.
# ---------------------------------------------------------------------------
DECISIONS = {
    # D-1 : documents de pilotage du moteur Java → lsp-java/docs/
    'docs/INTEGRATION-CODEIDE.md': ('D-1', 'java'),
    'docs/BASCULE-CODEIDE.md': ('D-1', 'java'),
    'docs/PLAN-REECRITURE.md': ('D-1', 'java'),
    'docs/RECALAGE-1.md': ('D-1', 'java'),
    'docs/RECALAGE-2.md': ('D-1', 'java'),
    'docs/ECARTS-SERVEUR.md': ('D-1', 'java'),
    'docs/REGISTRE-CLIENT-K2.md': ('D-1', 'java'),
    # D-2 : gabarit commun de passe bascule appareil → docs/ (transverse)
    'bancs/GABARIT-BASCULE-APPAREIL.md': ('D-2', 'transverse'),
    # D-3 : résultats de la bascule §8.5 (moteur Java) → lsp-java/bancs/
    'bancs/ANALYSE-DELTAS-BASCULE.md': ('D-3', 'java'),
    'bancs/resultats-bascule.txt': ('D-3', 'java'),
    # D-4 : étude éditeur ↔ LSP (valable pour tous les LSP) → docs/ transverse
    'docs/etudes/editeur-lsp/01-responsabilites-editeur-lsp.md': ('D-4', 'transverse'),
    # D-5 : scripts de bancs / bascule du moteur Java → lsp-java/scripts/
    'scripts/kit-bascule.sh': ('D-5', 'java'),
    'scripts/kit-mesure.sh': ('D-5', 'java'),
    'scripts/preparer-oracle.sh': ('D-5', 'java'),
    'scripts/preparer-base-api.sh': ('D-5', 'java'),
    'scripts/preparer-manifeste-ask.sh': ('D-5', 'java'),
    'scripts/preparer-manifestes-corpus.sh': ('D-5', 'java'),
    'scripts/telecharger-corpus-android.sh': ('D-5', 'java'),
    'scripts/preparer-projet-android.sh': ('D-5', 'java'),
    'scripts/ask-export.init.gradle': ('D-5', 'java'),
    'scripts/manifeste-corpus.init.gradle': ('D-5', 'java'),
    # D-6 : USAGE.md racine documente le serveur Java → lsp-java/USAGE.md
    'USAGE.md': ('D-6', 'java'),
}

# ---------------------------------------------------------------------------
# Règles de classification (chemin → (classe, fonction nouveau_chemin)).
# L'ordre des règles est sans importance : chaque règle est exclusive.
# ---------------------------------------------------------------------------
def regle(ancien: str) -> tuple:
    """Retourne (classe, nouveau_chemin, decision_ou_None) pour un chemin suivi."""
    if ancien in DECISIONS:
        d, classe = DECISIONS[ancien]
        return classe, mapper_decision(ancien, classe), d

    # --- racine transverse --------------------------------------------------
    if ancien in ('README.md', 'AGENTS.md', 'NOTICE', 'LICENSE', '.gitignore',
                  'settings.gradle.kts', 'build.gradle.kts', 'gradle.properties',
                  'gradlew', 'gradlew.bat'):
        return 'transverse', ancien, None
    if ancien.startswith(('.github/', '.githooks/', 'gradle/')):
        return 'transverse', ancien, None

    # --- documents transverses ---------------------------------------------
    if ancien in ('docs/REORGANISATION-AVANT.md', 'docs/REORGANISATION.md',
                  'docs/MIGRATION-CHEMINS.md', 'docs/INDEX.md', 'docs/GOUVERNANCE.md'):
        return 'transverse', ancien, None

    # --- socle --------------------------------------------------------------
    for m in ('index', 'model', 'testing', 'ecj-art'):
        if ancien.startswith(m + '/'):
            return 'socle', 'socle/' + ancien, None

    # --- moteur Java --------------------------------------------------------
    if ancien.startswith('java/'):
        return 'java', 'lsp-java/moteur/' + ancien[len('java/'):], None
    if ancien.startswith('spike/'):
        return 'java', 'lsp-java/spike/' + ancien[len('spike/'):], None
    if ancien.startswith('docs/adr/00'):          # ADR 0001–0032
        return 'java', 'lsp-java/docs/' + ancien[len('docs/'):], None
    if ancien.startswith('docs/cahier-specifications/'):
        return 'java', 'lsp-java/docs/' + ancien[len('docs/'):], None
    if ancien.startswith(('docs/etudes/moteur-java-psi/',
                          'docs/etudes/niveau-android-studio/')):
        return 'java', 'lsp-java/docs/' + ancien[len('docs/'):], None
    if ancien == 'docs/PROVENANCE-REPRISE.md':
        return 'java', 'lsp-java/docs/PROVENANCE-REPRISE.md', None
    if ancien.startswith('bancs/baselines/completion.json'):
        return 'java', 'lsp-java/bancs/baselines/completion.json', None
    if ancien.startswith('bancs/baselines/parite-completion.json'):
        return 'java', 'lsp-java/bancs/baselines/parite-completion.json', None
    if ancien.startswith(('bancs/projet-android/', 'bancs/projet-android-genere/',
                          'bancs/corpus-android/')):
        return 'java', 'lsp-java/bancs/' + ancien[len('bancs/'):], None

    # --- moteur Kotlin ------------------------------------------------------
    if ancien.startswith('kotlin/'):
        return 'kotlin', 'lsp-kotlin/moteur/' + ancien[len('kotlin/'):], None
    if ancien.startswith('spike-kotlin/'):
        return 'kotlin', 'lsp-kotlin/spike/' + ancien[len('spike-kotlin/'):], None
    if ancien.startswith('docs/adr/01'):          # ADR 0100–0136
        return 'kotlin', 'lsp-kotlin/docs/' + ancien[len('docs/'):], None
    if ancien.startswith('docs/cahier-specifications-kotlin/'):
        return 'kotlin', 'lsp-kotlin/docs/' + ancien[len('docs/'):], None
    if ancien.startswith(('docs/etudes/moteur-kotlin/',
                          'docs/etudes/niveau-android-studio-kotlin/')):
        return 'kotlin', 'lsp-kotlin/docs/' + ancien[len('docs/'):], None
    if ancien == 'docs/PROVENANCE-REPRISE-KOTLIN.md':
        return 'kotlin', 'lsp-kotlin/docs/PROVENANCE-REPRISE-KOTLIN.md', None
    if ancien == 'bancs/baselines/completion-kotlin.json':
        return 'kotlin', 'lsp-kotlin/bancs/baselines/completion-kotlin.json', None
    if ancien.startswith('bancs/corpus-android-kotlin/'):
        return 'kotlin', 'lsp-kotlin/bancs/' + ancien[len('bancs/'):], None
    if ancien in ('scripts/telecharger-corpus-kotlin.sh',
                  'scripts/vendor-grammaire-kotlin.sh'):
        return 'kotlin', 'lsp-kotlin/scripts/' + ancien[len('scripts/'):], None

    # --- scripts communs ----------------------------------------------------
    if ancien in ('scripts/verifier-push.sh', 'scripts/verifier-provenance.sh',
                  'scripts/generer-migration-chemins.py'):
        return 'transverse', ancien, None

    raise SystemExit(f'NON CLASSÉ : {ancien} — complète les règles ou DECISIONS.')


def mapper_decision(ancien: str, classe: str) -> str:
    """Destination recommandée pour un fichier à décision."""
    if classe == 'java':
        if ancien == 'USAGE.md':
            return 'lsp-java/USAGE.md'
        if ancien.startswith('bancs/'):
            return 'lsp-java/bancs/' + ancien[len('bancs/'):]
        if ancien.startswith('scripts/'):
            return 'lsp-java/scripts/' + ancien[len('scripts/'):]
        return 'lsp-java/docs/' + ancien[len('docs/'):]
    if classe == 'transverse':
        if ancien.startswith('bancs/'):
            return 'docs/' + ancien[len('bancs/'):]      # gabarit commun → docs/
        return ancien                                       # reste en docs/ transverse
    raise ValueError(classe)


def principal(chemin: str) -> str:
    """Dossier/fichier « racine » d'un chemin (premier segment)."""
    return chemin.split('/', 1)[0] if '/' in chemin else chemin


def main():
    ls = subprocess.run(
        ['git', '-c', 'core.quotepath=false', 'ls-files'],
        capture_output=True, text=True, check=True).stdout.splitlines()

    lignes = []
    stats = Counter()
    non_classes = []
    for ancien in ls:
        try:
            classe, nouveau, decision = regle(ancien)
        except SystemExit as e:
            non_classes.append(str(e))
            continue
        lignes.append((ancien, nouveau, classe, decision))
        stats[classe] += 1

    if non_classes:
        print('\n'.join(non_classes), file=sys.stderr)
        sys.exit(1)

    # --- table principale : une ligne par dossier/fichier racine -------------
    racines = {}
    for ancien, nouveau, classe, decision in lignes:
        r = principal(ancien)
        racines.setdefault(r, {'classes': Counter(), 'decision': None, 'exemples': []})
        racines[r]['classes'][classe] += 1
        if decision:
            racines[r]['decision'] = decision
        if len(racines[r]['exemples']) < 3 and ancien != r:
            racines[r]['exemples'].append((ancien, nouveau))

    out = []
    out.append('# MIGRATION-CHEMINS — table ancien → nouveau (Phase 1, en attente de validation)')
    out.append('')
    out.append('> Générée par `scripts/generer-migration-chemins.py` (relue à la main) à partir de')
    out.append('> `git ls-files` de la branche `integration/fusion`. **Aucun déplacement effectué**')
    out.append('> — la table devient exécutoire après validation du plan par le propriétaire')
    out.append('> (`docs/REORGANISATION.md`). Les chemins marqués D-x portent une décision ouverte')
    out.append('> (recommandation de l’agent) ; les autres sont directs.')
    out.append('')
    out.append(f'Fichiers classés : {len(lignes)} — ' +
               ' · '.join(f'{c} : {n}' for c, n in sorted(stats.items())) + '.')
    out.append('')
    out.append('## 1. Table racine (une ligne par dossier/fichier racine)')
    out.append('')
    out.append('| Ancien | Nouveau | Classe(s) | Fichiers | Décision |')
    out.append('|---|---|---|---:|---|')
    for r in sorted(racines, key=lambda x: (x.lower(),)):
        info = racines[r]
        est_dossier = any(a != r for a in ls if a.startswith(r + '/'))
        etiquette = f'{r}/' if est_dossier else r
        classes = ' + '.join(sorted(info['classes']))
        n = sum(info['classes'].values())
        if len(info['classes']) == 1:
            c = list(info['classes'])[0]
            if c == 'socle':
                dest = f'socle/{r}' if r in ('index', 'model', 'testing', 'ecj-art') else r
            elif c == 'java':
                dest = ('lsp-java/moteur/' if r == 'java' else
                        'lsp-java/spike/' if r == 'spike' else
                        'lsp-java/USAGE.md' if r == 'USAGE.md' else
                        'lsp-java/docs/… + lsp-java/bancs/… (éclaté)' if r == 'docs' else
                        'lsp-java/… (éclaté)')
            elif c == 'kotlin':
                dest = ('lsp-kotlin/moteur/' if r == 'kotlin' else
                        'lsp-kotlin/spike/' if r == 'spike-kotlin' else
                        'lsp-kotlin/… (éclaté)')
            else:
                dest = r
        else:
            dest = 'ÉCLATÉ (voir §2–§4)'
        d = info['decision'] or ''
        out.append(f'| `{etiquette}` | `{dest}` | {classes} | {n} | {d} |')
    out.append('')
    out.append('Sections 2–4 : détail par fichier pour les répertoires éclatés '
               '(`docs/`, `bancs/`, `scripts/`, fichiers racine).')
    out.append('')

    # --- détail par fichier (annexes) ----------------------------------------
    for titre, prefixes in (
        ('2. Détail — documents (`docs/`)', ('docs/',)),
        ('3. Détail — bancs (`bancs/`)', ('bancs/',)),
        ('4. Détail — scripts et fichiers racine', ('scripts/',)),
    ):
        out.append(f'## {titre}')
        out.append('')
        out.append('| Ancien | Nouveau | Classe | Décision |')
        out.append('|---|---|---|---|')
        for ancien, nouveau, classe, decision in lignes:
            if ancien.startswith(prefixes) or ('/' not in ancien and 'scripts' in titre):
                out.append(f'| `{ancien}` | `{nouveau}` | {classe} | {decision or ""} |')
        out.append('')

    with open('docs/MIGRATION-CHEMINS.md', 'w', encoding='utf-8') as f:
        f.write('\n'.join(out) + '\n')

    print(f'OK : docs/MIGRATION-CHEMINS.md — {len(lignes)} fichiers classés')
    for c, n in sorted(stats.items()):
        print(f'  {c:11s} {n}')


if __name__ == '__main__':
    main()
