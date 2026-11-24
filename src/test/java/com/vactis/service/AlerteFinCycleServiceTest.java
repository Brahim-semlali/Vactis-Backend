package com.vactis.service;

import com.vactis.dto.alerte.AlertesFinCycleResponse;
import com.vactis.dto.alerte.AlertesFinCycleResponse.Alerte;
import com.vactis.dto.alerte.AlertesFinCycleResponse.LigneCommercial;
import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.auth.Users;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.StatutVisite;
import com.vactis.model.medecin.TypeVisite;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ReclamationRepository;
import com.vactis.repository.RetourTerrainRepository;
import com.vactis.repository.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlerteFinCycleServiceTest {

    private static final LocalDate J_MOINS_6 = LocalDate.of(2026, 11, 24);

    @Mock private ActionRepository actionRepository;
    @Mock private RetourTerrainRepository retourTerrainRepository;
    @Mock private ReclamationRepository reclamationRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private AlerteFinCycleService service;

    private Medecin alaoui;
    private Medecin bennani;

    @BeforeEach
    void setUp() {
        alaoui = medecin(1L, "ALAOUI");
        bennani = medecin(2L, "BENNANI");

        Action visitee = action(10L, alaoui, "Commercial 1", "2026-11");
        Action nonVisitee = action(11L, bennani, "Commercial 1", "2026-11");
        Action realisee = action(12L, bennani, "Commercial 2", "2026-11");
        realisee.setEtatAction(EtatAction.REALISEE);
        Action autreCommercial = action(13L, alaoui, "Commercial 2", "2026-11");
        Action ancienCycle = action(14L, alaoui, "Commercial 1", "2026-10");

        RetourTerrain qualifie = retour(100L, alaoui, "Commercial 1", LocalDate.of(2026, 11, 5), QualificationVisite.FAVORABLE);
        qualifie.setAction(visitee);
        RetourTerrain aQualifier = retour(101L, bennani, "commercial 1", LocalDate.of(2026, 11, 8), QualificationVisite.NON_RENSEIGNE);
        RetourTerrain service = retour(102L, bennani, "Commercial 1", LocalDate.of(2026, 11, 9), QualificationVisite.NON_RENSEIGNE);
        service.setTypeVisite(TypeVisite.SERVICE);
        RetourTerrain nonRealise = retour(103L, bennani, "Commercial 1", LocalDate.of(2026, 11, 10), null);
        nonRealise.setStatutVisite(StatutVisite.NON_REALISEE);
        RetourTerrain moisPrecedent = retour(104L, bennani, "Commercial 1", LocalDate.of(2026, 10, 20), null);

        Reclamation ancienne = reclamation(200L, "Commercial 1", StatutReclamation.A_TRAITER, J_MOINS_6.minusDays(10));
        ancienne.setDateEcheance(J_MOINS_6.minusDays(1));
        Reclamation recente = reclamation(201L, "Commercial 1", StatutReclamation.EN_COURS, J_MOINS_6.minusDays(2));
        Reclamation traitee = reclamation(202L, "Commercial 1", StatutReclamation.TRAITEE, J_MOINS_6.minusDays(20));

        when(actionRepository.findAll()).thenReturn(List.of(visitee, nonVisitee, realisee, autreCommercial, ancienCycle));
        when(retourTerrainRepository.findAllWithFetch()).thenReturn(List.of(qualifie, aQualifier, service, nonRealise, moisPrecedent));
        when(reclamationRepository.findAll()).thenReturn(List.of(ancienne, recente, traitee));
        when(userRepository.findByUsername("Commercial 1")).thenReturn(Optional.empty());
    }

    @Test
    void unCommercialNeVoitQueSesAlertesDuCycleActif() {
        AlertesFinCycleResponse reponse = service.getAlertes("Commercial 1", false, J_MOINS_6);

        assertEquals("2026-11", reponse.getCycle());
        assertEquals(LocalDate.of(2026, 11, 30), reponse.getDateCloture());
        assertEquals(6L, reponse.getJoursRestants());
        assertTrue(reponse.isFenetreActive());
        assertFalse(reponse.isUrgent());
        assertFalse(reponse.isVueEquipe());
        assertTrue(reponse.getParCommercial().isEmpty());

        List<String> types = reponse.getAlertes().stream().map(a -> a.getType() + ":" + idDe(a)).toList();
        assertEquals(List.of("RECLAMATION_EN_ATTENTE:200", "ACTION_NON_VISITEE:11", "RETOUR_A_QUALIFIER:101"), types);
        Alerte reclamation = reponse.getAlertes().get(0);
        assertEquals("URGENT", reclamation.getGravite());
        assertEquals("Ouverte depuis 10 jours", reclamation.getDetail());
        assertEquals("ATTENTION", reponse.getAlertes().get(1).getGravite());
    }

    @Test
    void laDirectionVoitToutLEquipeEtLesRealisationsFaibles() {
        AlertesFinCycleResponse reponse = service.getAlertes("direction", true, J_MOINS_6);

        assertTrue(reponse.isVueEquipe());
        assertEquals(2, reponse.getParCommercial().size());
        LigneCommercial c1 = reponse.getParCommercial().get(0);
        assertEquals("Commercial 1", c1.getCommercial());
        assertEquals(2, c1.getActionsDuCycle());
        assertEquals(1, c1.getActionsVisitees());
        assertEquals(50.0, c1.getTauxRealisation());
        assertEquals(1, c1.getRetoursAQualifier());
        assertEquals(1, c1.getReclamationsEnAttente());
        LigneCommercial c2 = reponse.getParCommercial().get(1);
        assertEquals(1, c2.getActionsNonVisitees());
        assertEquals(50.0, c2.getTauxRealisation());

        assertEquals(2, reponse.getAlertes().stream().filter(a -> "ACTION_NON_VISITEE".equals(a.getType())).count());
        List<Alerte> faibles = reponse.getAlertes().stream().filter(a -> "REALISATION_FAIBLE".equals(a.getType())).toList();
        assertEquals(2, faibles.size());
        assertEquals("1 / 2 actions visitées (50 %)", faibles.get(0).getDetail());
    }

    @Test
    void lesAlertesPassentEnUrgentAJMoins3EtLaFenetreSOuvreAJMoins7() {
        AlertesFinCycleResponse urgent = service.getAlertes("Commercial 1", false, LocalDate.of(2026, 11, 27));
        assertTrue(urgent.isUrgent());
        assertTrue(urgent.getAlertes().stream().allMatch(a -> "URGENT".equals(a.getGravite())
                || "RECLAMATION_EN_ATTENTE".equals(a.getType())));

        AlertesFinCycleResponse tot = service.getAlertes("Commercial 1", false, LocalDate.of(2026, 11, 10));
        assertFalse(tot.isFenetreActive());
        assertEquals(20L, tot.getJoursRestants());
    }

    @Test
    void lUtilisateurEstReconnuParSonNomComplet() {
        Users user = new Users();
        user.setUsername("ahmed");
        user.setFirstName("Commercial");
        user.setLastName("1");
        when(userRepository.findByUsername("ahmed")).thenReturn(Optional.of(user));

        AlertesFinCycleResponse reponse = service.getAlertes("ahmed", false, J_MOINS_6);

        assertEquals(3, reponse.getAlertes().size());
    }

    @Test
    void sansCycleChargeAucuneAlerte() {
        when(actionRepository.findAll()).thenReturn(List.of());

        AlertesFinCycleResponse reponse = service.getAlertes("Commercial 1", false, J_MOINS_6);

        assertNull(reponse.getCycle());
        assertNull(reponse.getJoursRestants());
        assertTrue(reponse.getAlertes().isEmpty());
    }

    private static Long idDe(Alerte a) {
        if (a.getReclamationId() != null) return a.getReclamationId();
        if (a.getRetourTerrainId() != null) return a.getRetourTerrainId();
        return a.getActionId();
    }

    private static Medecin medecin(Long id, String nom) {
        Medecin m = new Medecin();
        m.setId(id);
        m.setNom(nom);
        return m;
    }

    private static Action action(Long id, Medecin medecin, String commercial, String cycle) {
        Action a = new Action();
        a.setId(id);
        a.setMedecin(medecin);
        a.setCommercial(commercial);
        a.setCycleMensuel(cycle);
        a.setActionRecommandee("VISITE_RECONNAISSANCE");
        return a;
    }

    private static RetourTerrain retour(Long id, Medecin medecin, String visiteur, LocalDate date, QualificationVisite q) {
        RetourTerrain r = new RetourTerrain();
        r.setId(id);
        r.setMedecin(medecin);
        r.setVisiteur(visiteur);
        r.setDateVisite(date);
        r.setStatutVisite(StatutVisite.REALISEE);
        r.setQualification(q);
        return r;
    }

    private static Reclamation reclamation(Long id, String commercial, StatutReclamation statut, LocalDate declaration) {
        Reclamation r = new Reclamation();
        r.setId(id);
        r.setCommercialDeclarant(commercial);
        r.setStatut(statut);
        r.setDateDeclaration(declaration);
        r.setMedecin(new Medecin());
        return r;
    }
}
