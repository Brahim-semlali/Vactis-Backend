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
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.RetourTerrainRepository;
import com.vactis.model.Controle.TypeControle;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ResourceLoader resourceLoader;

    @PersistenceContext
    private final EntityManager entityManager;

    @Transactional
    public void importFictifExcelAndSyncMedecins() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/data_fictif_test_vactis.xlsx");
            if (!resource.exists()) {
                log.warn("Fichier Excel introuvable dans classpath:data/data_fictif_test_vactis.xlsx");
                return;
            }
            try (InputStream inputStream = resource.getInputStream()) {
                importFromExcelStream(inputStream);
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'import et de la synchronisation depuis l'Excel fictif: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void importFromExcelStream(InputStream inputStream) throws Exception {
        // Supprimer les contraintes de check obsolètes si elles existent en base PostgreSQL
        try {
            entityManager.createNativeQuery("ALTER TABLE medecins DROP CONSTRAINT IF EXISTS medecins_statut_check").executeUpdate();
            entityManager.createNativeQuery("ALTER TABLE medecins DROP CONSTRAINT IF EXISTS medecins_segment_check").executeUpdate();
            entityManager.createNativeQuery("ALTER TABLE actions DROP CONSTRAINT IF EXISTS actions_statut_check").executeUpdate();
            entityManager.createNativeQuery("ALTER TABLE actions DROP CONSTRAINT IF EXISTS actions_segment_check").executeUpdate();
        } catch (Exception ex) {
            log.debug("Ignoré lors du nettoyage des contraintes: {}", ex.getMessage());
        }

        // Purger uniquement les données d'extraction et les actions générées lors de l'import
        log.info("Purge et ré-importation des actions et dossiers data_fictif (retours_terrain conservés)...");
        actionRepository.deleteAllInBatch();
        extractionDonneesRepository.deleteAllInBatch();
        extractionDonneesRepository.flush();

        // Récupérer les médecins existants indexés par codeMedecin pour éviter de casser les clés étrangères de retours_terrain
        Map<String, Medecin> existingMedecinsMap = new HashMap<>();
        for (Medecin m : medecinRepository.findAll()) {
            if (m.getCodeMedecin() != null) {
                existingMedecinsMap.put(m.getCodeMedecin().toUpperCase(), m);
            }
        }

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
            if (docRaw.isEmpty()) continue;

            LocalDate dateRec = getCellValueAsDate(cellDate);
            String spec = getCellValueAsString(cellSpec).trim();
            String org = getCellValueAsString(cellOrg).trim();
            String lieu = getCellValueAsString(cellLieu).trim();
            int prixTot = getCellValueAsInt(cellPrixTot);
            int prixPay = getCellValueAsInt(cellPrixPay);
            boolean urgent = "oui".equalsIgnoreCase(getCellValueAsString(cellUrgent).trim()) || "true".equalsIgnoreCase(getCellValueAsString(cellUrgent).trim());

            RawRowData rowData = new RawRowData(rowIdx, dateRec, docRaw, spec, org, lieu, prixTot, prixPay, urgent);
            rawRows.add(rowData);

            doctorGroupMap.computeIfAbsent(docRaw, k -> new ArrayList<>()).add(rowData);
        }

        log.info("Lecture Excel terminée: {} dossiers trouvés pour {} médecins distincts.", rawRows.size(), doctorGroupMap.size());

        // 1. Extraire et synchroniser (upsert) les médecins issus de data_fictif
        Map<String, Medecin> doctorEntityMap = new HashMap<>();
        List<Medecin> savedMedecins = new ArrayList<>();
        int codeSeq = 1;

        for (Map.Entry<String, List<RawRowData>> entry : doctorGroupMap.entrySet()) {
            String rawName = entry.getKey();
            List<RawRowData> docRows = entry.getValue();

            String cleanedName = cleanDoctorName(rawName);
            String[] parsedName = parseNomPrenom(cleanedName);

            int totalCa = docRows.stream().mapToInt(r -> r.prixPay).sum();
            LocalDate minDate = docRows.stream().map(r -> r.dateRec).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(LocalDate.now());
            LocalDate maxDate = docRows.stream().map(r -> r.dateRec).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(LocalDate.now());
            String mainSpec = docRows.stream().map(r -> r.specialite).filter(s -> !s.isEmpty()).findFirst().orElse("Autre");
            String mainOrg = docRows.stream().map(r -> r.organisme).filter(o -> !o.isEmpty()).findFirst().orElse("Cabinet / Hôpital");
            String mainVille = docRows.stream().map(r -> r.lieu).filter(l -> !l.isEmpty())
                    .collect(java.util.stream.Collectors.groupingBy(l -> l, java.util.stream.Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);

            String expectedCode = String.format("MED%03d", codeSeq++);
            Medecin medecin = existingMedecinsMap.get(expectedCode.toUpperCase());
            if (medecin == null) {
                medecin = new Medecin();
                medecin.setCodeMedecin(expectedCode);
            }

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

            int caMoisActuel = docRows.stream()
                    .filter(r -> r.dateRec != null && YearMonth.from(r.dateRec).equals(maxYm))
                    .mapToInt(r -> r.prixPay)
                    .sum();

            int caBaselineRef = docRows.stream()
                    .filter(r -> r.dateRec != null && YearMonth.from(r.dateRec).equals(maxYm.minusMonths(1)))
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
            dossier.setDateReception(r.dateRec != null ? r.dateRec : LocalDate.now());
            dossier.setDatePrelevement(r.dateRec != null ? r.dateRec : LocalDate.now());
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
        segmentationService.recalculerSegmentationPortefeuille();

        // 4. Générer des actions associées uniquement aux nouveaux médecins synchronisés
        generateActionsForMedecins(savedMedecins);

        log.info("Base synchronisée avec succès depuis data_fictif: {} médecins et {} dossiers.", savedMedecins.size(), dossiers.size());
    }

    private void generateActionsForMedecins(List<Medecin> medecins) {
        List<Action> actions = new ArrayList<>();
        String[] commerciaux = new String[]{"Karim Bennani", "Salma Idrissi"};
        // Cycle mensuel courant calculé dynamiquement (format yyyy-MM)
        String cycleCourant = java.time.YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        int idx = 0;

        for (Medecin m : medecins) {
            String commercial = commerciaux[idx++ % commerciaux.length];
            String statutMed = (m.getStatut() != null ? m.getStatut() : (m.getStatutPilotage() != null ? m.getStatutPilotage().name() : "NOUVEAU")).toUpperCase();

            if ("SURVEILLANCE".equals(statutMed) || m.getStatutPilotage() == StatutPilotage.SURVEILLANCE) {
                Action a = new Action();
                a.setMedecin(m);
                a.setStatut(statutMed);
                a.setSegment(m.getSegment());
                a.setActionRecommandee("visite urgence silence");
                a.setUrgence(UrgenceAction.SILENCE_CRITIQUE);
                a.setEtatAction(EtatAction.PLANIFIEE);
                a.setDateVisite(LocalDate.now().plusDays(5));
                a.setCommercial(commercial);
                a.setLieuOrganisme(m.getOrganisme());
                a.setBacklog(false);
                a.setUrgenceSilence(true);
                a.setCycleMensuel(cycleCourant);
                a.setCommentaire("Relance prioritaire sur données fictives.");
                actions.add(a);
            } else if ("PROGRESSION".equals(statutMed) || m.getStatutPilotage() == StatutPilotage.PROGRESSION) {
                Action a = new Action();
                a.setMedecin(m);
                a.setStatut(statutMed);
                a.setSegment(m.getSegment());
                a.setActionRecommandee("visite suivi progression");
                a.setUrgence(UrgenceAction.FAIBLE);
                a.setEtatAction(EtatAction.REALISEE);
                a.setDateVisite(LocalDate.now().minusDays(3));
                a.setCommercial(commercial);
                a.setLieuOrganisme(m.getOrganisme());
                a.setBacklog(false);
                a.setUrgenceSilence(false);
                a.setCycleMensuel(cycleCourant);
                a.setCommentaire("Visite de suivi progression effectuée.");
                actions.add(a);
            } else if ("ONBOARDING".equals(statutMed) || m.getStatutPilotage() == StatutPilotage.ONBOARDING) {
                Action a = new Action();
                a.setMedecin(m);
                a.setStatut(statutMed);
                a.setSegment(m.getSegment());
                a.setActionRecommandee("visite onboarding");
                a.setUrgence(UrgenceAction.ELEVE);
                a.setEtatAction(EtatAction.PLANIFIEE);
                a.setDateVisite(LocalDate.now().plusDays(7));
                a.setCommercial(commercial);
                a.setLieuOrganisme(m.getOrganisme());
                a.setBacklog(false);
                a.setUrgenceSilence(false);
                a.setCycleMensuel(cycleCourant);
                a.setCommentaire("Première visite d onboarding.");
                actions.add(a);
            } else if ("SILENCE_CRITIQUE".equals(statutMed) || "RETENTION".equals(statutMed) || m.getStatutPilotage() == StatutPilotage.SILENCE_CRITIQUE) {
                Action a = new Action();
                a.setMedecin(m);
                a.setStatut(statutMed);
                a.setSegment(m.getSegment());
                a.setActionRecommandee("visite urgence silence");
                a.setUrgence(UrgenceAction.SILENCE_CRITIQUE);
                a.setEtatAction(EtatAction.PLANIFIEE);
                a.setDateVisite(LocalDate.now().plusDays(2));
                a.setCommercial(commercial);
                a.setLieuOrganisme(m.getOrganisme());
                a.setBacklog(false);
                a.setUrgenceSilence(true);
                a.setCycleMensuel(cycleCourant);
                a.setCommentaire("Silence prolongé à traiter en priorité.");
                actions.add(a);
            } else {
                Action a = new Action();
                a.setMedecin(m);
                a.setStatut(statutMed);
                a.setSegment(m.getSegment());
                a.setActionRecommandee("visite suivi régulier");
                a.setUrgence(UrgenceAction.FAIBLE);
                a.setEtatAction(EtatAction.PLANIFIEE);
                a.setDateVisite(LocalDate.now().plusDays(5));
                a.setCommercial(commercial);
                a.setLieuOrganisme(m.getOrganisme());
                a.setBacklog(false);
                a.setUrgenceSilence(false);
                a.setCycleMensuel(cycleCourant);
                a.setCommentaire("Visite de suivi commercial régulier et fidélisation.");
                actions.add(a);
            }
        }
        actionRepository.saveAll(actions);
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
        if (statut == null) return StatutPilotage.ACTIF;
        return switch (statut.toUpperCase()) {
            case "PROGRESSION" -> StatutPilotage.PROGRESSION;
            case "SURVEILLANCE" -> StatutPilotage.SURVEILLANCE;
            case "SILENCE_CRITIQUE", "RETENTION" -> StatutPilotage.SILENCE_CRITIQUE;
            case "ONBOARDING" -> StatutPilotage.ONBOARDING;
            default -> StatutPilotage.ACTIF;
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
        if (cell == null) return LocalDate.now();
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            Date date = cell.getDateCellValue();
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            Date date = DateUtil.getJavaDate(cell.getNumericCellValue());
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return LocalDate.now();
    }

    private record RawRowData(int rowIdx, LocalDate dateRec, String docName, String specialite,
                              String organisme, String lieu, int prixTot, int prixPay, boolean urgent) {}
}