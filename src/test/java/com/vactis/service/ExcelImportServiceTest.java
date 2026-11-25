package com.vactis.service;

import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.action.UrgenceAction;
import com.vactis.model.medecin.Medecin;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.MedecinRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Génération des actions du cycle après un import : une action par statut éligible, aucune pour les
 * inactifs, irréguliers et pseudo-médecins, et suppression préalable des actions d'import non travaillées.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExcelImportServiceTest {

    private static final LocalDate REFERENCE = LocalDate.of(2026, 11, 1);

    @Mock private ActionRepository actionRepository;
    @Mock private MedecinRepository medecinRepository;

    @InjectMocks private ExcelImportService service;

    private static Medecin medecin(long id, String statut) {
        Medecin m = new Medecin();
        m.setId(id);
        m.setNom("M" + id);
        m.setStatut(statut);
        m.setSegment("B");
        m.setOrganisme("Clinique " + id);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Action> generer(List<Medecin> medecins) {
        service.regenerateImportedDataActions(medecins, REFERENCE);
        ArgumentCaptor<List<Action>> captor = ArgumentCaptor.forClass(List.class);
        verify(actionRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void leStatutDeLImportSeCalculeSurLaVariationDuCaEtNonSurLeMontant() {
        // 25 000 ce mois contre 20 000 le mois précédent : +25 % (et non 25 000)
        assertEquals(25L, ExcelImportService.variationCaPourcent(25_000, 20_000));
        assertEquals(-40L, ExcelImportService.variationCaPourcent(12_000, 20_000));
        assertEquals(-100L, ExcelImportService.variationCaPourcent(0, 20_000));
        assertEquals(-33L, ExcelImportService.variationCaPourcent(2, 3));
        // Pas de CA le mois précédent : variation non calculable -> statut NOUVEAU
        assertNull(ExcelImportService.variationCaPourcent(25_000, 0));
    }

    @Test
    void lesActionsNonTravailleesDeLImportPrecedentSontRemplacees() {
        generer(List.of(medecin(1, "ACTIF")));

        InOrder ordre = inOrder(actionRepository);
        ordre.verify(actionRepository).deleteUntouchedImportGeneratedActions();
        ordre.verify(actionRepository).saveAll(any());
        ordre.verify(actionRepository).flush();
    }

    @Test
    void uneActionParStatutEligibleSelonLaSection1() {
        List<Action> actions = generer(List.of(
                medecin(1, "PROGRESSION"), medecin(2, "actif_stable"), medecin(3, "SURVEILLANCE"),
                medecin(4, "RETENTION"), medecin(5, "ONBOARDING"), medecin(6, "SILENCE_CRITIQUE")));

        Map<Long, Action> parMedecin = actions.stream().collect(Collectors.toMap(a -> a.getMedecin().getId(), Function.identity()));
        assertEquals(6, actions.size());
        assertEquals("visite suivi progression", parMedecin.get(1L).getActionRecommandee());
        assertEquals(UrgenceAction.FAIBLE, parMedecin.get(1L).getUrgence());
        assertEquals(REFERENCE.plusDays(7), parMedecin.get(1L).getDateVisite());
        assertEquals("visite suivi régulier", parMedecin.get(2L).getActionRecommandee());
        assertEquals("visite diagnostic surveillance", parMedecin.get(3L).getActionRecommandee());
        assertEquals(UrgenceAction.ELEVE, parMedecin.get(3L).getUrgence());
        assertEquals("visite diagnostic", parMedecin.get(4L).getActionRecommandee());
        assertEquals(REFERENCE.plusDays(2), parMedecin.get(4L).getDateVisite());
        assertEquals("visite onboarding", parMedecin.get(5L).getActionRecommandee());
        assertEquals("visite diagnostic", parMedecin.get(6L).getActionRecommandee());

        Action a = parMedecin.get(3L);
        assertEquals("2026-11", a.getCycleMensuel());
        assertEquals(EtatAction.PLANIFIEE, a.getEtatAction());
        assertEquals("IMPORT_AUTO", a.getSourceRecommandation());
        assertEquals("B", a.getSegment());
        assertEquals("Clinique 3", a.getLieuOrganisme());
    }

    @Test
    void aucuneActionAutomatiquePourInactifsIrreguliersEtPseudoMedecins() {
        Medecin pseudo = medecin(9, "ACTIF");
        when(medecinRepository.isPseudoMedecin(any())).thenAnswer(i -> i.getArgument(0) == pseudo);

        List<Action> actions = generer(List.of(medecin(1, "INACTIF"), medecin(2, "ACTIVITE_IRREGULIERE"),
                medecin(3, "A_REACTIVER"), pseudo, medecin(4, "ACTIF")));

        assertEquals(List.of(4L), actions.stream().map(a -> a.getMedecin().getId()).toList());
    }

    @Test
    void lesActionsSontRepartiesEntreLesCommerciauxEnAlternance() {
        List<Action> actions = generer(List.of(medecin(1, "ACTIF"), medecin(2, "ACTIF"), medecin(3, "ACTIF")));

        List<String> commerciaux = actions.stream().map(Action::getCommercial).toList();
        assertEquals(commerciaux.get(0), commerciaux.get(2));
        assertTrue(!commerciaux.get(0).equals(commerciaux.get(1)));
    }
}
