package com.vactis.service;

import com.vactis.dto.excel.ExcelImportSummary;
import com.vactis.model.data.ExtractionDonnees;
import com.vactis.model.medecin.Medecin;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExcelImportExportServiceTest {

    @Test
    void importsValidWorkbookAndReturnsSummary() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("extratction_donnees");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Médecin");
        header.createCell(1).setCellValue("Date réception");
        header.createCell(2).setCellValue("Specialite");
        header.createCell(3).setCellValue("Organisme");
        header.createCell(4).setCellValue("Lieu de prélèvement");
        header.createCell(5).setCellValue("Prix total");
        header.createCell(6).setCellValue("Prix à payer");
        header.createCell(7).setCellValue("Urgent");

        Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("Dr Test Import");
        row.createCell(1).setCellValue(java.sql.Date.valueOf(LocalDate.of(2025, 10, 3)));
        row.createCell(2).setCellValue("Cardiologie");
        row.createCell(3).setCellValue("Cabinet Test");
        row.createCell(4).setCellValue("Cabinet Test");
        row.createCell(5).setCellValue(1500);
        row.createCell(6).setCellValue(1200);
        row.createCell(7).setCellValue("Oui");

        Row rowWithDifferentDoctorMetadata = sheet.createRow(2);
        rowWithDifferentDoctorMetadata.createCell(0).setCellValue("Dr Test Import");
        rowWithDifferentDoctorMetadata.createCell(1).setCellValue(java.sql.Date.valueOf(LocalDate.of(2025, 10, 4)));
        rowWithDifferentDoctorMetadata.createCell(2).setCellValue("Dermatologie");
        rowWithDifferentDoctorMetadata.createCell(3).setCellValue("Autre Cabinet");
        rowWithDifferentDoctorMetadata.createCell(4).setCellValue("Autre lieu");
        rowWithDifferentDoctorMetadata.createCell(5).setCellValue(200);
        rowWithDifferentDoctorMetadata.createCell(6).setCellValue(150);

        Row rowWithMissingFields = sheet.createRow(3);
        rowWithMissingFields.createCell(1).setCellValue(java.sql.Date.valueOf(LocalDate.of(2025, 10, 5)));
        rowWithMissingFields.createCell(5).setCellValue(100);
        rowWithMissingFields.createCell(6).setCellValue(50);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "import_test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                out.toByteArray()
        );

        ExtractionDonneesRepository extractionRepository = mock(ExtractionDonneesRepository.class);
        MedecinRepository medecinRepository = mock(MedecinRepository.class);
        MedecinService medecinService = mock(MedecinService.class);
        ExcelImportService excelImportService = mock(ExcelImportService.class);
        AlerteHebdomadaireService alerteService = mock(AlerteHebdomadaireService.class);
        Medecin existingDoctor = new Medecin();
        existingDoctor.setPrenom("Test");
        existingDoctor.setNom("Import");
        existingDoctor.setSpecialite("Ancienne spécialité");
        existingDoctor.setOrganisme("Ancien organisme");
        existingDoctor.setCodeMedecin("MED-EXISTANT");
        when(extractionRepository.count()).thenReturn(0L, 3L);
        when(medecinRepository.findAll()).thenReturn(List.of(existingDoctor));
        when(extractionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ExcelImportExportService service = new ExcelImportExportService(
            extractionRepository, medecinRepository, medecinService, excelImportService, alerteService);
        ExcelImportSummary summary = service.validateWorkbook(file);

        assertEquals(3, summary.getTotalRows());
        assertEquals(3, summary.getValidRows());
        assertEquals(0, summary.getErrorRows());
        assertEquals(1400L, summary.getTotalAmount());
        assertEquals("extratction_donnees", summary.getSheetName());
        assertTrue(summary.getIssues().isEmpty());

        ExcelImportExportService.ImportResult firstImport = service.importWorkbook(file);
        assertEquals(0, firstImport.deletedRows());
        assertEquals(3, firstImport.importedRows());
        assertEquals(LocalDate.of(2025, 10, 3), firstImport.fromDate());
        assertEquals(LocalDate.of(2025, 10, 5), firstImport.toDate());
        var savedRows = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(extractionRepository).saveAll(savedRows.capture());
        List<ExtractionDonnees> importedRows = new java.util.ArrayList<>();
        savedRows.getValue().forEach(value -> importedRows.add((ExtractionDonnees) value));
        ExtractionDonnees imported = importedRows.stream().filter(value -> value.getMedecin() != null).findFirst().orElseThrow();
        ExtractionDonnees importedWithMissingFields = importedRows.stream().filter(value -> value.getMedecin() == null).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2025, 10, 3), imported.getDateReception());
        assertEquals(1500, imported.getPrixTotal());
        assertEquals(1200, imported.getPrixAPayer());
        assertEquals("Cardiologie", imported.getTypeAnalyse());
        assertSame(existingDoctor, imported.getMedecin());
        assertTrue(importedRows.stream().filter(value -> value.getMedecin() != null)
            .allMatch(value -> value.getMedecin() == existingDoctor));
        assertEquals("Non renseigné", importedWithMissingFields.getTypeAnalyse());
        assertEquals("Non renseigné", importedWithMissingFields.getLieuPrelevement());
        assertEquals(LocalDate.of(2025, 10, 3), existingDoctor.getDatePremiereCollaboration());
        assertEquals(LocalDate.of(2025, 10, 4), existingDoctor.getDateDerniereActivite());
        verify(medecinRepository, org.mockito.Mockito.never()).save(any(Medecin.class));
        verify(medecinService).recalculerStatutsEtSegmentsDynamiques(LocalDate.of(2025, 10, 5));
        verify(excelImportService).regenerateImportedDataActions(List.of(existingDoctor), LocalDate.of(2025, 10, 5));
        verify(alerteService).refreshImportedDataset(LocalDate.of(2025, 10, 5));

        ExcelImportExportService.ImportResult replacement = service.importWorkbook(file);
        assertEquals(3, replacement.deletedRows());
        assertEquals(3, replacement.importedRows());
        verify(extractionRepository, times(2)).saveAll(anyList());
        verify(extractionRepository, times(2)).deleteAllInBatch();
        verify(medecinService, times(2)).recalculerStatutsEtSegmentsDynamiques(LocalDate.of(2025, 10, 5));
        verify(excelImportService, times(2)).regenerateImportedDataActions(List.of(existingDoctor), LocalDate.of(2025, 10, 5));
        verify(alerteService, times(2)).refreshImportedDataset(LocalDate.of(2025, 10, 5));
    }
}
