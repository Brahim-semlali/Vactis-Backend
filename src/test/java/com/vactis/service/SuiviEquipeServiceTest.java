package com.vactis.service;

import com.vactis.dto.suiviequipe.SuiviEquipeDtos.CommercialResume;
import com.vactis.dto.suiviequipe.SuiviEquipeDtos.ComparaisonVisite;
import com.vactis.dto.suiviequipe.SuiviEquipeDtos.EvolutionMedecin;
import com.vactis.dto.suiviequipe.SuiviEquipeDtos.ReclamationLigne;
import com.vactis.dto.suiviequipe.SuiviEquipeDtos.VisiteLigne;
import com.vactis.model.action.Action;
import com.vactis.model.data.ExtractionDonnees;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.StatutVisite;
import com.vactis.model.medecin.TypeVisite;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.ReclamationRepository;
import com.vactis.repository.RetourTerrainRepository;
import com.vactis.service.Activite.ActivitePortefeuilleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SuiviEquipeServiceTest {

    @Mock private RetourTerrainRepository retourTerrainRepository;
    @Mock private ActionRepository actionRepository;
    @Mock private ReclamationRepository reclamationRepository;
    @Mock private MedecinRepository medecinRepository;
    @Mock private ExtractionDonneesRepository extractionDonneesRepository;
    @Mock private ActivitePortefeuilleService portefeuilleService;

    @InjectMocks private SuiviEquipeService service;

    private Medecin medecin;

    @BeforeEach
    void setUp() {
        medecin = new Medecin();
        medecin.setId(7L);
        medecin.setNom("ENNOUR");
        medecin.setPrenom("Mhammed");
        medecin.setSegment("A");
        when(actionRepository.findAll()).thenReturn(List.of());
        when(reclamationRepository.findAll()).thenReturn(List.of());
        when(retourTerrainRepository.findAll()).thenReturn(List.of());
        when(portefeuilleService.buildStatutMapForMonth(any(), any())).thenReturn(Map.of(7L, "stable"));
    }

    @Test
    void regroupeActionsVisitesEtReclamationsParCommercialSansTenirCompteDeLaCasse() {
        Action action = new Action();
        action.setCommercial("Mouad");
        action.setDateVisite(LocalDate.of(2026, 10, 5));
        action.setEstEnRetard(true);
        when(actionRepository.findAll()).thenReturn(List.of(action));

        RetourTerrain vactis = visite(1L, "mouad", LocalDate.of(2026, 10, 6), StatutVisite.REALISEE, QualificationVisite.FAVORABLE);
        vactis.setAction(action);
        RetourTerrain libre = visite(2L, "Mouad", LocalDate.of(2026, 10, 20), StatutVisite.REALISEE, QualificationVisite.DEFAVORABLE);
        RetourTerrain manquee = visite(3L, "Khalid", LocalDate.of(2026, 10, 8), StatutVisite.NON_REALISEE, null);
        when(retourTerrainRepository.findAll()).thenReturn(List.of(vactis, libre, manquee));

        Reclamation rec = new Reclamation();
        rec.setCommercialDeclarant("MOUAD");
        rec.setDateDeclaration(LocalDate.of(2026, 10, 7));
        rec.setStatut(StatutReclamation.A_TRAITER);
        when(reclamationRepository.findAll()).thenReturn(List.of(rec));

        List<CommercialResume> resumes = service.getCommerciaux(null, null);

        assertEquals(2, resumes.size());
        CommercialResume mouad = resumes.stream().filter(r -> r.getCommercial().equalsIgnoreCase("mouad")).findFirst().orElseThrow();
        assertEquals(1, mouad.getActionsPrevues());
        assertEquals(1, mouad.getActionsEnRetard());
        assertEquals(2, mouad.getVisitesRealisees());
        assertEquals(1, mouad.getVisitesVactis());
        assertEquals(1, mouad.getVisitesLibres());
        assertEquals(1, mouad.getQualificationsFavorables());
        assertEquals(1, mouad.getQualificationsDefavorables());
        assertEquals(1, mouad.getMedecinsVisites());
        assertEquals(1, mouad.getReclamationsOuvertes());
        assertEquals(LocalDate.of(2026, 10, 20), mouad.getDerniereVisite());
        CommercialResume khalid = resumes.stream().filter(r -> r.getCommercial().equals("Khalid")).findFirst().orElseThrow();
        assertEquals(1, khalid.getVisitesNonRealisees());
        assertEquals(0, khalid.getVisitesRealisees());
    }

    @Test
    void historiqueFiltreParCommercialEtSignaleLesVisitesRepetees() {
        RetourTerrain v1 = visite(1L, "Mouad", LocalDate.of(2026, 10, 1), StatutVisite.REALISEE, QualificationVisite.NEUTRE);
        RetourTerrain v2 = visite(2L, "Mouad", LocalDate.of(2026, 10, 20), StatutVisite.REALISEE, QualificationVisite.FAVORABLE);
        RetourTerrain autre = visite(3L, "Khalid", LocalDate.of(2026, 9, 1), StatutVisite.REALISEE, null);
        when(retourTerrainRepository.findAll()).thenReturn(List.of(v1, v2, autre));

        List<VisiteLigne> lignes = service.getVisites("mouad", null, null, null, null);

        assertEquals(2, lignes.size());
        assertEquals(LocalDate.of(2026, 10, 20), lignes.get(0).getDateVisite());
        assertTrue(lignes.get(0).isVisiteRepetee());
        assertFalse(lignes.get(0).isVactis());
        assertEquals("ENNOUR Mhammed", lignes.get(0).getMedecinNom());
    }

    @Test
    void reclamationOuverteAvecEcheanceDepasseeEstEnRetard() {
        Reclamation rec = new Reclamation();
        rec.setId(1L);
        rec.setMedecin(medecin);
        rec.setCommercialDeclarant("Mouad");
        rec.setDateDeclaration(LocalDate.now().minusDays(10));
        rec.setStatut(StatutReclamation.EN_COURS);
        rec.setResponsableTraitement("Direction");
        rec.setDateEcheance(LocalDate.now().minusDays(1));
        when(reclamationRepository.findAll()).thenReturn(List.of(rec));

        List<ReclamationLigne> lignes = service.getReclamations("Mouad", null, null);

        assertEquals(1, lignes.size());
        assertTrue(lignes.get(0).isOuverte());
        assertTrue(lignes.get(0).isEnRetard());
        assertEquals("Direction", lignes.get(0).getResponsable());
    }

    @Test
    void evolutionCompareAvantApresEtSignaleDonneesInsuffisantes() {
        when(medecinRepository.findById(7L)).thenReturn(Optional.of(medecin));
        when(extractionDonneesRepository.findAllDatesDescending()).thenReturn(List.of(LocalDate.of(2026, 10, 31)));
        List<ExtractionDonnees> dossiers = new ArrayList<>();
        dossiers.add(dossier(LocalDate.of(2026, 5, 10), 1000, 2));
        dossiers.add(dossier(LocalDate.of(2026, 6, 10), 1000, 2));
        dossiers.add(dossier(LocalDate.of(2026, 7, 10), 1000, 2));
        dossiers.add(dossier(LocalDate.of(2026, 9, 10), 3000, 5));
        dossiers.add(dossier(LocalDate.of(2026, 10, 10), 3000, 5));
        when(extractionDonneesRepository.findByMedecinIdOrderByDateReceptionAsc(7L)).thenReturn(dossiers);

        RetourTerrain visiteJuillet = visite(1L, "Mouad", LocalDate.of(2026, 8, 5), StatutVisite.REALISEE, QualificationVisite.FAVORABLE);
        RetourTerrain visiteOctobre = visite(2L, "Mouad", LocalDate.of(2026, 10, 15), StatutVisite.REALISEE, QualificationVisite.NEUTRE);
        when(retourTerrainRepository.findByMedecinOrderByDateVisiteDescCreatedAtDesc(medecin))
                .thenReturn(List.of(visiteOctobre, visiteJuillet));

        EvolutionMedecin evolution = service.getEvolutionMedecin(7L);

        assertEquals("2026-10", evolution.getDernierMoisDisponible());
        assertEquals(6, evolution.getMois().size());
        assertEquals(SuiviEquipeService.AVERTISSEMENT, evolution.getAvertissement());

        ComparaisonVisite aout = evolution.getComparaisons().get(0);
        assertEquals(1000.0, aout.getCaMoyenAvant());
        assertEquals(3000.0, aout.getCaMoyenApres());
        assertEquals(2, aout.getMoisApresDisponibles());
        assertEquals(200.0, aout.getEvolutionCaPct());
        assertEquals(3000L, aout.getCaMPlus1());
        assertNull(aout.getCaMPlus3());
        assertEquals("EN_HAUSSE", aout.getTendance());
        assertTrue(aout.isVisitesRepetees());

        ComparaisonVisite octobre = evolution.getComparaisons().get(1);
        assertTrue(octobre.isDonneesInsuffisantes());
        assertEquals("NON_OBSERVABLE", octobre.getTendance());
        assertNull(octobre.getCaMPlus1());
    }

    @Test
    void tendanceUtiliseUnSeuilDeDixPourcent() {
        assertEquals("STABLE", SuiviEquipeService.tendance(100.0, 105.0));
        assertEquals("EN_BAISSE", SuiviEquipeService.tendance(100.0, 80.0));
        assertEquals("NON_OBSERVABLE", SuiviEquipeService.tendance(null, 80.0));
    }

    private RetourTerrain visite(Long id, String visiteur, LocalDate date, StatutVisite statut, QualificationVisite qualif) {
        RetourTerrain r = new RetourTerrain();
        r.setId(id);
        r.setMedecin(medecin);
        r.setVisiteur(visiteur);
        r.setDateVisite(date);
        r.setStatutVisite(statut);
        r.setQualification(qualif);
        r.setTypeVisite(TypeVisite.FIDELISATION);
        return r;
    }

    private ExtractionDonnees dossier(LocalDate date, int prix, int analyses) {
        ExtractionDonnees d = new ExtractionDonnees();
        d.setDateReception(date);
        d.setPrixAPayer(prix);
        d.setNombreAnalyses(analyses);
        d.setMedecin(medecin);
        return d;
    }
}
