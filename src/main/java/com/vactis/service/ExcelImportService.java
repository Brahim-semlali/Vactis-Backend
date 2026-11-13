package com.vactis.service;

import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.action.UrgenceAction;
import com.vactis.model.data.ExtractionDonnees;
import com.vactis.model.data.StatutDossier;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RisqueUrgence;
import com.vactis.model.medecin.StatutPilotage;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.AlerteHebdomadaireRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.ReclamationHistoriqueRepository;
import com.vactis.repository.ReclamationRepository;
import com.vactis.repository.RetourTerrainRepository;
import com.vactis.repository.notification.NotificationRepository;
import com.vactis.model.Controle.TypeControle;

import com.vactis.service.Activite.SegmentationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelImportService {
    private final ControleService controleService;
    private final SegmentationService segmentationService;
    private final ExtractionDonneesRepository extractionDonneesRepository;
    private final MedecinRepository medecinRepository;
    private final ActionRepository actionRepository;
    private final RetourTerrainRepository retourTerrainRepository;
    private final AlerteHebdomadaireRepository alerteHebdomadaireRepository;
    private final ReclamationHistoriqueRepository reclamationHistoriqueRepository;
    private final ReclamationRepository reclamationRepository;
    private final NotificationRepository notificationRepository;
    private final ResourceLoader resourceLoader;

    @Transactional
    public void importFictifExcelAndSyncMedecins() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/data_fictif_test_vactis.xlsx");
            if (!resource.exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Classeur de données fictives introuvable.");
            }
            try (InputStream inputStream = resource.getInputStream()) {
                importFromExcelStream(inputStream);
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'import et de la synchronisation depuis l'Excel fictif: {}", e.getMessage(), e);
            if (e instanceof ResponseStatusException responseStatusException) {
                throw responseStatusException;
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Échec de la synchronisation du classeur fictif.", e);
        }
    }

    @Transactional
    public void importFromExcelStream(InputStream inputStream) throws Exception {
        log.info("Remplacement complet du jeu fictif (médecins, dossiers, actions et données médicales liées)...");
        retourTerrainRepository.detachActions();
        notificationRepository.deleteByType("RECLAMATION");
        reclamationHistoriqueRepository.deleteAllInBatch();
        reclamationRepository.deleteAllInBatch();
        alerteHebdomadaireRepository.deleteAllInBatch();
        retourTerrainRepository.deleteAllInBatch();
        actionRepository.deleteAllInBatch();
        extractionDonneesRepository.deleteAllInBatch();
        medecinRepository.deleteAllInBatch();
        medecinRepository.flush();

        Workbook workbook = WorkbookFactory.create(inputStream);
        Sheet sheet = workbook.getSheetAt(0);

        List<RawRowData> rawRows = new ArrayList<>();
        Map<String, List<RawRowData>> doctorGroupMap = new LinkedHashMap<>();

        int rowIdx = 0;
        for (Row row : sheet) {
            rowIdx++;
            if (rowIdx == 1) continue; // Skip header

            Cell cellDate = row.getCell(0);
            Cell cellDoc = row.getCell(1);
            Cell cellSpec = row.getCell(2);
            Cell cellOrg = row.getCell(3);
            Cell cellLieu = row.getCell(4);
            Cell cellPrixTot = row.getCell(5);
            Cell cellPrixPay = row.getCell(6);
            Cell cellUrgent = row.getCell(7);

            String docRaw = getCellValueAsString(cellDoc).trim();

            LocalDate dateRec = getCellValueAsDate(cellDate);
            String spec = getCellValueAsString(cellSpec).trim();
            String org = getCellValueAsString(cellOrg).trim();
            String lieu = getCellValueAsString(cellLieu).trim();
            int prixTot = getCellValueAsInt(cellPrixTot);
            int prixPay = getCellValueAsInt(cellPrixPay);
            boolean urgent = "oui".equalsIgnoreCase(getCellValueAsString(cellUrgent).trim()) || "true".equalsIgnoreCase(getCellValueAsString(cellUrgent).trim());

            RawRowData rowData = new RawRowData(rowIdx, dateRec, docRaw, spec, org, lieu, prixTot, prixPay, urgent);
            rawRows.add(rowData);

            if (!docRaw.isEmpty()) {
                doctorGroupMap.computeIfAbsent(docRaw, k -> new ArrayList<>()).add(rowData);
            }
        }

        log.info("Lecture Excel terminée: {} dossiers trouvés pour {} médecins distincts.", rawRows.size(), doctorGroupMap.size());
        LocalDate dateReference = rawRows.stream()
            .map(RawRowData::dateRec)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .orElse(LocalDate.now());
        log.info("Date de référence de l'import: {}", dateReference);

        // 1. Recréer les médecins depuis le classeur de référence.
        Map<String, Medecin> doctorEntityMap = new HashMap<>();
        List<Medecin> savedMedecins = new ArrayList<>();
        int codeSeq = 1;

        for (Map.Entry<String, List<RawRowData>> entry : doctorGroupMap.entrySet()) {
            String rawName = entry.getKey();
            List<RawRowData> docRows = entry.getValue();

            String cleanedName = cleanDoctorName(rawName);
            String[] parsedName = parseNomPrenom(cleanedName);

            int totalCa = docRows.stream().mapToInt(r -> r.prixPay).sum();
            LocalDate minDate = docRows.stream().map(RawRowData::dateRec).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(dateReference);
            LocalDate maxDate = docRows.stream().map(RawRowData::dateRec).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(dateReference);
            String mainSpec = docRows.stream().map(r -> r.specialite).filter(s -> !s.isEmpty()).findFirst().orElse("Autre");
            String mainOrg = docRows.stream().map(r -> r.organisme).filter(o -> !o.isEmpty()).findFirst().orElse("Cabinet / Hôpital");
            String mainVille = docRows.stream().map(r -> r.lieu).filter(l -> !l.isEmpty())
                    .collect(java.util.stream.Collectors.groupingBy(l -> l, java.util.stream.Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);

            Medecin medecin = new Medecin();
            medecin.setCodeMedecin(String.format(Locale.ROOT, "MED%03d", codeSeq++));

            medecin.setPrenom(parsedName[0]);
            medecin.setNom(parsedName[1]);
            medecin.setSpecialite(mainSpec);
            medecin.setOrganisme(mainOrg);
            YearMonth maxYm = docRows.stream()
                    .map(r -> r.dateRec)
                    .filter(Objects::nonNull)
                    .map(YearMonth::from)
                    .max(YearMonth::compareTo)
                    .orElse(YearMonth.now());

            YearMonth moisCourant = YearMonth.from(dateReference);

            int caMoisActuel = docRows.stream()
                    .filter(r -> r.dateRec != null && YearMonth.from(r.dateRec).equals(moisCourant))
                    .mapToInt(r -> r.prixPay)
                    .sum();

            int caBaselineRef = docRows.stream()
                    .filter(r -> r.dateRec != null && YearMonth.from(r.dateRec).equals(moisCourant.minusMonths(1)))
                    .mapToInt(r -> r.prixPay)
                    .sum();

            String statutResult = controleService.determinerEtat(TypeControle.STATUT, (long) caMoisActuel);
            medecin.setStatut(statutResult != null ? statutResult : "NOUVEAU");
            medecin.setStatutPilotage(determineStatutPilotage(statutResult, caMoisActuel));
            medecin.setRisqueUrgence(caMoisActuel > 50000 ? RisqueUrgence.FAIBLE : (caMoisActuel > 15000 ? RisqueUrgence.MOYEN : RisqueUrgence.ELEVE));
            medecin.setCaMois(caMoisActuel);
            medecin.setCaBaseline(caBaselineRef);
            medecin.setCaTotal(totalCa);
            medecin.setTotalCas(docRows.size());
            medecin.setDatePremiereCollaboration(minDate);
            medecin.setDateDerniereActivite(maxDate);
            String segmentResult = controleService.determinerEtat(TypeControle.SEGEMENTS, (long) totalCa);
            medecin.setSegment(segmentResult != null ? segmentResult : "STANDARD");
            medecin.setCommentaire("Médecin synchronisé depuis les données terrain fictives (" + docRows.size() + " dossiers)");

            medecin = medecinRepository.save(medecin);
            doctorEntityMap.put(rawName, medecin);
            savedMedecins.add(medecin);
        }

        // 2. Insérer les dossiers dans data_fictif (ExtractionDonnees)
        List<ExtractionDonnees> dossiers = new ArrayList<>();
        for (RawRowData r : rawRows) {
            Medecin m = doctorEntityMap.get(r.docName);

            ExtractionDonnees dossier = new ExtractionDonnees();
            dossier.setMedecin(m);
            dossier.setReferenceDossier(String.format("DOS-%05d", r.rowIdx));
            dossier.setDateReception(r.dateRec != null ? r.dateRec : dateReference);
            dossier.setDatePrelevement(r.dateRec != null ? r.dateRec : dateReference);
            dossier.setTypeAnalyse(r.specialite != null && !r.specialite.isEmpty() ? r.specialite : "Analyse biologique");
            dossier.setNombreAnalyses(1);
            dossier.setStatutDossier(StatutDossier.TERMINE);
            dossier.setLieuPrelevement(r.lieu != null && !r.lieu.isEmpty() ? r.lieu : "Laboratoire VACTIS");
            dossier.setPrixTotal(r.prixTot);
            dossier.setPrixAPayer(r.prixPay);
            dossier.setMontantRembourse(Math.max(0, r.prixTot - r.prixPay));
            dossier.setUrgent(r.urgent);

            dossiers.add(dossier);
        }
        extractionDonneesRepository.saveAll(dossiers);

        // 3. Recalculer le Score de valeur et la segmentation A/B/C/D
        segmentationService.recalculerSegmentationPortefeuille(dateReference);

        // 4. Générer des actions associées uniquement aux nouveaux médecins synchronisés
        generateActionsForMedecins(savedMedecins, dateReference);

        log.info("Base synchronisée avec succès depuis data_fictif au {}: {} médecins et {} dossiers.",
            dateReference, savedMedecins.size(), dossiers.size());
    }

    void generateActionsForMedecins(List<Medecin> medecins, LocalDate dateReference) {
        List<Action> actions = new ArrayList<>();
        String[] commerciaux = new String[]{"Karim Bennani", "Salma Idrissi"};
        String cycleCourant = YearMonth.from(dateReference).toString();
        int idx = 0;

        for (Medecin m : medecins) {
            if (medecinRepository.isPseudoMedecin(m)) continue;
            String commercial = commerciaux[idx++ % commerciaux.length];
            String statutMed = (m.getStatut() != null ? m.getStatut()
                    : (m.getStatutPilotage() != null ? m.getStatutPilotage().name() : "NOUVEAU")).toUpperCase();

            Action a = buildActionPourStatut(m, statutMed, commercial, cycleCourant, dateReference);
            if (a != null) {
                actions.add(a);
            }
            // Section 1 du document :
            // INACTIF -> 0 action automatique (reactivation sur decision uniquement)
            // ACTIVITE_IRREGULIERE -> 0 action automatique (liste Recommandations uniquement)
        }
        actionRepository.saveAll(actions);
    }

    @Transactional
    public void regenerateImportedDataActions(List<Medecin> medecins, LocalDate dateReference) {
        actionRepository.deleteUntouchedImportGeneratedActions();
        generateActionsForMedecins(medecins, dateReference);
        actionRepository.flush();
    }

    /**
     * Retourne l'action VACTIS conforme a la Section 1 pour un statut donne,
     * ou null si aucune action automatique n'est prevue (Inactif, Activite irreguliere).
     * Libelles fonctionnels du document :
     *   Progression -> Reconnaissance       -> "visite suivi progression"
     *   Actif/Actif stable -> Fidelisation  -> "visite suivi regulier"
     *   Surveillance -> Diagnostic/surveil. -> "visite diagnostic surveillance"
     *   Retention -> Diagnostic (Action 1)  -> "visite diagnostic"
     *   Onboarding -> Qualification         -> "visite onboarding"
     *   Inactif / Activite irreguliere      -> null (0 action)
     */
    private Action buildActionPourStatut(Medecin m, String statutMed, String commercial,
                                         String cycleCourant, LocalDate dateReference) {
        switch (statutMed) {
            case "PROGRESSION": {
                // Section 1 : Progression -> Reconnaissance (1 action)
                Action a = baseAction(m, statutMed, commercial, cycleCourant);
                a.setActionRecommandee("visite suivi progression");
                a.setUrgence(UrgenceAction.FAIBLE);
                a.setDateVisite(dateReference.plusDays(7));
                a.setCommentaire("Visite de reconnaissance et suivi de la progression.");
                return a;
            }
            case "ACTIF_STABLE": case "ACTIF": {
                // Section 1 : Actif -> Fidelisation (1 action)
                Action a = baseAction(m, statutMed, commercial, cycleCourant);
                a.setActionRecommandee("visite suivi r\u00e9gulier");
                a.setUrgence(UrgenceAction.FAIBLE);
                a.setDateVisite(dateReference.plusDays(5));
                a.setCommentaire("Visite de fid\u00e9lisation et suivi commercial r\u00e9gulier.");
                return a;
            }
            case "SURVEILLANCE": {
                // Section 1 : Surveillance -> Diagnostic / surveillance (1 action)
                Action a = baseAction(m, statutMed, commercial, cycleCourant);
                a.setActionRecommandee("visite diagnostic surveillance");
                a.setUrgence(UrgenceAction.ELEVE);
                a.setDateVisite(dateReference.plusDays(5));
                a.setCommentaire("Diagnostic et surveillance de l'activit\u00e9.");
                return a;
            }
            case "RETENTION": case "SILENCE_CRITIQUE": {
                // Section 1 : Retention -> Diagnostic (Action 1, 2 maximum)
                Action a = baseAction(m, statutMed, commercial, cycleCourant);
                a.setActionRecommandee("visite diagnostic");
                a.setUrgence(UrgenceAction.ELEVE);
                a.setDateVisite(dateReference.plusDays(2));
                a.setCommentaire("Visite de diagnostic pour identifier la cause de la baisse.");
                return a;
            }
            case "ONBOARDING": {
                // Section 1 : Onboarding -> Qualification (1 action)
                Action a = baseAction(m, statutMed, commercial, cycleCourant);
                a.setActionRecommandee("visite onboarding");
                a.setUrgence(UrgenceAction.ELEVE);
                a.setDateVisite(dateReference.plusDays(7));
                a.setCommentaire("Premi\u00e8re visite d'onboarding.");
                return a;
            }
            // INACTIF, ACTIVITE_IRREGULIERE, A_REACTIVER, et tout autre statut : 0 action automatique
            default:
                return null;
        }
    }

    private Action baseAction(Medecin m, String statutMed, String commercial, String cycleCourant) {
        Action a = new Action();
        a.setMedecin(m);
        a.setStatut(statutMed);
        a.setSegment(m.getSegment());
        a.setEtatAction(EtatAction.PLANIFIEE);
        a.setCommercial(commercial);
        a.setLieuOrganisme(m.getOrganisme());
        a.setBacklog(false);
        a.setUrgenceSilence(false);
        a.setCycleMensuel(cycleCourant);
        a.setSourceRecommandation("IMPORT_AUTO");
        return a;
    }

    private String cleanDoctorName(String raw) {
        if (raw == null) return "Inconnu";
        String s = raw.trim();
        if (s.toLowerCase().startsWith("dr ")) {
            s = s.substring(3).trim();
        } else if (s.toLowerCase().startsWith("dr. ")) {
            s = s.substring(4).trim();
        }
        return s;
    }

    private String[] parseNomPrenom(String cleanedName) {
        String[] parts = cleanedName.split("\\s+");
        if (parts.length == 0) return new String[]{"", "Inconnu"};
        if (parts.length == 1) return new String[]{"", parts[0]};

        String prenom = parts[0];
        String nom = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
        return new String[]{prenom, nom};
    }

    private StatutPilotage determineStatutPilotage(String statut, int totalCa) {
        if (statut == null) return StatutPilotage.ACTIF_STABLE;
        return switch (statut.toUpperCase()) {
            case "PROGRESSION" -> StatutPilotage.PROGRESSION;
            case "SURVEILLANCE" -> StatutPilotage.SURVEILLANCE;
            case "SILENCE_CRITIQUE", "RETENTION" -> StatutPilotage.RETENTION;
            case "ONBOARDING" -> StatutPilotage.ONBOARDING;
            case "A_REACTIVER" -> StatutPilotage.A_REACTIVER;
            case "INACTIF", "EXCLU" -> StatutPilotage.INACTIF;
            default -> StatutPilotage.ACTIF_STABLE;
        };
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private int getCellValueAsInt(Cell cell) {
        if (cell == null) return 0;
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) cell.getNumericCellValue();
        } else if (cell.getCellType() == CellType.STRING) {
            try {
                return Integer.parseInt(cell.getStringCellValue().trim());
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    private LocalDate getCellValueAsDate(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            Date date = cell.getDateCellValue();
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            Date date = DateUtil.getJavaDate(cell.getNumericCellValue());
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }

    private record RawRowData(int rowIdx, LocalDate dateRec, String docName, String specialite,
                              String organisme, String lieu, int prixTot, int prixPay, boolean urgent) {}
}