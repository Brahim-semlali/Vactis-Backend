package com.vactis.service;

import com.vactis.model.action.Action;
import com.vactis.model.medecin.Medecin;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "jwt.secret=VactisTestSecretKeyMustBeAtLeastThirtyTwoChars",
        "cors.allowed-origins=http://localhost:5173",
        "VACTIS_ADMIN_PASSWORD=VactisTestAdminPassword123!"
})
@Transactional
class JeuDonneesStatutsMai2026Test {

    private static final LocalDate DATE_REFERENCE = LocalDate.of(2026, 5, 31);
        private static final LocalDate DATE_REFERENCE_SEPTEMBRE = LocalDate.of(2026, 9, 30);

    private static final Map<String, String> ATTENDUS_STATUTS = Map.ofEntries(
            Map.entry("OULAD",        "PROGRESSION"),
            Map.entry("MANSOURI",     "PROGRESSION"),
            Map.entry("ALAOUI",       "PROGRESSION"),
            Map.entry("SAIDI",        "PROGRESSION"),
            Map.entry("CHRAIBI",      "PROGRESSION"),
            Map.entry("BENJELLOUN",   "PROGRESSION"),
            Map.entry("LAKHDAR",      "PROGRESSION"),
            Map.entry("ZEROUALI",     "PROGRESSION"),
            Map.entry("EL IDRISSI",   "ACTIF_STABLE"),
            Map.entry("RAHMANI",      "ACTIF_STABLE"),
            Map.entry("BENALI",       "ACTIF_STABLE"),
            Map.entry("AIT TALEB",    "ACTIF_STABLE"),
            Map.entry("BERRADA",      "ACTIF_STABLE"),
            Map.entry("BOUKILI",      "SURVEILLANCE"),
            Map.entry("EL HARRAK",    "SURVEILLANCE"),
            Map.entry("KABBAJ",       "SURVEILLANCE"),
            Map.entry("AMRANI",       "RETENTION"),
            Map.entry("EL GHAZOUANI","RETENTION"),
            Map.entry("TAZI",         "RETENTION"),
            Map.entry("EL FASSI",     "INACTIF")
    );

    // Libellés attendus par statut - codes techniques de ActionService.libelleActionPourStatut()
    private static final Map<String, String> LIBELLE_PAR_STATUT = Map.of(
            "PROGRESSION",  "visite_reconnaissance",
            "ACTIF_STABLE", "fidelisation",
            "SURVEILLANCE", "visite_diagnostic_surveillance",
            "RETENTION",    "visite_diagnostic",
            "ONBOARDING",   "visite_qualification"
    );


    // Segments attendus avec CA moyen historique hors mois courant (juillet 2026)
    // Performance CA = rang percentile sur CA moyen de TOUT l'historique hors mois courant
    private static final Map<String, String> ATTENDUS_SEGMENTS = Map.ofEntries(
            Map.entry("BOUKILI",       "A"),
            Map.entry("EL HARRAK",     "A"),
            Map.entry("AMRANI",        "B"),
            Map.entry("RAHMANI",       "B"),
            Map.entry("TAZI",          "B"),
            Map.entry("KABBAJ",        "B"),
            Map.entry("BERRADA",       "B"),
            Map.entry("EL GHAZOUANI", "C"),
            Map.entry("OULAD",         "C"),
            Map.entry("AIT TALEB",     "C"),
            Map.entry("CHRAIBI",       "C"),
            Map.entry("LAKHDAR",       "C"),
            Map.entry("BENJELLOUN",    "B"),
            Map.entry("EL IDRISSI",    "C"),
            Map.entry("ZEROUALI",      "D"),
            Map.entry("ALAOUI",        "D"),
            Map.entry("BENALI",        "D"),
            Map.entry("MANSOURI",      "D"),
            Map.entry("SAIDI",         "D"),
            Map.entry("EL FASSI",      "D")
    );

    @Autowired private ExcelImportService excelImportService;
    @Autowired private MedecinService medecinService;
    @Autowired private MedecinRepository medecinRepository;
        @Autowired private ExtractionDonneesRepository extractionDonneesRepository;
    @Autowired private ActionRepository actionRepository;
    @Autowired private ActionService actionService;

    @Test
    void importsWorkbookAndChecksMay2026StatusesAndGeneratedActions() throws Exception {
        try (InputStream workbook = getClass().getResourceAsStream("/data/data_fictif_test_vactis.xlsx")) {
            assertTrue(workbook != null, "Le classeur de test doit \u00eatre pr\u00e9sent dans src/main/resources/data.");
            excelImportService.importFromExcelStream(workbook);
        }

        medecinService.recalculerStatutsEtSegmentsDynamiques(DATE_REFERENCE);
        List<Medecin> medecins = medecinRepository.findAll().stream()
                .filter(medecin -> !medecinRepository.isPseudoMedecin(medecin)).toList();
        actionRepository.deleteAllInBatch();
        excelImportService.generateActionsForMedecins(medecins, DATE_REFERENCE);
        actionService.syncActionsWithMedecins();
        Map<String, Medecin> parNom = new LinkedHashMap<>();
        ATTENDUS_STATUTS.keySet().forEach(surname -> parNom.put(surname, findBySurname(medecins, surname)));

        List<String> ecartsStatut = new ArrayList<>();
        for (Map.Entry<String, String> attendu : ATTENDUS_STATUTS.entrySet()) {
            Medecin obtenu = parNom.get(attendu.getKey());
            String actual = obtenu == null ? "m\u00e9decin absent" : obtenu.getStatut()
                    + " (variation mixte " + obtenu.getVariationMixteSur100() + " %)";
            if (obtenu == null || !attendu.getValue().equals(obtenu.getStatut())) {
                ecartsStatut.add(attendu.getKey() + " : attendu " + attendu.getValue() + ", obtenu " + actual);
            }
        }

        Medecin boukili = parNom.get("BOUKILI");
        List<Action> actions = actionRepository.findAll().stream()
                .filter(action -> action.getMedecin() != null)
                .filter(action -> !medecinRepository.isPseudoMedecin(action.getMedecin()))
                .filter(action -> "2026-05".equals(action.getCycleMensuel())).toList();
        Map<String, Long> libellesActions = actions.stream().collect(Collectors.groupingBy(
                Action::getActionRecommandee, LinkedHashMap::new, Collectors.counting()));
        List<String> actionsElFassi = parNom.get("EL FASSI") == null ? List.of() : actions.stream()
                .filter(action -> action.getMedecin().getId().equals(parNom.get("EL FASSI").getId()))
                .map(Action::getActionRecommandee).toList();

        List<String> ecartsLibelle = new ArrayList<>();
        for (Map.Entry<String, String> attendu : ATTENDUS_STATUTS.entrySet()) {
            String statutAttendu = attendu.getValue();
            if (!LIBELLE_PAR_STATUT.containsKey(statutAttendu)) continue;
            Medecin m = parNom.get(attendu.getKey());
            if (m == null) continue;
            String libelleAttendu = LIBELLE_PAR_STATUT.get(statutAttendu);
            long nbActions = actions.stream()
                    .filter(a -> a.getMedecin().getId().equals(m.getId()))
                    .filter(a -> libelleAttendu.equals(a.getActionRecommandee()))
                    .count();
            if (nbActions != 1) {
                long total = actions.stream().filter(a -> a.getMedecin().getId().equals(m.getId())).count();
                String libelles = actions.stream().filter(a -> a.getMedecin().getId().equals(m.getId()))
                        .map(Action::getActionRecommandee).collect(Collectors.joining(", "));
                ecartsLibelle.add(attendu.getKey() + " (" + statutAttendu + ") : attendu 1x'"
                        + libelleAttendu + "', trouv\u00e9 " + total + " action(s): [" + libelles + "]");
            }
        }

        List<String> ecartsSegment = new ArrayList<>();
        for (Map.Entry<String, String> attendu : ATTENDUS_SEGMENTS.entrySet()) {
            Medecin m = parNom.get(attendu.getKey());
            if (m == null) { ecartsSegment.add(attendu.getKey() + " : m\u00e9decin absent"); continue; }
            if (!attendu.getValue().equals(m.getSegment())) {
                ecartsSegment.add(attendu.getKey() + " : attendu " + attendu.getValue()
                        + ", obtenu " + m.getSegment()
                        + " (score=" + m.getScoreValeur()
                        + " potentiel=" + m.getPotentielSur100()
                        + " perf=" + m.getPerformanceSur100()
                        + " poidsEco=" + m.getPoidsEcoSur100() + ")");
            }
        }

        String resultatsStatut = parNom.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + (entry.getValue() == null ? "absent"
                        : entry.getValue().getStatut() + "/" + entry.getValue().getVariationMixteSur100()))
                .collect(Collectors.joining(", "));

        System.out.println("=== TABLEAU 20 MEDECINS 2026-05 ===");
        for (Map.Entry<String, String> entry : ATTENDUS_STATUTS.entrySet()) {
            Medecin m = parNom.get(entry.getKey());
            Action a = actions.stream().filter(act -> act.getMedecin().getId().equals(m.getId())).findFirst().orElse(null);
            String libelle = a != null ? a.getActionRecommandee() : "AUCUNE";
            String urgence = a != null && a.getUrgence() != null ? a.getUrgence().name() : "AUCUNE";
            double ratioSilence = m.getIntervalleEffectif() != null && m.getIntervalleEffectif() > 0 && m.getJoursSansActivite() != null
                    ? (double) m.getJoursSansActivite() / m.getIntervalleEffectif() : 0.0;
            System.out.printf("| %-15s | %-14s | %-32s | %-8s | %-6.2f |%n",
                    entry.getKey(), m.getStatut(), libelle, urgence, ratioSilence);
        }

        System.out.println("=== DÉTAIL SILENCE AU 2026-05-31 ===");
        System.out.println("| Médecin | Dernière activité | Jours sans activité | Dates distinctes | Source rythme | Fenêtre observée | Intervalle attendu | Ratio | Classe | Échéance |");
        for (Map.Entry<String, String> entry : ATTENDUS_STATUTS.entrySet()) {
            Medecin m = parNom.get(entry.getKey());
            List<LocalDate> dates = extractionDonneesRepository.findDatesReceptionByMedecinId(m.getId()).stream()
                    .filter(date -> !date.isAfter(DATE_REFERENCE))
                    .toList();
            LocalDate derniereActivite = dates.isEmpty() ? null : dates.get(0);
            long joursSansActivite = derniereActivite == null ? 0
                    : ChronoUnit.DAYS.between(derniereActivite, DATE_REFERENCE);
            long moisActifs = dates.stream().map(YearMonth::from).distinct().count();
            String sourceRythme = dates.size() >= 3 || moisActifs >= 2 ? "récent"
                    : dates.size() >= 2 ? "historique" : "30 jours";
            String fenetre = dates.size() >= 2
                    ? Math.min(3, dates.size() - 1) + " écarts récents (code)"
                    : "repli 30 jours";
            int intervalle = dates.size() >= 2 && m.getIntervalleEffectif() != null
                    ? (int) Math.round(m.getIntervalleEffectif()) : 30;
            double ratio = (double) joursSansActivite / intervalle;
            String classe = ratio < 1.5 ? "aucune" : ratio < 2.0 ? "à surveiller" : "critique";
            Action a = actions.stream().filter(action -> action.getMedecin().getId().equals(m.getId()))
                    .findFirst().orElse(null);
            System.out.printf(Locale.FRANCE,
                    "| %-15s | %-10s | %19d | %15d | %-12s | %-22s | %18d | %5.2f | %-12s | %s |%n",
                    entry.getKey(), derniereActivite == null ? "—" : derniereActivite,
                    joursSansActivite, dates.size(), sourceRythme, fenetre, intervalle, ratio, classe,
                    a == null ? "AUCUNE" : a.getHorizonJours() + " jours");
        }

        System.out.println("=== TABLEAU SCORES ET SEGMENTS BLOC C ===");
        for (Map.Entry<String, String> entry : ATTENDUS_STATUTS.entrySet()) {
            Medecin m = parNom.get(entry.getKey());
            System.out.printf("| %-15s | %-10.2f | %-4d | %-10.2f | %-6.0f | %-6.2f | %-6.2f | %-6.2f | %-6.2f | %-3s |%n",
                    entry.getKey(),
                    m.getCaMensuelMoyen() != null ? m.getCaMensuelMoyen() : 0.0,
                    m.getRangPerformance() != null ? m.getRangPerformance() : 0,
                    m.getCaNormaliseSur100() != null ? m.getCaNormaliseSur100() : 0.0,
                    m.getVolumeNormaliseSur100() != null ? m.getVolumeNormaliseSur100() : 0.0,
                    m.getPotentielSur100() != null ? m.getPotentielSur100() : 0.0,
                    m.getPerformanceSur100() != null ? m.getPerformanceSur100() : 0.0,
                    m.getPoidsEcoSur100() != null ? m.getPoidsEcoSur100() : 0.0,
                    m.getScoreValeur() != null ? m.getScoreValeur() : 0.0,
                    m.getSegment() != null ? m.getSegment() : "D");
        }

        List<Action> actionsApresSync = actionRepository.findAll().stream()
                .filter(action -> action.getMedecin() != null)
                .filter(action -> !medecinRepository.isPseudoMedecin(action.getMedecin()))
                .filter(action -> "2026-05".equals(action.getCycleMensuel())).toList();
        List<Double> scores19 = actionsApresSync.stream()
                .map(Action::getMedecin)
                .map(Medecin::getScoreUrgence)
                .filter(java.util.Objects::nonNull)
                .toList();
        double med = ActionHorizonPolicy.percentile(scores19, 0.50);
        double p80 = ActionHorizonPolicy.percentile(scores19, 0.80);
        System.out.println("=== TABLEAU HORIZONS 19 ACTIONS 2026-05 (Population=" + scores19.size() + ", Mediane=" + String.format(Locale.US, "%.2f", med) + ", P80=" + String.format(Locale.US, "%.2f", p80) + ") ===");
        for (Map.Entry<String, String> entry : ATTENDUS_STATUTS.entrySet()) {
            Medecin m = parNom.get(entry.getKey());
            Action a = actionsApresSync.stream().filter(act -> act.getMedecin().getId().equals(m.getId())).findFirst().orElse(null);
            if (a == null) continue;
            System.out.printf("| %-15s | %-14s | %-10.2f | %-8s | %-2d jours |%n",
                    entry.getKey(), m.getStatut(), m.getScoreUrgence() != null ? m.getScoreUrgence() : 0.0, a.getUrgence(), a.getHorizonJours());
        }

        assertEquals(25, medecins.size(), "Nombre de médecins non-pseudo importés depuis le classeur actualisé");
        assertTrue(ecartsStatut.isEmpty(), String.join("\n", ecartsStatut));
        assertTrue(boukili != null && Math.abs(boukili.getVariationMixteSur100() - (-10.01)) < 0.005,
                "BOUKILI variation mixte attendue -10.01 %, obtenue "
                        + (boukili == null ? "m\u00e9decin absent" : boukili.getVariationMixteSur100()));
        assertAll(
                () -> assertEquals(19, actions.size(), "Actions obtenues=" + actions.size() + ", libell\u00e9s="
                        + libellesActions + ", statuts/variations=" + resultatsStatut),
                () -> assertTrue(actionsElFassi.isEmpty(),
                        "Actions g\u00e9n\u00e9r\u00e9es pour EL FASSI (Inactif, 0 attendue)=" + actionsElFassi),
                () -> assertTrue(ecartsLibelle.isEmpty(),
                        "Ecarts libell\u00e9 action (Section 1 document):\n" + String.join("\n", ecartsLibelle)),
                () -> assertTrue(ecartsSegment.isEmpty(),
                        "Ecarts segment (Bloc C document):\n" + String.join("\n", ecartsSegment))
        );
    }

        @Test
        void classeRahmaniEnSegmentBSelonSonScoreAu30Septembre2026() throws Exception {
                try (InputStream workbook = getClass().getResourceAsStream("/data/data_fictif_test_vactis.xlsx")) {
                        assertTrue(workbook != null, "Le classeur actualisé doit être présent dans src/main/resources/data.");
                        excelImportService.importFromExcelStream(workbook);
                }

                medecinService.recalculerStatutsEtSegmentsDynamiques(DATE_REFERENCE_SEPTEMBRE);
                List<Medecin> medecins = medecinRepository.findAll().stream()
                                .filter(medecin -> !medecinRepository.isPseudoMedecin(medecin)).toList();
                Medecin rahmani = findBySurname(medecins, "RAHMANI");
                assertNotNull(rahmani, "Rahmani doit être présent dans le portefeuille.");

                assertAll("Segmentation de Rahmani au 30 septembre 2026 (CA total aligné encadrant)",
                                () -> assertEquals(25, medecins.size(), "La population métier exclut les deux profils sans médecin identifié."),
                                () -> assertEquals(6231.0, rahmani.getCaMensuelMoyen(), 100.0,
                                                "CA moyen calculé par le service sur l'historique hors septembre 2026."),

                                () -> assertEquals(60.0, rahmani.getPotentielSur100(), 0.01),
                                () -> assertEquals(87.50, rahmani.getPerformanceSur100(), 0.01,
                                                "Performance CA 87.5% = Rang 22 / 25 ((22 - 1) / (25 - 1) * 100)."),
                                () -> assertEquals(54.87, rahmani.getPoidsEcoSur100(), 0.01),
                                () -> assertEquals(69.97, rahmani.getScoreValeur(), 0.05),
                                () -> assertEquals("B", rahmani.getSegment(),
                                                "Score 69,97 >= 60 => Segment B avec le classement CA total aligné encadrant.")
                );
        }

    private Medecin findBySurname(List<Medecin> medecins, String surname) {
        String normalizedSurname = normalize(surname);
        return medecins.stream().filter(medecin -> normalize(
                        (medecin.getNom() == null ? "" : medecin.getNom()) + " "
                                + (medecin.getPrenom() == null ? "" : medecin.getPrenom()))
                .contains(normalizedSurname)).findFirst().orElse(null);
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}