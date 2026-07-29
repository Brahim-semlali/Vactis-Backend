package com.vactis.config;

import com.vactis.service.data.ExcelImportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DataFictifSeeder implements CommandLineRunner {

    private final ExcelImportService excelImportService;

    @Override
    public void run(String... args) {
        log.info("Démarrage de la synchronisation exclusive depuis data_fictif...");
        excelImportService.importFictifExcelAndSyncMedecins();
    }
}
