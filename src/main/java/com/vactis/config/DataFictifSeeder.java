package com.vactis.config;

import com.vactis.service.ExcelImportService;

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
    private final com.vactis.repository.MedecinRepository medecinRepository;

    @Override
    public void run(String... args) {
        if (medecinRepository.count() == 0) {
            log.info("Démarrage de l'initialisation exclusive depuis data_fictif (première fois)...");
            excelImportService.importFictifExcelAndSyncMedecins();
        } else {
            log.info("Données médecins déjà présentes ({} médecins), pas de ré-importation automatique au démarrage.", medecinRepository.count());
        }
    }
}
