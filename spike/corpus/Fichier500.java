package corpus.synthetique;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Classe générée pour le banc de parse (étude moteur Java PSI). */
public final class Fichier500 {

    private final String identifiant;
    private final int capacite;
    private final Map<String, List<Integer>> cache = new HashMap<>();
    private static final double SEUIL = 0.5d;


    /** Méthode générée n°1. */
    public Optional<Integer> traiterDonnee1(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 7;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return Optional.of(total);
    }

    /** Méthode générée n°2. */
    public Optional<Integer> traiterDonnee2(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 6;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return Optional.of(total);
    }

    /** Méthode générée n°3. */
    public long traiterDonnee3(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 7;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return (long) total;
    }

    /** Méthode générée n°4. */
    public long traiterDonnee4(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 6;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return (long) total;
    }

    /** Méthode générée n°5. */
    public long traiterDonnee5(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 4;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return (long) total;
    }

    /** Méthode générée n°6. */
    public boolean traiterDonnee6(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 8;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return total > borne && libelle.length() > 2;
    }

    /** Méthode générée n°7. */
    public long traiterDonnee7(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 3;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return (long) total;
    }

    /** Méthode générée n°8. */
    public double traiterDonnee8(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 5;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return total * 7.8;
    }

    /** Méthode générée n°9. */
    public boolean traiterDonnee9(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 2;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return total > borne && libelle.length() > 2;
    }

    /** Méthode générée n°10. */
    public long traiterDonnee10(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 7;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return (long) total;
    }

    /** Méthode générée n°11. */
    public String traiterDonnee11(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 7;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return libelle + '_' + total;
    }

    /** Méthode générée n°12. */
    public Optional<Integer> traiterDonnee12(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 6;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return Optional.of(total);
    }

    /** Méthode générée n°13. */
    public long traiterDonnee13(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 9;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return (long) total;
    }

    /** Méthode générée n°14. */
    public boolean traiterDonnee14(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 5;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return total > borne && libelle.length() > 2;
    }

    /** Méthode générée n°15. */
    public boolean traiterDonnee15(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 7;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return total > borne && libelle.length() > 2;
    }

    /** Méthode générée n°16. */
    public Optional<Integer> traiterDonnee16(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 9;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return Optional.of(total);
    }

    /** Méthode générée n°17. */
    public boolean traiterDonnee17(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 9;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return total > borne && libelle.length() > 2;
    }

    /** Méthode générée n°18. */
    public int traiterDonnee18(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 1;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return (int) total;
    }

    /** Méthode générée n°19. */
    public boolean traiterDonnee19(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 3;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return total > borne && libelle.length() > 2;
    }

    /** Méthode générée n°20. */
    public long traiterDonnee20(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 7;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return (long) total;
    }

    /** Méthode générée n°21. */
    public Optional<Integer> traiterDonnee21(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 1;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return Optional.of(total);
    }

    /** Méthode générée n°22. */
    public double traiterDonnee22(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 6;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return total * 8.22;
    }

    /** Méthode générée n°23. */
    public boolean traiterDonnee23(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 7;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return total > borne && libelle.length() > 2;
    }

    /** Méthode générée n°24. */
    public String traiterDonnee24(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 4;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return libelle + '_' + total;
    }

    /** Méthode générée n°25. */
    public Optional<Integer> traiterDonnee25(int borne, String libelle) {
        List<Integer> valeurs = cache.computeIfAbsent(libelle, k -> new ArrayList<>());
        int total = 0;
        for (int i = 0; i < borne; i++) {
            if (i % 3 == 0) {
                total += i * 1;
            } else if (i % 5 == 0) {
                valeurs.add(i);
            }
        }
        switch (borne % 4) {
            case 0 -> { return convertir(total, libelle); }
            case 1 -> { return null; }
            default -> { /* rien */ }
        }
        return Optional.of(total);
    }

    private static int convertir(int v, String s) {
        return v + (s == null ? 0 : s.hashCode());
    }
}
