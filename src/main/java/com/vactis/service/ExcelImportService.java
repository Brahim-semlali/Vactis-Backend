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
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelImportService {
    private final ControleService controleService;
    private final ExtractionDonneesRepository extractionDonneesRepository;
    private final MedecinRepository medecinRepository;
    private final ActionRepository actionRepository;
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

        // Purger les anciennes données insérées précédemment depuis data.sql ou anciens tests
        log.info("Purge des anciennes données (actions, data_fictif, medecins)...");
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

        // 1. Extraire et insérer uniquement les médecins issus de data_fictif
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

            Medecin medecin = new Medecin();
            medecin.setCodeMedecin(String.format("MED%03d", codeSeq++));
            medecin.setPrenom(parsedName[0]);
            medecin.setNom(parsedName[1]);
            medecin.setSpecialite(mainSpec);
            medecin.setOrganisme(mainOrg);
            medecin.setVille("Marrakech");
            medecin.setStatut(controleService.determinerEtat(TypeControle.STATUT, (long) totalCa));
            medecin.setStatutPilotage(determineStatutPilotage(totalCa, docRows.size(), codeSeq));
            medecin.setRisqueUrgence(totalCa > 50000 ? RisqueUrgence.FAIBLE : (totalCa > 15000 ? RisqueUrgence.MOYEN : RisqueUrgence.ELEVE));
            medecin.setCaMois(totalCa);
            medecin.setDatePremiereCollaboration(minDate);
            medecin.setDateDerniereActivite(maxDate);
            medecin.setSegment(controleService.determinerEtat(TypeControle.SEGEMENTS, (long) totalCa));
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

        // 3. Générer des actions associées uniquement aux nouveaux médecins synchronisés
        generateActionsForMedecins(savedMedecins);

        log.info("Base synchronisée avec succès depuis data_fictif: {} médecins et {} dossiers.", savedMedecins.size(), dossiers.size());
    }

    private void generateActionsForMedecins(List<Medecin> medecins) {
        List<Action> actions = new ArrayList<>();
        String[] commerciaux = new String[]{"Karim Bennani", "Salma Idrissi"};
        int idx = 0;

        for (Medecin m : medecins) {
            String commercial = commerciaux[idx++ % commerciaux.length];
            if (m.getStatutPilotage() == StatutPilotage.SURVEILLANCE) {
                Action a = new Action();
                a.setMedecin(m);
                a.setStatut(StatutPilotage.SURVEILLANCE.name());
                a.setSegment(m.getSegment());
                a.setActionRecommandee("visite urgence silence");
                a.setUrgence(UrgenceAction.SILENCE_CRITIQUE);
                a.setEtatAction(EtatAction.PLANIFIEE);
                a.setDateVisite(LocalDate.now().plusDays(5));
                a.setCommercial(commercial);
                a.setLieuOrganisme(m.getOrganisme());
                a.setBacklog(false);
                a.setUrgenceSilence(true);
                a.setCycleMensuel("2026-07");
                a.setCommentaire("Relance prioritaire sur données fictives.");
                actions.add(a);
            } else if (m.getStatutPilotage() == StatutPilotage.PROGRESSION) {
                Action a = new Action();
                a.setMedecin(m);
                a.setStatut(StatutPilotage.PROGRESSION.name());
                a.setSegment(m.getSegment());
                a.setActionRecommandee("visite suivi progression");
                a.setUrgence(UrgenceAction.FAIBLE);
                a.setEtatAction(EtatAction.REALISEE);
                a.setDateVisite(LocalDate.now().minusDays(3));
                a.setCommercial(commercial);
                a.setLieuOrganisme(m.getOrganisme());
                a.setBacklog(false);
                a.setUrgenceSilence(false);
                a.setCycleMensuel("2026-07");
                a.setCommentaire("Visite de suivi progression effectuée.");
                actions.add(a);
            } else if (m.getStatutPilotage() == StatutPilotage.ONBOARDING) {
                Action a = new Action();
                a.setMedecin(m);
                a.setStatut(StatutPilotage.ONBOARDING.name());
                a.setSegment(m.getSegment());
                a.setActionRecommandee("visite onboarding");
                a.setUrgence(UrgenceAction.ELEVE);
                a.setEtatAction(EtatAction.PLANIFIEE);
                a.setDateVisite(LocalDate.now().plusDays(7));
                a.setCommercial(commercial);
                a.setLieuOrganisme(m.getOrganisme());
                a.setBacklog(false);
                a.setUrgenceSilence(false);
                a.setCycleMensuel("2026-07");
                a.setCommentaire("Première visite d onboarding.");
                actions.add(a);
            } else if (m.getStatutPilotage() == StatutPilotage.SILENCE_CRITIQUE) {
                Action a = new Action();
                a.setMedecin(m);
                a.setStatut(StatutPilotage.SILENCE_CRITIQUE.name());
                a.setSegment(m.getSegment());
                a.setActionRecommandee("visite urgence silence");
                a.setUrgence(UrgenceAction.SILENCE_CRITIQUE);
                a.setEtatAction(EtatAction.PLANIFIEE);
                a.setDateVisite(LocalDate.now().plusDays(2));
                a.setCommercial(commercial);
                a.setLieuOrganisme(m.getOrganisme());
                a.setBacklog(false);
                a.setUrgenceSilence(true);
                a.setCycleMensuel("2026-07");
                a.setCommentaire("Silence prolongé à traiter en priorité.");
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

    private StatutPilotage determineStatutPilotage(int totalCa, int dossierCount, int seq) {
        if (seq % 5 == 0) return StatutPilotage.SILENCE_CRITIQUE;
        if (seq % 4 == 0) return StatutPilotage.SURVEILLANCE;
        if (seq % 3 == 0) return StatutPilotage.ONBOARDING;
        if (totalCa >= 30000) return StatutPilotage.PROGRESSION;
        return StatutPilotage.ACTIF;
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