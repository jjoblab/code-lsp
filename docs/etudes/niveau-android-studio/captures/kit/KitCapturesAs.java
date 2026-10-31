package com.example.monip;

/*
 * ============================================================================
 * KIT DE CAPTURES ANDROID STUDIO — lots A (complétion), B (diagnostics) et
 * point D4. Référence : docs/etudes/niveau-android-studio/
 * 06-protocole-captures-android-studio.md §2.
 *
 * MODE D'EMPLOI (copie locale, JAMAIS committée) :
 *  1. copier ce fichier vers bancs/projet-android/app/src/main/java/
 *     com/example/monip/KitCapturesAs.java ;
 *  2. copier kit/res-values-strings.xml vers app/src/main/res/values/
 *     strings.xml (le projet du banc n'en contient aucune) ;
 *  3. ouvrir le projet dans Android Studio (Ladybug ou plus récent) et
 *     ATTENDRE la fin de l'indexation avant les captures (sauf scénario C4,
 *     qui se joue PENDANT l'indexation) ;
 *  4. pour chaque bloc : placer le caret à l'emplacement décrit par le
 *     commentaire « CARET », invoquer la complétion (Ctrl+Espace ou frappe)
 *     et TRANSCRIRE EN TEXTE la liste proposée — l'ordre compte (§1c) ;
 *  5. reporter chaque résultat dans captures/RESULTATS-LOT-A.md (ou B/D).
 *
 * Ce fichier ne compile PAS volontairement : les positions de capture
 * simulent une frappe en cours (énoncés incomplets). Support de
 * spécification, pas du code de production.
 * ============================================================================
 */

import androidx.annotation.CallSuper;
import androidx.annotation.Dimension;
import androidx.annotation.IntDef;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nonnull;
import org.jetbrains.annotations.Nullable;

/*
 * A9 — rappel du protocole (à taper puis annuler, pas à laisser dans le
 * fichier) : sur une ligne vide de la section imports ci-dessus, taper
 * « import ja » (caret après « ja »), puis « import i », puis « i » seul
 * en tête de fichier ; transcrire le top-10 à chaque étape.
 */

// ============================================================================
// Supports (aucune capture directe ici)
// ============================================================================

/** Modes de notification reconnus par {@link Reglages#setMode(int)} (A5). */
@IntDef({Reglages.MODE_SILENCE, Reglages.MODE_VIBREUR, Reglages.MODE_SONNERIE})
@interface Mode {
}

/** Constantes typedef pour le scénario A5. */
class Reglages {
    static final int MODE_SILENCE = 0;
    static final int MODE_VIBREUR = 1;
    static final int MODE_SONNERIE = 2;

    void setMode(@Mode int mode) {
        // vide — seul le contrat compte
    }
}

/** Chaîne de getters pour la complétion « chain » (A3/A4). */
class FilA {
    FilB getB() {
        return new FilB();
    }
}

/** Maillon B de la chaîne (A3/A4). */
class FilB {
    FilC getC() {
        return new FilC();
    }

    String getNom() {
        return "monip";
    }
}

/** Maillon C de la chaîne (A3/A4). */
class FilC {
    int getTaille() {
        return 3;
    }
}

// ============================================================================
// LOT A — complétion
// ============================================================================

/** Scénarios de complétion du lot A. */
class ScenariosLotA {

    /**
     * A1 — smart : filtrage par type attendu.
     * CARET : en fin de ligne « String s =  » (après l'espace).
     * À transcrire : positions relatives de « nom », « villes » (compatibles
     * String) et « age » (non compatible — présent ou absent ?), puis
     * membres de String, méthodes statiques, mots-clés…
     */
    void a1Smart() {
        String nom = "monip";
        int age = 3;
        List<String> villes = new ArrayList<>();
        String s =
    }

    /**
     * A2 — argument typé + surcharges.
     * CARET : juste après « villes.add( » (parenthèse ouverte).
     * À transcrire : liste proposée + comportement de l'aide de signature
     * face aux deux surcharges de List#add.
     */
    void a2Argument(List<String> villes) {
        villes.add(
    }

    /**
     * A3 — chain, 1re invocation.
     * CARET : juste après « new FilA().getB().get ».
     * À transcrire : que propose la 1re invocation (préfixe « get »).
     */
    void a3Chaine() {
        new FilA().getB().get
    }

    /*
     * A4 — 2e puis 3e invocation À LA MÊME POSITION que a3Chaine
     * (Ctrl+Espace répété) : membres inaccessibles (marqués comment ?),
     * proposition d'import statique, bascule vers le projet entier.
     * Aucun code dédié : rejouer a3Chaine et noter l'évolution de la liste.
     */

    /**
     * A5 — constantes typedef (@IntDef).
     * CARET : juste après « r.setMode( ».
     * À transcrire : ordre des constantes MODE_*, filtrage (autres int
     * proposés ?), import automatique des constantes si non statiques.
     */
    void a5Typedef(Reglages r) {
        r.setMode(
    }

    /**
     * A6 — ressources chaîne.
     * CARET 1 : juste après « R.string. » (ligne id) ;
     * CARET 2 : juste après « c.getString(R.string. ».
     * À transcrire : ordre, décoration (icônes ?), ressources privées ou
     * d'autres modules filtrées ?
     */
    void a6Ressources(android.content.Context c) {
        int id = R.string.
        String t = c.getString(R.string.
    }

    /**
     * A7 — littéral de chaîne contextuel.
     * CARET : juste après la quote ouvrante de makeText(c, ".
     * À transcrire : des propositions apparaissent-elles DANS la chaîne ?
     */
    void a7Litteral(android.content.Context c) {
        android.widget.Toast.makeText(c, "
    }

    /**
     * A10 — suggestion de nom depuis le type.
     * Prérequis local (copie non committée) : ajouter dans
     * res/layout/activity_main.xml un Button android:id="@+id/bouton_envoyer"
     * puis laisser AS resynchroniser.
     * CARET : juste après « Button » (l'import android.widget.Button étant
     * résolu) puis invoquer la complétion.
     * À transcrire : les noms proposés, dans l'ordre exact.
     */
    void a10Nom() {
        Button
    }
}

// ============================================================================
// LOT B — diagnostics et inspections
// ============================================================================

/** Scénarios d'inspections du lot B. */
class ScenariosLotB {

    /**
     * B1 — comparaison de références sur String.
     * Geste : caret sur « == » puis Alt+Enter.
     * À capturer : message exact du quick-fix « equals » + présence de
     * l'option « unsafe » dans la liste.
     */
    void b1Egalite(String a, String b) {
        boolean pareil = a == b;
    }

    /**
     * B3 — suppression par identifiant.
     * À capturer : @SuppressWarnings("StringEquality") est-il accepté sans
     * soulignement ? Sinon, quel token AS propose-t-il ?
     */
    @SuppressWarnings("StringEquality")
    void b3Suppression(String a, String b) {
        boolean pareil = a == b;
    }

    /**
     * B2 — nullité : @Nonnull en retour d'un champ @Nullable.
     * À capturer : sévérité affichée (Warning / Weak Warning), message exact
     * au survol (DataFlowIssue ou équivalent).
     */
    static class Nullite {
        @Nullable
        private String champ;

        @Nonnull
        String lire() {
            return champ;
        }
    }

    /**
     * B4 — NewApi : appel API 26+ sous minSdk 24 (sans désucrage activé).
     * À capturer : sévérité, message exact, ET la liste complète des fixes
     * proposés (garde SDK_INT, @RequiresApi, « do not show »…).
     */
    void b4Api26() {
        String stamp = java.time.LocalDate.now().toString();
    }

    /**
     * B4 (suite) — le même appel SOUS garde.
     * À capturer : la signalisation disparaît-elle entièrement ?
     */
    void b4Api26Garde() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            String stamp = java.time.LocalDate.now().toString();
        }
    }

    /**
     * D4 — @Dimension(unit = PX) : diagnostic ou complétion associée ?
     * CARET : juste après « poserMarge( ».
     * À capturer : le littéral 8 est-il signalé quelque part ? la
     * complétion propose-t-elle une conversion (8dp…) ?
     */
    static void poserMarge(@Dimension(unit = Dimension.PX) int px) {
        // vide
    }

    /** Appel du scénario D4. */
    void d4Appel() {
        poserMarge(
    }
}

/** Base du scénario B5 (@CallSuper). */
class B5Base {

    @CallSuper
    protected void onStart() {
    }
}

/**
 * B5 — super non appelé.
 * À capturer : message exact + fix proposé (insertion de super.onStart()).
 */
class B5Fille extends B5Base {

    @Override
    protected void onStart() {
    }
}

/*
 * A8 — mots-clés de tête de déclaration (amendement fiche 007 — D7).
 * Ce bloc est VOLONTAIREMENT en fin de fichier : placer le caret juste
 * après « class Principal e » (après la lettre « e ») et invoquer la
 * complétion. À transcrire : « extends » et « implements » apparaissent-ils
 * en même temps que les stubs de classes/interfaces ?
 */
class Principal e
