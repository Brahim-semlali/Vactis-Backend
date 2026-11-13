package com.vactis.config;

import com.vactis.service.ExcelImportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DataFictifSeeder implements CommandLineRunner {

    private final ExcelImportService excelImportService;
    private final com.vactis.repository.MedecinRepository medecinRepository;

    @Value("${vactis.data-fictif.force-sync:false}")
    private boolean forceSync;

    @Override
    public void run(String... args) {
        if (forceSync || medecinRepository.count() == 0) {
            log.info("Démarrage du remplacement depuis le classeur data_fictif (forcé: {})...", forceSync);
            excelImportService.importFictifExcelAndSyncMedecins();
        } else {
            log.info("Données médecins déjà présentes ({} médecins), pas de ré-importation automatique au démarrage.", medecinRepository.count());
        }
    }
}
