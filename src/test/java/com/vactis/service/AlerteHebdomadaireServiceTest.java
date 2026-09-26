package com.vactis.service;

import com.vactis.model.action.Action;
import com.vactis.model.alerte.AlerteHebdomadaire;
import com.vactis.model.alerte.StatutAlerte;
import com.vactis.model.alerte.TypeAlerte;
import com.vactis.model.medecin.Medecin;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.AlerteHebdomadaireRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.RetourTerrainRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlerteHebdomadaireServiceTest {

    @Mock
    private AlerteHebdomadaireRepository alerteHebdomadaireRepository;

    @Mock
    private MedecinRepository medecinRepository;

    @Mock
    private ExtractionDonneesRepository extractionDonneesRepository;

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private RetourTerrainRepository retourTerrainRepository;

    @InjectMocks
    private AlerteHebdomadaireService alerteHebdomadaireService;

    private Medecin medecin;

    @BeforeEach
    void setUp() {
        medecin = new Medecin();
        medecin.setId(10L);
        medecin.setNom("Benali");
        medecin.setPrenom("Karim");
        medecin.setStatut("ACTIF_STABLE");
        medecin.setSegment("A");
        medecin.setIsProfilIrregulier(false);
        medecin.setNoteInput(4.0);
        medecin.setFiabilite("PARTIEL");
        medecin.setScoreRisque(40.0);
        medecin.setScoreValeur(75.0);
    }

    @Test
    @DisplayName("Le batch hebdomadaire détecte une rupture de rythme pour un médecin régulier")
    void testExecuterBatchHebdomadaire_DetecteRupture() {
        when(medecinRepository.findAll()).thenReturn(List.of(medecin));
        when(retourTerrainRepository.findByMedecinOrderByDateVisiteDescCreatedAtDesc(medecin)).thenReturn(List.of());

        // 4 dates distinctes sur 2 mois : intervalle moyen ~6.6 jours, silence de 30 jours (rupture nette)
        LocalDate d1 = LocalDate.now().minusDays(30);
        LocalDate d2 = LocalDate.now().minusDays(35);
        LocalDate d3 = LocalDate.now().minusDays(40);
        LocalDate d4 = LocalDate.now().minusDays(50);
        when(extractionDonneesRepository.findDatesReceptionByMedecinId(10L)).thenReturn(List.of(d1, d2, d3, d4));
        when(extractionDonneesRepository.countCasByMedecinId(10L)).thenReturn(12L);
        when(alerteHebdomadaireRepository.findRecentActiveAlerte(eq(medecin), any(LocalDate.class), eq(StatutAlerte.A_TRAITER)))
                .thenReturn(Optional.empty());

        when(alerteHebdomadaireRepository.save(any(AlerteHebdomadaire.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<AlerteHebdomadaire> result = alerteHebdomadaireService.executerBatchHebdomadaire();

        assertFalse(result.isEmpty());
        AlerteHebdomadaire alerte = result.get(0);
        assertEquals(StatutAlerte.A_TRAITER, alerte.getStatutAlerte());
        assertTrue(alerte.getJoursSilence() >= 20);
        assertNotNull(medecin.getScoreUrgence());
    }

    @Test
    @DisplayName("Ignorer les médecins ayant un profil irrégulier validé")
    void testExecuterBatchHebdomadaire_IgnoreIrreguliers() {
        medecin.setIsProfilIrregulier(true);
        when(medecinRepository.findAll()).thenReturn(List.of(medecin));
        when(retourTerrainRepository.findByMedecinOrderByDateVisiteDescCreatedAtDesc(medecin)).thenReturn(List.of());

        List<AlerteHebdomadaire> result = alerteHebdomadaireService.executerBatchHebdomadaire();

        assertTrue(result.isEmpty());
        verify(extractionDonneesRepository, never()).findDatesReceptionByMedecinId(any());
    }

    @Test
    @DisplayName("Génération d'une action commerciale J+3 depuis une alerte stratégique")
    void testGenererActionDepuisAlerte() {
        AlerteHebdomadaire alerte = new AlerteHebdomadaire();
        alerte.setId(1L);
        alerte.setMedecin(medecin);
        alerte.setJoursSilence(25);
        alerte.setMessage("Silence critique");
        alerte.setStatutAlerte(StatutAlerte.A_TRAITER);

        when(alerteHebdomadaireRepository.findById(1L)).thenReturn(Optional.of(alerte));
        when(actionRepository.save(any(Action.class))).thenAnswer(invocation -> {
            Action a = invocation.getArgument(0);
            a.setId(99L);
            return a;
        });

        Action action = alerteHebdomadaireService.genererActionDepuisAlerte(1L, "delegue1");

        assertNotNull(action);
        assertEquals(3, action.getHorizonJours());
        assertEquals(StatutAlerte.ACTION_CREEE, alerte.getStatutAlerte());
        assertEquals(99L, alerte.getActionGenereeId());
    }
}
