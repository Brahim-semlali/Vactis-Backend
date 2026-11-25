package com.vactis.service;

import com.vactis.model.Controle.Controle;
import com.vactis.model.Controle.TypeControle;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Paliers dynamiques de la table Contrôle (Rupture, Urgence, Fiabilité, Délai d'action).
 *
 * Convention des paliers : borne min incluse, borne max exclue ([min ; max[),
 * sauf pour le palier le plus haut dont la borne max est incluse. Une valeur
 * sous le premier palier prend le premier, une valeur au-dessus du dernier prend le dernier.
 * Une valeur tombant dans un trou entre deux paliers retombe sur les règles par défaut.
 *
 * Les valeurs par défaut reprennent à l'identique les règles VACTIS codées
 * jusqu'ici dans le moteur ; elles servent à initialiser la table et de repli.
 */
public final class PaliersControle {

    /** États autorisés par type : le moteur s'appuie sur ces libellés exacts. */
    public static final Map<TypeControle, Set<String>> ETATS_AUTORISES = Map.of(
            TypeControle.RUPTURE, Set.of("AUCUNE", "SURVEILLANCE", "CRITIQUE"),
            TypeControle.URGENCE, Set.of("FAIBLE", "MOYEN", "ELEVE", "URGENT"),
            TypeControle.FIABILITE, Set.of("NON_FIABLE", "PARTIEL", "FIABLE"),
            TypeControle.DELAI_ACTION, Set.of("SILENCE_STRATEGIQUE", "CRITIQUE", "ELEVEE", "NORMALE")
    );

    /** Plafond technique du dernier palier (valeurs « illimitées »). */
    public static final long PLAFOND = 100_000L;

    private static final Map<TypeControle, List<Controle>> DEFAUTS = Map.of(
            // Ratio jours de silence / intervalle moyen, exprimé en % (150 = 1,5×)
            TypeControle.RUPTURE, List.of(
                    palier(TypeControle.RUPTURE, "AUCUNE", 0, 150),
                    palier(TypeControle.RUPTURE, "SURVEILLANCE", 150, 200),
                    palier(TypeControle.RUPTURE, "CRITIQUE", 200, PLAFOND)),
            // Score d'urgence finale (0-100)
            TypeControle.URGENCE, List.of(
                    palier(TypeControle.URGENCE, "FAIBLE", 0, 25),
                    palier(TypeControle.URGENCE, "MOYEN", 25, 50),
                    palier(TypeControle.URGENCE, "ELEVE", 50, 75),
                    palier(TypeControle.URGENCE, "URGENT", 75, 100)),
            // Nombre de mois actifs (FIABLE exige en plus 6 mois d'ancienneté)
            TypeControle.FIABILITE, List.of(
                    palier(TypeControle.FIABILITE, "NON_FIABLE", 0, 2),
                    palier(TypeControle.FIABILITE, "PARTIEL", 2, 3),
                    palier(TypeControle.FIABILITE, "FIABLE", 3, 1_200)),
            // Niveau d'intensité -> délai d'action recommandé en jours
            TypeControle.DELAI_ACTION, List.of(
                    delai("SILENCE_STRATEGIQUE", 3),
                    delai("CRITIQUE", 7),
                    delai("ELEVEE", 15),
                    delai("NORMALE", 30))
    );

    private final TypeControle type;
    private final List<Controle> regles;

    private PaliersControle(TypeControle type, List<Controle> regles) {
        this.type = type;
        this.regles = regles.stream()
                .filter(Objects::nonNull)
                .filter(r -> r.getEtat() != null && !r.getEtat().isBlank())
                .sorted(Comparator.comparing(Controle::getMinCA, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    /** Paliers construits à partir des règles actives ; repli sur les défauts si aucune règle. */
    public static PaliersControle de(TypeControle type, List<Controle> reglesActives) {
        if (reglesActives == null || reglesActives.isEmpty()) return defauts(type);
        return new PaliersControle(type, reglesActives);
    }

    public static PaliersControle defauts(TypeControle type) {
        return new PaliersControle(type, DEFAUTS.getOrDefault(type, List.of()));
    }

    /** Retourne les paliers fournis, ou les défauts si absents (ex. dépendance non injectée). */
    public static PaliersControle ou(PaliersControle paliers, TypeControle type) {
        return paliers != null ? paliers : defauts(type);
    }

    /** Copies des règles par défaut d'un type, prêtes à être enregistrées. */
    public static List<Controle> reglesParDefaut(TypeControle type) {
        return DEFAUTS.getOrDefault(type, List.of()).stream().map(PaliersControle::copie).toList();
    }

    public TypeControle type() {
        return type;
    }

    /** État du palier contenant la valeur ([min ; max[), ou état par défaut en cas de trou. */
    public String etatPour(double valeur) {
        String etat = chercher(regles, valeur);
        if (etat != null) return etat;
        return chercher(DEFAUTS.getOrDefault(type, List.of()), valeur);
    }

    /** Valeur (ex. délai en jours) associée à un état ; défaut si non configurée. */
    public Integer valeurPour(String etat) {
        if (etat == null) return null;
        String cle = normaliser(etat);
        Integer valeur = trouverValeur(regles, cle);
        return valeur != null ? valeur : trouverValeur(DEFAUTS.getOrDefault(type, List.of()), cle);
    }

    private static String chercher(List<Controle> regles, double valeur) {
        List<Controle> paliers = regles.stream()
                .filter(r -> r.getMinCA() != null && r.getMaxCA() != null)
                .sorted(Comparator.comparing(Controle::getMinCA))
                .toList();
        if (paliers.isEmpty()) return null;

        Controle premier = paliers.get(0);
        Controle dernier = paliers.stream().max(Comparator.comparing(Controle::getMaxCA)).orElse(premier);
        if (valeur < premier.getMinCA()) return premier.getEtat();
        if (valeur >= dernier.getMaxCA()) return dernier.getEtat();

        for (Controle palier : paliers) {
            if (valeur >= palier.getMinCA() && valeur < palier.getMaxCA()) {
                return palier.getEtat();
            }
        }
        return null;
    }

    private static Integer trouverValeur(List<Controle> regles, String cle) {
        Map<String, Integer> parEtat = new HashMap<>();
        for (Controle r : regles) {
            if (r.getValeur() != null) parEtat.putIfAbsent(normaliser(r.getEtat()), r.getValeur().intValue());
        }
        return parEtat.get(cle);
    }

    static String normaliser(String etat) {
        String upper = etat.trim().toUpperCase(Locale.ROOT);
        return "ÉLEVÉE".equals(upper) ? "ELEVEE" : upper;
    }

    private static Controle palier(TypeControle type, String etat, long min, long max) {
        Controle c = new Controle();
        c.setType(type);
        c.setEtat(etat);
        c.setMinCA(min);
        c.setMaxCA(max);
        c.setActif(true);
        return c;
    }

    private static Controle delai(String etat, long jours) {
        Controle c = new Controle();
        c.setType(TypeControle.DELAI_ACTION);
        c.setEtat(etat);
        c.setValeur(jours);
        c.setActif(true);
        return c;
    }

    private static Controle copie(Controle source) {
        Controle c = new Controle();
        c.setType(source.getType());
        c.setEtat(source.getEtat());
        c.setMinCA(source.getMinCA());
        c.setMaxCA(source.getMaxCA());
        c.setValeur(source.getValeur());
        c.setActif(true);
        return c;
    }
}
