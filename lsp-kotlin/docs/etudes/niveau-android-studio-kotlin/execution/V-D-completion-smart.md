# Journal d'exécution — Vague V-D : Complétion smart/chain/DSL (P1)

> Vague V-D du [plan](../04-plan-vagues.md) — exécutée le **12/11/2026**.
> Périmètre (KC-01/KC-02/KC-04 W1/KC-05, ADR 0126) : classement SMART par
> type attendu, chaînes d'extensions applicables (profondeur ≤ 2),
> déclassement des constantes citées d'un `when`, membres OUVRABLES après
> `override`. Le banc d'étiquetage 50+ positions (E-K11) est REPORTÉ
> (bornologie §3).

## 1. Ce qui est livré

1. **Le peseur `PeseurTypeAttenduKotlin`** (KC-01) : deuxième dimension de
   la chaîne standard (après la correspondance de préfixe, avant la
   nature) — les propositions dont le DÉTAIL porte le type attendu
   (`Ecran`, FQCN, retour de signature) remontent d'une dimension ; les
   autres restent visibles (le tri ne CACHE jamais, T6). Le peseur se
   TAIT sans types attendus (poids constant) : la chaîne est alors
   l'ancienne à l'identique.
2. **Les types attendus démontables** (`CompletionSmart.typesAttendus`) :
   T1 argument d'appel typé (paramètre à la place ordinale, surcharges du
   projet), T2 `return` de fonction typée, T3 initialisateur typé
   (`val/var x: T = |` — fenêtre du jeton `=`), T4 branche d'`if`/`when`
   expression typée par la branche sœur complète (zones de branche : une
   branche VIDE a une plage nulle, le caret vit entre le `)` de l'en-tête
   et le `else`).
3. **Amendement de la fiche 043** (consigné dans KC-01) : la position
   d'argument d'un appel propose les arguments nommés ET les expressions
   de la portée (le type attendu du paramètre ordonne) ; les contextes
   CONSTANTES_ENUM et ATTRIBUTS_ANNOTATION restent EXCLUSIFS.
4. **Les chaînes d'extensions** (KC-02 E1/E2) : au point d'un sélecteur
   `recepteur.|` typé avec type attendu, les extensions applicables
   (projet + classpath par la chaîne de supertypes) retournant le type
   attendu — profondeur 1 directe, puis profondeur 2 en UN SEUL item
   (`auteur().nomComplet()`). E4 : sans type attendu, aucune spéculation.
5. **Le déclassement des citées** (KC-04 W1) : les constantes DÉJÀ CITÉES
   dans les conditions des branches du `when` couvrant le caret
   descendent en fin de liste — jamais cachées. W2 (sealed) suit la
   preuve d'ensemble (V-C) : reporté avec elle.
6. **Le contexte `OVERRIDE`** (KC-05) : après `override fun|val|var ` dans
   le corps d'une classe à supertypes — les membres ABSTRAITS (lus sur
   l'ARBRE : le prédicat modèle `estAbstrait` blanchit BLOCK par erreur
   latente — contournée, non corrigée dans le modèle) puis les membres
   OUVERTS (`open` lu sur la déclaration source ; interface = ouverte par
   nature), hors membres déjà surchargés (O1) ; l'insertion porte la
   SIGNATURE COMPLÈTE avec corps `TODO(nom)` (O2/O3, miroir
   MembresManquants) ; le classement déclasse les OUVERTS derrière les
   ABSTRAITS.
7. **La portée des paramètres dans un initialiseur local**
   (`CompletionNoms.localesEtParametres`) : une propriété LOCALE est une
   région propre, mais la portée Kotlin garde les paramètres de la
   fonction porteuse visibles dans l'initialiseur (KC-01 T3) — les
   paramètres se lisent désormais de la plus profonde fonction
   englobante.

## 2. Les preuves

| Preuve | Résultat |
|---|---|
| Tests moteur (`CompletionSmartKotlinTest`) | **12 verts** — T1–T6 (argument, return, initialisateur val/var, branche if, sans preuve = inchangé), E1/E2/E4 (extension directe, chaîne profondeur 2, repli muet), W1 (citées déclassées non cachées), O1/O2/O3/O5 (ouvrables, signature complète, propriétés, déjà surchargé absent, repli NOM sans supertype) |
| Suite `:kotlin:test` | **577 verts** (564 + 12 + resynchronisation), 3 ignorés (gates corpus) |
| Re-signature du digeste (E-K9) | par la COMMANDE OFFICIELLE fiche 010 ; le diff porte les 5 fichiers touchés, dont 2 neufs (`CompletionSmart.kt`, `CompletionSmartKotlinTest.kt`) |
| Régression | aucune — fiches 043/045/046 et bancs P1–P9 inchangés |

## 3. Bornologie (honnêteté D3)

- **Le banc d'étiquetage 50+ positions avec split train/éval (E-K11) est
  REPORTÉ** : le critère top-1 ≥ 0,80/MRR du plan §2 n'est PAS mesuré
  ici — les 12 tests verrouillent les COMPORTEMENTS (ordres relatifs),
  pas la précision positionnelle à l'échelle ;
- le type attendu T1 se lit des appelables du PROJET (le classpath
  exige le résolveur d'appel complet — frontière SPECIFICATION-TYPES) ;
- T4 prend la PREMIÈRE branche complète lisible (pas l'inférence
  complète — relève du compilateur) ;
- les chaînes de profondeur 2 se bornent aux maillons DÉMONTRABLES (un
  maillon non typé n'offre rien, E2) ;
- KC-04 W2 (sealed) suit la preuve d'ensemble des hiérarchies sealed
  (reportée avec V-C) ;
- l'appel du super optionnel (KC-05 O4) relève du réglage client.
