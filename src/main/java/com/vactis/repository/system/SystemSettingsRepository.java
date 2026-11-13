package com.vactis.repository.system;

import com.vactis.model.system.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Long> {
    SystemSettings findFirstByOrderByIdAsc();

    default SystemSettings findFirstOrCreateDefault() {
        SystemSettings settings = findFirstByOrderByIdAsc();
        if (settings != null) {
            if (settings.getDureeSessionMinutes() == null) settings.setDureeSessionMinutes(60);
            if (settings.getDureeInactiviteJours() == null) settings.setDureeInactiviteJours(90);
            if (settings.getMdpLongueurMinimale() == null) settings.setMdpLongueurMinimale(8);
            if (settings.getMdpExigeMajuscule() == null) settings.setMdpExigeMajuscule(false);
            if (settings.getMdpExigeChiffre() == null) settings.setMdpExigeChiffre(false);
            if (settings.getMdpExigeCaractereSpecial() == null) settings.setMdpExigeCaractereSpecial(false);
            if (settings.getMaxTentativesConnexion() == null) settings.setMaxTentativesConnexion(5);
            if (settings.getDureeBlocageMinutes() == null) settings.setDureeBlocageMinutes(15);
            if (settings.getJournalConnexionActif() == null) settings.setJournalConnexionActif(true);
            if (settings.getUpdatedAt() == null) settings.setUpdatedAt(java.time.LocalDateTime.now());
            save(settings);
            return settings;
        }
        SystemSettings defaults = new SystemSettings();
        defaults.setDureeSessionMinutes(60);
        defaults.setDureeInactiviteJours(90);
        defaults.setUpdatedAt(java.time.LocalDateTime.now());
        return save(defaults);
    }
}