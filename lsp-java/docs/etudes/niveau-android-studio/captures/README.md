# Captures Android Studio — mode d'emploi du kit

> Complément d'exécution du [`06-protocole-captures-android-studio.md`](../06-protocole-captures-android-studio.md) :
> ce dossier contient le **kit** (scénarios prêts à jouer) et les **gabarits
> de restitution** (un par lot). Le propriétaire capture, l'agent consigne
> (protocole §1). Rappel R5 : **aucune valeur inventée** — une case vide
> reste vide avec sa raison.

## 1. Ordre conseillé (par valeur pour la mission)

1. **Lot A (complétion)** — le plus urgent (demande du 31/10) : A1→A10.
2. **Lot B** — B6 et B4 d'abord (confirment les écarts P0 G-001/G-010), puis
   B1→B3, B5, B7.
3. **Lot D** — D1/D2 (alimentent les classes light, V-B), D3, D5.
4. **Lot C** (performance perçue) — le plus long (5 projets du corpus).

## 2. Préparation (une fois, ~10 min)

1. Android Studio **Ladybug ou plus récent**, projet de test = copie locale
   de `bancs/projet-android/` (ne pas committer les modifications locales).
2. Copier [`kit/KitCapturesAs.java`](kit/KitCapturesAs.java) vers
   `app/src/main/java/com/example/monip/KitCapturesAs.java`.
3. Copier [`kit/res-values-strings.xml`](kit/res-values-strings.xml) vers
   `app/src/main/res/values/strings.xml` (le banc n'en a aucune).
4. Pour A10 : ajouter localement dans `res/layout/activity_main.xml` un
   `<Button android:id="@+id/bouton_envoyer" …/>`.
5. Ouvrir le projet, **attendre la fin de l'indexation** (sauf scénario C4).
6. Pour B6 : rouvrir une copie **fraîche** du projet (sans build) — voir le
   gabarit du lot B.

## 3. Pour chaque capture (protocole §1)

- (a) fichier + position exacte (ligne:colonne) ;
- (b) geste (caret, frappe, Ctrl+Espace, Alt+Enter…) ;
- (c) **liste top-10 copiée en texte** — l'ordre compte, l'image ne suffit
  jamais seule ;
- (d) temps perçu (immédiat / visible / lent).

Images dans `images/` (créer le dossier), nommées `A1-1.png`, `A1-2.png`…
Transcriptions dans [`RESULTATS-LOT-A.md`](RESULTATS-LOT-A.md) →
[`RESULTATS-LOT-B.md`](RESULTATS-LOT-B.md) →
[`RESULTATS-LOT-C.md`](RESULTATS-LOT-C.md) →
[`RESULTATS-LOT-D.md`](RESULTATS-LOT-D.md).

## 4. Règles clean-room (§1.3 du prompt mission)

Ces captures sont des **données de spécification** : on décrit des
comportements (ordres de listes, messages, sévérités), on ne copie aucun
fichier source d'IntelliJ/Android Studio. Les extraits du kit sont nôtres.

## 5. Restitution

Deux voies équivalentes : (i) commit direct par le propriétaire sur
`etude/moteur-java` (après `scripts/verifier-push.sh`), ou (ii) envoi des
fichiers remplis + images à l'agent, qui consigne, committe et pousse.
