package com.vactis.controller;

import com.vactis.dto.excel.ExcelImportSummary;
import com.vactis.service.ExcelImportExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/excel")
@RequiredArgsConstructor
@Slf4j
public class ExcelImportExportController {

    private final ExcelImportExportService excelImportExportService;

    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExcelImportSummary validate(@RequestParam("file") MultipartFile file) {
        return excelImportExportService.validateWorkbook(file);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'MENU:/import-export-excel')")
    public Map<String, Object> importExcel(@RequestParam("file") MultipartFile file) {
        ExcelImportExportService.ImportResult result;
        try {
            result = excelImportExportService.importWorkbook(file);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            Throwable rootCause = exception;
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            log.error("Import Excel refusé par une contrainte de base de données; la transaction a été annulée: {}",
                    rootCause.getMessage(), exception);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Import refusé par une contrainte de base de données. Le remplacement a été annulé et les données précédentes ont été conservées. Contactez l’administrateur avec l’heure de l’erreur.");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Remplacement complet des dossiers terminé.");
        response.put("deletedRows", result.deletedRows());
        response.put("importedRows", result.importedRows());
        response.put("periodFrom", result.fromDate());
        response.put("periodTo", result.toDate());
        response.put("status", "OK");
        return response;
    }

    @GetMapping("/rows")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'MENU:/import-export-excel')")
    public Page<Map<String, Object>> getRows(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return excelImportExportService.listRows(year, month, search, page, size);
    }

    @GetMapping("/filters")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'MENU:/import-export-excel')")
    public Map<String, Object> getFilters() {
        return excelImportExportService.getFilterMetadata();
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'MENU:/import-export-excel')")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String search
    ) throws IOException {
        List<Map<String, Object>> rows = excelImportExportService.listExportRows(year, month, search);
        byte[] payload = excelImportExportService.exportWorkbook(rows, "Vactis_Export");
        String filename = "Vactis_Export_" + LocalDate.now().toString() + ".xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return new ResponseEntity<>(payload, headers, HttpStatus.OK);
    }
}
