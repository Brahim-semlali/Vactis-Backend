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
