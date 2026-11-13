package com.vactis.service;

import com.vactis.dto.excel.ExcelImportSummary;
import com.vactis.model.data.ExtractionDonnees;
import com.vactis.model.data.StatutDossier;
import com.vactis.model.medecin.Medecin;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.service.AlerteHebdomadaireService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelImportExportService {

    private static final List<String> EXPECTED_HEADERS = List.of(
            "Date réception",
            "Médecin",
            "Specialite",
            "Organisme",
            "Lieu de prélèvement",
            "Prix total",
            "Prix à payer",
            "Urgent"
    );

    private final ExtractionDonneesRepository extractionDonneesRepository;
    private final MedecinRepository medecinRepository;
    private final MedecinService medecinService;
    private final ExcelImportService excelImportService;
    private final AlerteHebdomadaireService alerteHebdomadaireService;

    @PersistenceContext
    private EntityManager entityManager;

    public ExcelImportSummary validateWorkbook(MultipartFile file) {
        ExcelImportSummary summary = new ExcelImportSummary();
        summary.setStatus("VALIDATION");

        if (file == null || file.isEmpty()) {
            summary.getIssues().add("Le fichier est vide.");
            summary.setStatus("ERROR");
            return summary;
        }

        String filename = file.getOriginalFilename() == null ? "import.xlsx" : file.getOriginalFilename();
        String lower = filename.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".xlsx") || lower.endsWith(".xls"))) {
            summary.getIssues().add("Format non pris en charge. Veuillez importer un fichier .xlsx ou .xls.");
            summary.setStatus("ERROR");
            return summary;
        }

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                summary.getIssues().add("Le classeur ne contient aucune feuille exploitable.");
                summary.setStatus("ERROR");
                return summary;
            }

            summary.setSheetName(sheet.getSheetName());
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                summary.getIssues().add("La première ligne est absente ou vide.");
                summary.setStatus("ERROR");
                return summary;
            }

            List<String> headerValues = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                headerValues.add(getCellText(cell).trim());
            }
            summary.setColumns(headerValues);

            List<String> missing = EXPECTED_HEADERS.stream()
                    .filter(expected -> headerValues.stream().noneMatch(value -> value.equalsIgnoreCase(expected)))
                    .toList();
            if (!missing.isEmpty()) {
                summary.getIssues().add("Colonnes obligatoires absentes : " + String.join(", ", missing));
                summary.setStatus("ERROR");
                return summary;
            }
            Map<String, Integer> columnIndexes = headerIndexes(headerValues);

            int totalRows = 0;
            int validRows = 0;
            int errorRows = 0;
            List<Map<String, Object>> previewRows = new ArrayList<>();
            LocalDate minDate = null;
            LocalDate maxDate = null;
            Long totalAmount = 0L;

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                if (isEmptyRow(row, columnIndexes)) {
                    continue;
                }
                totalRows++;

                String dateText = getCellText(row.getCell(columnIndexes.get("date réception"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                String medecin = getCellText(row.getCell(columnIndexes.get("médecin"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                String specialite = getCellText(row.getCell(columnIndexes.get("specialite"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                String organisme = getCellText(row.getCell(columnIndexes.get("organisme"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                String lieu = getCellText(row.getCell(columnIndexes.get("lieu de prélèvement"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                String prixTotalText = getCellText(row.getCell(columnIndexes.get("prix total"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                String prixPayerText = getCellText(row.getCell(columnIndexes.get("prix à payer"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                String urgent = getCellText(row.getCell(columnIndexes.get("urgent"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));

                LocalDate dateValue = readDate(row.getCell(columnIndexes.get("date réception"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                Integer totalValue = readInteger(row.getCell(columnIndexes.get("prix total"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                Integer payerValue = readInteger(row.getCell(columnIndexes.get("prix à payer"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));

                List<String> rowIssues = new ArrayList<>();
                if (dateValue == null) {
                    rowIssues.add("Ligne " + (rowIndex + 1) + " : date invalide");
                } else {
                    minDate = (minDate == null || dateValue.isBefore(minDate)) ? dateValue : minDate;
                    maxDate = (maxDate == null || dateValue.isAfter(maxDate)) ? dateValue : maxDate;
                }
                if (totalValue == null) rowIssues.add("Ligne " + (rowIndex + 1) + " : prix total invalide");
                if (payerValue == null) rowIssues.add("Ligne " + (rowIndex + 1) + " : prix à payer invalide");
                if (!urgent.isBlank() && !"oui".equalsIgnoreCase(urgent) && !"non".equalsIgnoreCase(urgent) && !"true".equalsIgnoreCase(urgent) && !"false".equalsIgnoreCase(urgent)) {
                    rowIssues.add("Ligne " + (rowIndex + 1) + " : statut urgent invalide");
                }

                if (rowIssues.isEmpty()) {
                    validRows++;
                    totalAmount += payerValue == null ? 0L : payerValue.longValue();
                    Map<String, Object> rowMap = new LinkedHashMap<>();
                    rowMap.put("Date réception", dateValue);
                    rowMap.put("Médecin", fallback(medecin));
                    rowMap.put("Specialite", fallback(specialite));
                    rowMap.put("Organisme", fallback(organisme));
                    rowMap.put("Lieu de prélèvement", fallback(lieu));
                    rowMap.put("Prix total", totalValue);
                    rowMap.put("Prix à payer", payerValue);
                    rowMap.put("Urgent", urgent);
                    if (previewRows.size() < 5) {
                        previewRows.add(rowMap);
                    }
                } else {
                    errorRows++;
                    summary.getIssues().addAll(rowIssues);
                }
            }

            summary.setTotalRows(totalRows);
            summary.setValidRows(validRows);
            summary.setErrorRows(errorRows);
            summary.setPreviewRows(previewRows);
            summary.setFromDate(minDate == null ? null : minDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
            summary.setToDate(maxDate == null ? null : maxDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
            summary.setTotalAmount(totalAmount);
            if (totalRows == 0) {
                summary.getIssues().add("Aucune ligne de données n’a été trouvée dans la feuille.");
                errorRows++;
                summary.setErrorRows(errorRows);
            }
            summary.setStatus(errorRows > 0 ? "ERROR" : "READY");
            return summary;
        } catch (Exception ex) {
            log.error("Erreur de validation du classeur Excel", ex);
            summary.getIssues().add("Le fichier ne peut pas être lu. Vérifiez qu’il est bien un fichier Excel valide.");
            summary.setStatus("ERROR");
            return summary;
        }
    }

    @Transactional
    public ImportResult importWorkbook(MultipartFile file) {
        ExcelImportSummary summary = validateWorkbook(file);
        if ("ERROR".equals(summary.getStatus())) {
            throw new IllegalArgumentException(summary.getIssues().isEmpty() ? "Fichier Excel invalide." : String.join("; ", summary.getIssues()));
        }

        List<ImportRecord> records = new ArrayList<>();
        Map<String, Integer> duplicateOccurrences = new HashMap<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            for (int index = 0; index < headerRow.getLastCellNum(); index++) {
                headers.add(getCellText(headerRow.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)).trim());
            }
            Map<String, Integer> columnIndexes = headerIndexes(headers);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isEmptyRow(row, columnIndexes)) {
                    continue;
                }

                String dateText = getCellText(row.getCell(columnIndexes.get("date réception"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                String medecinName = getCellText(row.getCell(columnIndexes.get("médecin"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                String specialite = getCellText(row.getCell(columnIndexes.get("specialite"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                String organisme = getCellText(row.getCell(columnIndexes.get("organisme"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                String lieu = getCellText(row.getCell(columnIndexes.get("lieu de prélèvement"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                String prixTotalText = getCellText(row.getCell(columnIndexes.get("prix total"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                String prixPayerText = getCellText(row.getCell(columnIndexes.get("prix à payer"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                String urgent = getCellText(row.getCell(columnIndexes.get("urgent"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));

                LocalDate dateReception = readDate(row.getCell(columnIndexes.get("date réception"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                Integer prixTotal = readInteger(row.getCell(columnIndexes.get("prix total"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                Integer prixAPayer = readInteger(row.getCell(columnIndexes.get("prix à payer"), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                if (dateReception == null || prixTotal == null || prixAPayer == null) {
                    continue;
                }

                String baseKey = buildBusinessKey(dateReception, medecinName, specialite, organisme, lieu, prixTotal, prixAPayer, urgent);
                int occurrence = duplicateOccurrences.merge(baseKey, 1, Integer::sum);
                String key = occurrence == 1 ? baseKey : baseKey + "-" + occurrence;
                records.add(new ImportRecord(key, dateReception, medecinName, specialite, organisme, lieu,
                        prixTotal, prixAPayer, "oui".equalsIgnoreCase(urgent) || "true".equalsIgnoreCase(urgent)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de lire le fichier Excel pour l’import.", e);
        }

        if (records.isEmpty()) {
            throw new IllegalArgumentException("Aucune ligne importable n’a été trouvée dans le fichier.");
        }

        LocalDate startDate = records.stream().map(ImportRecord::dateReception).min(LocalDate::compareTo).orElseThrow();
        LocalDate endDate = records.stream().map(ImportRecord::dateReception).max(LocalDate::compareTo).orElseThrow();
        int deletedRows = Math.toIntExact(extractionDonneesRepository.count());
        extractionDonneesRepository.deleteAllInBatch();
        // Force l'exécution immédiate du DELETE en DB avant les INSERT
        // pour éviter la violation de la contrainte UNIQUE sur reference_dossier
        // lorsqu'on ré-importe le même fichier dans la même transaction.
        if (entityManager != null) {
            entityManager.flush();
            entityManager.clear();
        }
        Map<String, Medecin> medecinsByKey = new HashMap<>();
        medecinRepository.findAll().forEach(medecin -> medecinsByKey.putIfAbsent(
            normalizeDoctorName(medecin.getPrenom() + " " + medecin.getNom()).toLowerCase(Locale.ROOT), medecin));
        List<ExtractionDonnees> toSave = new ArrayList<>();
        Map<String, DoctorActivity> activityByDoctor = new HashMap<>();

        for (ImportRecord record : records) {
            Medecin medecin = null;
            if (!record.medecinName().isBlank()) {
                String doctorKey = normalizeDoctorName(record.medecinName()).toLowerCase(Locale.ROOT);
                medecin = medecinsByKey.computeIfAbsent(doctorKey, ignored -> createMedecin(
                        record.medecinName(), record.specialite(), record.organisme()));
                DoctorActivity activity = activityByDoctor.computeIfAbsent(doctorKey, ignored -> new DoctorActivity());
                activity.include(record.dateReception());
                if (activity.rowCount == 1) {
                    if (!record.specialite().isBlank()) medecin.setSpecialite(record.specialite());
                    if (!record.organisme().isBlank()) medecin.setOrganisme(record.organisme());
                }
            }
                ExtractionDonnees dossier = new ExtractionDonnees();
                dossier.setReferenceDossier(record.reference());
                dossier.setMedecin(medecin);
                dossier.setDateReception(record.dateReception());
                dossier.setDatePrelevement(record.dateReception());
                dossier.setTypeAnalyse(fallback(record.specialite()));
                dossier.setNombreAnalyses(1);
                dossier.setStatutDossier(StatutDossier.TERMINE);
                dossier.setLieuPrelevement(fallback(record.lieu().isBlank() ? record.organisme() : record.lieu()));
                dossier.setPrixTotal(record.prixTotal());
                dossier.setPrixAPayer(record.prixAPayer());
                dossier.setMontantRembourse(Math.max(0, record.prixTotal() - record.prixAPayer()));
                dossier.setUrgent(record.urgent());
                toSave.add(dossier);
        }

        if (!toSave.isEmpty()) {
            extractionDonneesRepository.saveAll(toSave);
        }
        for (Map.Entry<String, Medecin> entry : medecinsByKey.entrySet()) {
            DoctorActivity activity = activityByDoctor.get(entry.getKey());
            Medecin medecin = entry.getValue();
            if (activity == null) {
                medecin.setDatePremiereCollaboration(null);
                medecin.setDateDerniereActivite(null);
            } else {
                medecin.setDatePremiereCollaboration(activity.firstDate);
                medecin.setDateDerniereActivite(activity.lastDate);
            }
        }
        medecinRepository.saveAll(medecinsByKey.values());

        LocalDate dateReference = endDate;
        medecinService.recalculerStatutsEtSegmentsDynamiques(dateReference);
        List<Medecin> importedDoctors = activityByDoctor.keySet().stream()
                .map(medecinsByKey::get)
                .filter(Objects::nonNull)
                .toList();
        excelImportService.regenerateImportedDataActions(importedDoctors, dateReference);
        alerteHebdomadaireService.refreshImportedDataset(dateReference);
        return new ImportResult(deletedRows, toSave.size(), startDate, endDate);
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> listRows(Integer year, Integer month, String search, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        return extractionDonneesRepository.searchAdminRows(year, month, normalizeSearch(search),
                        PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "dateReception").and(Sort.by(Sort.Direction.DESC, "id"))))
                .map(this::toRowMap);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listExportRows(Integer year, Integer month, String search) {
        return extractionDonneesRepository.findAllAdminRows(year, month, normalizeSearch(search)).stream()
                .map(this::toRowMap)
                .toList();
    }

    public Map<String, Object> getFilterMetadata() {
        List<Integer> years = extractionDonneesRepository.findDistinctYears();
        List<Integer> months = new ArrayList<>();
        for (Integer year : years) {
            months.addAll(extractionDonneesRepository.findDistinctMonthsByYear(year));
        }
        months = months.stream().distinct().sorted().toList();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("years", years);
        metadata.put("months", months);
        return metadata;
    }

    public byte[] exportWorkbook(List<Map<String, Object>> rows, String filenamePrefix) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Export Vactis");
        Row headerRow = sheet.createRow(0);

        List<String> headers = List.of(
                "Date réception",
                "Médecin",
                "Specialite",
                "Organisme",
                "Lieu de prélèvement",
                "Prix total",
                "Prix à payer",
                "Urgent"
        );

        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
        }

        int rowIndex = 1;
        for (Map<String, Object> row : rows) {
            Row rowValue = sheet.createRow(rowIndex++);
            int columnIndex = 0;
            for (String header : headers) {
                Object value = row.getOrDefault(header, "");
                Cell cell = rowValue.createCell(columnIndex++);
                if (value instanceof Number number) {
                    cell.setCellValue(number.doubleValue());
                } else if (value instanceof LocalDate date) {
                    cell.setCellValue(java.sql.Date.valueOf(date));
                } else if (value instanceof Boolean bool) {
                    cell.setCellValue(bool ? "Oui" : "Non");
                } else {
                    cell.setCellValue(value == null ? "" : String.valueOf(value));
                }
            }
        }

        for (int i = 0; i < headers.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            workbook.close();
            return out.toByteArray();
        }
    }

    private Map<String, Object> toRowMap(ExtractionDonnees row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", row.getId());
        map.put("dateReception", row.getDateReception());
        String medecin = row.getMedecin() == null ? "Non identifié" : normalizeMedecinName(row.getMedecin());
        map.put("Médecin", medecin);
        map.put("Medecin", medecin);
        map.put("Specialite", row.getTypeAnalyse());
        String organisme = row.getMedecin() == null ? "" : row.getMedecin().getOrganisme();
        map.put("Organisme", organisme);
        map.put("Lieu de prélèvement", row.getLieuPrelevement());
        map.put("Prix total", row.getPrixTotal());
        map.put("Prix à payer", row.getPrixAPayer());
        map.put("Urgent", row.isUrgent() ? "Oui" : "Non");
        map.put("typeAnalyse", row.getTypeAnalyse());
        map.put("organisme", organisme);
        map.put("lieuPrelevement", row.getLieuPrelevement());
        map.put("prixTotal", row.getPrixTotal());
        map.put("prixAPayer", row.getPrixAPayer());
        map.put("urgent", row.isUrgent());
        return map;
    }

    private static String normalizeSearch(String search) {
        return search == null || search.isBlank() ? null : search.trim();
    }

    private static String fallback(String value) {
        return value == null || value.isBlank() ? "Non renseigné" : value.trim();
    }

    private String normalizeMedecinName(Medecin medecin) {
        if (medecin == null) {
            return "Non identifié";
        }
        StringBuilder name = new StringBuilder();
        if (medecin.getPrenom() != null && !medecin.getPrenom().isBlank()) {
            name.append(medecin.getPrenom());
        }
        if (medecin.getNom() != null && !medecin.getNom().isBlank()) {
            if (!name.isEmpty()) name.append(" ");
            name.append(medecin.getNom());
        }
        if (name.isEmpty()) {
            return "Non identifié";
        }
        return name.toString();
    }

    private Medecin createMedecin(String doctorName, String specialite, String organisme) {
        String normalizedName = normalizeDoctorName(doctorName);
        String cleanedName = doctorName.trim().replaceFirst("(?i)^dr\\.?\\s*", "");
        String[] parts = cleanedName.split("\\s+");
        Medecin medecin = new Medecin();
        medecin.setPrenom(parts.length > 1 ? parts[0] : "Dr");
        medecin.setNom(parts.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length)) : cleanedName);
        medecin.setCodeMedecin("IMP" + digest(normalizedName.toLowerCase(Locale.ROOT), 8).toUpperCase(Locale.ROOT));
        medecin.setSpecialite(specialite.isBlank() ? "Autre" : specialite);
        medecin.setOrganisme(organisme.isBlank() ? "Non précisé" : organisme);
        medecin.setStatut("NOUVEAU");
        medecin.setStatutPilotage(com.vactis.model.medecin.StatutPilotage.ACTIF_STABLE);
        medecin.setRisqueUrgence(com.vactis.model.medecin.RisqueUrgence.FAIBLE);
        medecin.setCaMois(0);
        medecin.setCaBaseline(0);
        medecin.setCaTotal(0);
        medecin.setTotalCas(0);
        return medecinRepository.save(medecin);
    }

    private static String buildBusinessKey(LocalDate date, String medecin, String specialite, String organisme,
                                           String lieu, Integer prixTotal, Integer prixAPayer, String urgent) {
        String raw = String.format(Locale.ROOT, "%s|%s|%s|%s|%s|%s|%s|%s",
                date,
                sanitize(medecin),
                sanitize(specialite),
                sanitize(organisme),
                sanitize(lieu),
                prixTotal == null ? "0" : prixTotal,
                prixAPayer == null ? "0" : prixAPayer,
                sanitize(urgent));
        return "VACTIS-" + digest(raw, 16).toUpperCase(Locale.ROOT);
    }

    private static String digest(String value, int bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible de générer une référence d’import.", exception);
        }
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String normalizeDoctorName(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("(?i)^dr\\b", "")
                .replaceAll("[^a-zA-Z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Map<String, Integer> headerIndexes(List<String> headers) {
        Map<String, Integer> indexes = new HashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            indexes.put(headers.get(index).trim().toLowerCase(Locale.ROOT), index);
        }
        return indexes;
    }

    private static boolean isEmptyRow(Row row, Map<String, Integer> columnIndexes) {
        return EXPECTED_HEADERS.stream().allMatch(header -> {
            Integer index = columnIndexes.get(header.toLowerCase(Locale.ROOT));
            return index == null || getCellText(row.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)).isBlank();
        });
    }

    private record ImportRecord(String reference, LocalDate dateReception, String medecinName, String specialite,
                                String organisme, String lieu, Integer prixTotal, Integer prixAPayer, boolean urgent) {}

    private static final class DoctorActivity {
        private LocalDate firstDate;
        private LocalDate lastDate;
        private int rowCount;

        private void include(LocalDate date) {
            firstDate = firstDate == null || date.isBefore(firstDate) ? date : firstDate;
            lastDate = lastDate == null || date.isAfter(lastDate) ? date : lastDate;
            rowCount++;
        }
    }

    public record ImportResult(int deletedRows, int importedRows, LocalDate fromDate, LocalDate toDate) {}

    private static String getCellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue() == null ? "" : cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    : String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> String.valueOf(cell.getNumericCellValue());
            default -> "";
        };
    }

    private static LocalDate readDate(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            try {
                return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate();
            } catch (Exception ignored) {
                return null;
            }
        }
        return parseDate(getCellText(cell));
    }

    private static Integer readInteger(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (!Double.isFinite(value) || value < 0 || value > Integer.MAX_VALUE) {
                return null;
            }
            return (int) Math.round(value);
        }
        Integer value = parseInteger(getCellText(cell));
        return value != null && value >= 0 ? value : null;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(trimmed);
        }
        if (trimmed.matches("\\d{2}/\\d{2}/\\d{4}")) {
            return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
        if (trimmed.matches("\\d{1,2}/\\d{1,2}/\\d{2,4}")) {
            try {
                return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("d/M/yy"));
            } catch (Exception ignored) {
                return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("d/M/yyyy"));
            }
        }
        return null;
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.replace(" ", "").replace("€", "").replace("MAD", "").replace(".", "").replace(",", ".");
        try {
            return (int) Math.round(Double.parseDouble(cleaned));
        } catch (Exception e) {
            return null;
        }
    }
}
