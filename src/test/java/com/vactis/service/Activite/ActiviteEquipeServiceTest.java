package com.vactis.service.Activite;

import com.vactis.dto.activite.EquipeCommercialeResponse;
import com.vactis.dto.activite.EquipeCommercialeResponse.LigneCommercial;
import com.vactis.dto.activite.EquipeCommercialeResponse.LigneVisite;
import com.vactis.dto.activite.EquipeCommercialeResponse.Synthese;
import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.StatutVisite;
import com.vactis.model.medecin.TypeVisite;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.RetourTerrainRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActiviteEquipeServiceTest {

    private static final YearMonth OCTOBRE = YearMonth.of(2025, 10);
    private static final YearMonth NOVEMBRE = YearMonth.of(2025, 11);
    private static final LocalDate DEBUT = OCTOBRE.atDay(1);
    private static final LocalDate FIN = OCTOBRE.atEndOfMonth();

    @Mock private RetourTerrainRepository retourTerrainRepository;
    @Mock private ActionRepository actionRepository;
    @Mock private ExtractionDonneesRepository extractionDonneesRepository;
    @Mock private ActivitePortefeuilleService portefeuilleService;

    @InjectMocks private ActiviteEquipeService service;

    private Medecin connu1;
    private Medecin connu2;
    private Medecin prospect;
    private Action actionVisitee;
    private Action actionSansVisite;

    @BeforeEach
    void setUp() {
        connu1 = medecin(1L, "ALAOUI");
        connu2 = medecin(2L, "BENNANI");
        prospect = medecin(3L, "PROSPECT");

        actionVisitee = action(10L, connu1, "Commercial 1", "2025-10");
        actionSansVisite = action(11L, connu2, "Commercial 1", "2025-10");
        Action ancienCycle = action(12L, connu2, "Commercial 2", "2025-09");
        Action realiseeSansRetour = action(13L, connu1, "Commercial 2", "2025-10");
        realiseeSansRetour.setEtatAction(EtatAction.REALISEE);

        // Visite VACTIS réalisée par un autre commercial que le prioritaire, avec un appui
        RetourTerrain relais = visite(100L, connu1, "commercial 2", LocalDate.of(2025, 10, 5), QualificationVisite.FAVORABLE);
        relais.setAction(actionVisitee);
        relais.setCommercialAppui("Commercial 1");
        RetourTerrain libre = visite(101L, connu2, "Commercial 1", LocalDate.of(2025, 10, 10), QualificationVisite.NEUTRE);
        RetourTerrain service = visite(102L, connu2, "Commercial 1", LocalDate.of(2025, 10, 11), QualificationVisite.NON_RENSEIGNE);
        service.setTypeVisite(TypeVisite.SERVICE);
        RetourTerrain versProspect = visite(103L, prospect, "Commercial 1", LocalDate.of(2025, 10, 12), QualificationVisite.NON_RENSEIGNE);
        RetourTerrain manquee = visite(104L, connu2, "Commercial 2", LocalDate.of(2025, 10, 13), null);
        manquee.setStatutVisite(StatutVisite.NON_REALISEE);
        RetourTerrain horsPeriode = visite(105L, connu2, "Commercial 1", LocalDate.of(2025, 8, 1), QualificationVisite.RECLAMATION);

        when(retourTerrainRepository.findAllWithFetch())
                .thenReturn(List.of(relais, libre, service, versProspect, manquee, horsPeriode));
        when(actionRepository.findAll()).thenReturn(List.of(actionVisitee, actionSansVisite, ancienCycle, realiseeSansRetour));
        when(extractionDonneesRepository.findDistinctMedecinIdsWithActivity()).thenReturn(List.of(1L, 2L));
        when(extractionDonneesRepository.findAllDatesDescending())
                .thenReturn(List.of(LocalDate.of(2025, 11, 20), LocalDate.of(2025, 10, 3)));
        when(extractionDonneesRepository.findMedecinIdsWithActivityInRange(any(), any())).thenReturn(List.of(1L));
        when(portefeuilleService.buildStatutMapForMonth(eq(OCTOBRE), any())).thenReturn(Map.of(1L, "surveillance"));
        when(portefeuilleService.buildStatutMapForMonth(eq(NOVEMBRE), any())).thenReturn(Map.of(1L, "actif_stable"));
    }

    @Test
    void syntheseEquipeSepareVactisHorsVactisServicesEtProspects() {
        EquipeCommercialeResponse res = service.getEquipeCommerciale(DEBUT, FIN, null);
        Synthese s = res.getSynthese();

        assertEquals(5, s.getPassagesSaisis());
        assertEquals(4, s.getPassagesRealises());
        assertEquals(80.0, s.getTauxRealisation());
        assertEquals(1, s.getVisitesVactis());
        assertEquals(3, s.getHorsVactis());
        assertEquals(1, s.getPassagesService());
        assertEquals(3, s.getEchangesCommerciaux());
        assertEquals(2, s.getMedecinsEnEchange(), "médecins connus, services exclus");
        assertEquals(1, s.getProspects());
        assertEquals(1, s.getRelais());
        assertEquals(1, s.getParticipationsAppui());
        assertEquals(List.of("2025-10", "2025-08"), res.getMoisDisponibles());
    }

    @Test
    void qualificationsExcluentLesPassagesDeServiceEtEvolutionSuitLaRegleR4() {
        EquipeCommercialeResponse res = service.getEquipeCommerciale(DEBUT, FIN, null);

        assertEquals(1L, res.getQualifications().get("FAVORABLE"));
        assertEquals(1L, res.getQualifications().get("NEUTRE"));
        assertEquals(1L, res.getQualifications().get("NON_RENSEIGNE"), "le passage de service n'est pas compté");
        assertEquals(1L, res.getEvolutions().get(EvolutionStatutPolicy.FAVORABLE));
        assertEquals(0L, res.getEvolutions().get(EvolutionStatutPolicy.NON_OBSERVABLE));

        LigneVisite vactis = res.getVisites().stream().filter(LigneVisite::isVactis).findFirst().orElseThrow();
        assertEquals("surveillance", vactis.getStatutAvant());
        assertEquals("actif_stable", vactis.getStatutApres());
        assertTrue(vactis.isRelais());
        assertEquals("Commercial 1", vactis.getCommercialPrioritaire());
        LigneVisite horsVactis = res.getVisites().stream().filter(v -> v.getRetourTerrainId() == 101L).findFirst().orElseThrow();
        assertNull(horsVactis.getEvolution(), "pas d'évolution calculée hors VACTIS");
    }

    @Test
    void tableauParCommercialCompteAppuisRelaisEtRetoursQualifies() {
        List<LigneCommercial> lignes = service.getEquipeCommerciale(DEBUT, FIN, null).getParCommercial();

        assertEquals(2, lignes.size(), "les commerciaux sont regroupés sans tenir compte de la casse");
        LigneCommercial c1 = lignes.get(0);
        assertEquals("Commercial 1", c1.getCommercial());
        assertEquals(3, c1.getPassagesPrincipaux());
        assertEquals(1, c1.getParticipationsAppui());
        assertEquals(0, c1.getVisitesVactis());
        assertEquals(1, c1.getServices());
        assertEquals(2, c1.getEchanges());
        assertEquals(1, c1.getRetoursQualifies());
        assertEquals(1, c1.getProspects());

        LigneCommercial c2 = lignes.get(1);
        assertEquals(2, c2.getPassagesSaisis());
        assertEquals(1, c2.getPassagesPrincipaux());
        assertEquals(1, c2.getRelais());
        assertEquals(1, c2.getProgressions());
        assertEquals(1, c2.getComparables());
        assertEquals(0, c2.getBaisses());
    }

    @Test
    void decisionsListentLesActionsDuClasseurActifSansVisite() {
        EquipeCommercialeResponse.Decisions d = service.getEquipeCommerciale(DEBUT, FIN, null).getDecisions();

        assertEquals("2025-10", d.getCycleActif());
        assertEquals(1, d.getActionsSansVisite().size());
        assertEquals(11L, d.getActionsSansVisite().get(0).getActionId());
        assertEquals(1, d.getRetoursACompleter());
        assertEquals(0, d.getRefusOuReclamations());
    }

    @Test
    void vueIndividuelleNeGardeQueLesPassagesPrincipauxEtSesActions() {
        EquipeCommercialeResponse res = service.getEquipeCommerciale(DEBUT, FIN, "  COMMERCIAL 1 ");

        assertEquals("COMMERCIAL 1", res.getCommercial());
        assertEquals(3, res.getSynthese().getPassagesSaisis());
        assertEquals(1, res.getSynthese().getParticipationsAppui());
        assertEquals(0, res.getSynthese().getVisitesVactis());
        assertEquals(3, res.getVisites().size());
        assertEquals(1, res.getDecisions().getActionsSansVisite().size());

        EquipeCommercialeResponse autre = service.getEquipeCommerciale(DEBUT, FIN, "Commercial 2");
        assertTrue(autre.getDecisions().getActionsSansVisite().isEmpty());
    }

    @Test
    void evolutionNonObservableQuandLeMoisSuivantNestPasDansLExtraction() {
        when(extractionDonneesRepository.findAllDatesDescending()).thenReturn(List.of(LocalDate.of(2025, 10, 3)));

        EquipeCommercialeResponse res = service.getEquipeCommerciale(DEBUT, FIN, null);

        assertEquals(1L, res.getEvolutions().get(EvolutionStatutPolicy.NON_OBSERVABLE));
        assertEquals(0, res.getParCommercial().get(1).getComparables());
        verify(portefeuilleService, never()).buildStatutMapForMonth(eq(NOVEMBRE), any());
    }

    @Test
    void sansPeriodeToutesLesVisitesSontLues() {
        EquipeCommercialeResponse res = service.getEquipeCommerciale(null, null, null);

        assertEquals(6, res.getSynthese().getPassagesSaisis());
        assertEquals(1, res.getDecisions().getRefusOuReclamations());
        assertFalse(res.getCommerciaux().isEmpty());
    }

    private static Medecin medecin(Long id, String nom) {
        Medecin m = new Medecin();
        m.setId(id);
        m.setNom(nom);
        m.setPrenom("Test");
        return m;
    }

    private static Action action(Long id, Medecin m, String commercial, String cycle) {
        Action a = new Action();
        a.setId(id);
        a.setMedecin(m);
        a.setCommercial(commercial);
        a.setCycleMensuel(cycle);
        a.setActionRecommandee("visite_reconnaissance");
        return a;
    }

    private static RetourTerrain visite(Long id, Medecin m, String visiteur, LocalDate date, QualificationVisite q) {
        RetourTerrain r = new RetourTerrain();
        r.setId(id);
        r.setMedecin(m);
        r.setVisiteur(visiteur);
        r.setDateVisite(date);
        r.setStatutVisite(StatutVisite.REALISEE);
        r.setQualification(q);
        return r;
    }
}
