package com.vactis.service;

import com.vactis.model.Controle.Controle;
import com.vactis.model.Controle.TypeControle;
import com.vactis.repository.ControleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControleServiceTest {

    @Mock
    private ControleRepository controleRepository;

    @InjectMocks
    private ControleService controleService;

    @Test
    void determineEtatUsesFallbackStatutThresholds() {
        when(controleRepository.findByTypeAndActifTrue(TypeControle.STATUT)).thenReturn(List.of());

        assertEquals("PROGRESSION", controleService.determinerEtat(TypeControle.STATUT, 21L));
        assertEquals("ACTIF_STABLE", controleService.determinerEtat(TypeControle.STATUT, -10L));
        assertEquals("SURVEILLANCE", controleService.determinerEtat(TypeControle.STATUT, -40L));
        assertEquals("RETENTION", controleService.determinerEtat(TypeControle.STATUT, -70L));
        assertEquals("SILENCE_CRITIQUE", controleService.determinerEtat(TypeControle.STATUT, -71L));
    }

    @Test
    void determineEtatUsesConfiguredRuleBeforeFallback() {
        Controle rule = rule(TypeControle.STATUT, "PERSONNALISE", -20L, 10L);
        when(controleRepository.findByTypeAndActifTrue(TypeControle.STATUT)).thenReturn(List.of(rule));

        assertEquals("PERSONNALISE", controleService.determinerEtat(TypeControle.STATUT, 0L));
    }

    @Test
    void determineEtatParScoreUsesSegmentFallback() {
        when(controleRepository.findByTypeAndActifTrue(TypeControle.SEGEMENTS)).thenReturn(List.of());

        assertEquals("A", controleService.determinerEtatParScore(TypeControle.SEGEMENTS, 75.0));
        assertEquals("B", controleService.determinerEtatParScore(TypeControle.SEGEMENTS, 60.0));
        assertEquals("C", controleService.determinerEtatParScore(TypeControle.SEGEMENTS, 45.0));
        assertEquals("D", controleService.determinerEtatParScore(TypeControle.SEGEMENTS, 44.9));
    }

    @Test
    void createRejectsInvertedRange() {
        Controle invalid = rule(TypeControle.STATUT, "TEST", 100L, 50L);

        assertThrows(ResponseStatusException.class, () -> controleService.create(invalid));
        verify(controleRepository, never()).save(any());
    }

    @Test
    void deleteRejectsUnknownRule() {
        when(controleRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> controleService.delete(99L));
        verify(controleRepository, never()).deleteById(anyLong());
    }

    @Test
    void createRejectsUnknownEtatForPalierTypes() {
        Controle invalid = rule(TypeControle.URGENCE, "TRES_URGENT", 75L, 100L);

        assertThrows(ResponseStatusException.class, () -> controleService.create(invalid));
        verify(controleRepository, never()).save(any());
    }

    @Test
    void createRejectsOverlappingPalier() {
        Controle existant = rule(TypeControle.RUPTURE, "SURVEILLANCE", 150L, 200L);
        existant.setIdControle(1L);
        when(controleRepository.findByTypeAndActifTrue(TypeControle.RUPTURE)).thenReturn(List.of(existant));

        Controle chevauche = rule(TypeControle.RUPTURE, "CRITIQUE", 180L, 100000L);
        assertThrows(ResponseStatusException.class, () -> controleService.create(chevauche));

        // Bornes qui se touchent ([150 ; 200[ puis [200 ; ...[) : accepté
        Controle contigu = rule(TypeControle.RUPTURE, "critique", 200L, 100000L);
        when(controleRepository.save(contigu)).thenReturn(contigu);
        controleService.create(contigu);
        assertEquals("CRITIQUE", contigu.getEtat());
    }

    @Test
    void delaiActionRequiresPositiveValueAndUniqueEtat() {
        Controle sansValeur = rule(TypeControle.DELAI_ACTION, "CRITIQUE", null, null);
        assertThrows(ResponseStatusException.class, () -> controleService.create(sansValeur));

        Controle existant = rule(TypeControle.DELAI_ACTION, "CRITIQUE", null, null);
        existant.setIdControle(1L);
        existant.setValeur(7L);
        when(controleRepository.findByTypeAndActifTrue(TypeControle.DELAI_ACTION)).thenReturn(List.of(existant));

        Controle doublon = rule(TypeControle.DELAI_ACTION, "CRITIQUE", null, null);
        doublon.setValeur(5L);
        assertThrows(ResponseStatusException.class, () -> controleService.create(doublon));
        verify(controleRepository, never()).save(any());
    }

    @Test
    void initialiserReglesParDefautRemplitSeulementLesTypesVides() {
        when(controleRepository.findByTypeOrderByMinCAAsc(any())).thenReturn(List.of());
        when(controleRepository.findByTypeOrderByMinCAAsc(TypeControle.URGENCE))
                .thenReturn(List.of(rule(TypeControle.URGENCE, "FAIBLE", 0L, 25L)));

        int crees = controleService.initialiserReglesParDefaut();

        // Rupture 3 + Fiabilité 3 + Délai d'action 4 ; Urgence déjà configurée
        assertEquals(10, crees);
        verify(controleRepository, times(3)).saveAll(anyList());
    }

    @Test
    void paliersUtiliseLesReglesActivesPuisLesDefauts() {
        when(controleRepository.findByTypeAndActifTrue(TypeControle.URGENCE))
                .thenReturn(List.of(rule(TypeControle.URGENCE, "URGENT", 60L, 100L),
                        rule(TypeControle.URGENCE, "FAIBLE", 0L, 60L)));
        when(controleRepository.findByTypeAndActifTrue(TypeControle.RUPTURE)).thenReturn(List.of());

        assertEquals("URGENT", controleService.paliers(TypeControle.URGENCE).etatPour(65));
        assertEquals("FAIBLE", controleService.paliers(TypeControle.URGENCE).etatPour(59.9));
        // Aucune règle active : règles VACTIS par défaut
        assertEquals("SURVEILLANCE", controleService.paliers(TypeControle.RUPTURE).etatPour(150));
    }

    @Test
    void reinitialiserRemplaceLesReglesParLesDefauts() {
        Controle ancienne = rule(TypeControle.FIABILITE, "FIABLE", 5L, 100L);
        when(controleRepository.findByTypeOrderByMinCAAsc(TypeControle.FIABILITE)).thenReturn(List.of(ancienne));

        controleService.reinitialiser(TypeControle.FIABILITE);

        verify(controleRepository).deleteAll(List.of(ancienne));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<Controle>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(controleRepository).saveAll(captor.capture());
        assertEquals(List.of("NON_FIABLE", "PARTIEL", "FIABLE"), captor.getValue().stream().map(Controle::getEtat).toList());
        assertEquals(List.of(0L, 2L, 3L), captor.getValue().stream().map(Controle::getMinCA).toList());
    }

    @Test
    void reinitialiserRefuseLesTypesSansDefauts() {
        assertThrows(ResponseStatusException.class, () -> controleService.reinitialiser(TypeControle.STATUT));
        verify(controleRepository, never()).deleteAll(anyList());
    }

    @Test
    void updateConserveLeDelaiEtNormaliseLEtat() {
        Controle existant = rule(TypeControle.DELAI_ACTION, "ELEVEE", null, null);
        existant.setIdControle(4L);
        existant.setValeur(15L);
        when(controleRepository.findById(4L)).thenReturn(java.util.Optional.of(existant));
        when(controleRepository.findByTypeAndActifTrue(TypeControle.DELAI_ACTION)).thenReturn(List.of(existant));
        when(controleRepository.save(existant)).thenReturn(existant);

        Controle payload = rule(TypeControle.DELAI_ACTION, "Élevée", 1L, 2L);
        payload.setValeur(10L);
        Controle maj = controleService.update(4L, payload);

        assertEquals("ELEVEE", maj.getEtat());
        assertEquals(10L, maj.getValeur());
        // Les bornes ne servent pas pour un délai
        assertEquals(null, maj.getMinCA());
        assertEquals(null, maj.getMaxCA());
    }

    @Test
    void unDelaiInactifPeutDoublerUnDelaiActif() {
        Controle existant = rule(TypeControle.DELAI_ACTION, "CRITIQUE", null, null);
        existant.setIdControle(1L);
        existant.setValeur(7L);
        Controle brouillon = rule(TypeControle.DELAI_ACTION, "CRITIQUE", null, null);
        brouillon.setValeur(5L);
        brouillon.setActif(false);
        when(controleRepository.save(brouillon)).thenReturn(brouillon);

        controleService.create(brouillon);

        verify(controleRepository).save(brouillon);
    }

    @Test
    void unPalierDeLargeurNulleEstRefuse() {
        Controle vide = rule(TypeControle.URGENCE, "MOYEN", 30L, 30L);

        assertThrows(ResponseStatusException.class, () -> controleService.create(vide));
        verify(controleRepository, never()).save(any());
    }

    @Test
    void etatsActifsDesNouveauxTypesRetombentSurLesDefauts() {
        when(controleRepository.findByTypeAndActifTrue(TypeControle.RUPTURE)).thenReturn(List.of());
        when(controleRepository.findByTypeAndActifTrue(TypeControle.DELAI_ACTION)).thenReturn(List.of());

        assertEquals(List.of("AUCUNE", "SURVEILLANCE", "CRITIQUE"), controleService.getEtatsActifs(TypeControle.RUPTURE));
        assertEquals(List.of("SILENCE_STRATEGIQUE", "CRITIQUE", "ELEVEE", "NORMALE"),
                controleService.getEtatsActifs(TypeControle.DELAI_ACTION));
    }

    private Controle rule(TypeControle type, String etat, Long min, Long max) {
        Controle rule = new Controle();
        rule.setType(type);
        rule.setEtat(etat);
        rule.setMinCA(min);
        rule.setMaxCA(max);
        rule.setActif(true);
        return rule;
    }
}
