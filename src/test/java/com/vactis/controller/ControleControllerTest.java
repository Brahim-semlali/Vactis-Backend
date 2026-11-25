package com.vactis.controller;

import com.vactis.model.Controle.Controle;
import com.vactis.model.Controle.TypeControle;
import com.vactis.service.ControleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Routes REST de la table Contrôle (sans la chaîne de sécurité, testée dans SecurityRulesTest). */
class ControleControllerTest {

    private final ControleService controleService = mock(ControleService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ControleController(controleService)).build();
    }

    private static Controle regle(long id, TypeControle type, String etat, Long min, Long max, Long valeur) {
        Controle c = new Controle();
        c.setIdControle(id);
        c.setType(type);
        c.setEtat(etat);
        c.setMinCA(min);
        c.setMaxCA(max);
        c.setValeur(valeur);
        c.setActif(true);
        return c;
    }

    @Test
    void listeLesReglesDUnNouveauType() throws Exception {
        when(controleService.findByType(TypeControle.RUPTURE)).thenReturn(List.of(
                regle(1, TypeControle.RUPTURE, "SURVEILLANCE", 150L, 200L, null)));

        mockMvc.perform(get("/api/controle/type/RUPTURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].etat").value("SURVEILLANCE"))
                .andExpect(jsonPath("$[0].minCA").value(150))
                .andExpect(jsonPath("$[0].maxCA").value(200));
    }

    @Test
    void typeInconnuRenvoie400() throws Exception {
        mockMvc.perform(get("/api/controle/type/INEXISTANT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creeUnDelaiDActionAvecSaValeur() throws Exception {
        when(controleService.create(any(Controle.class)))
                .thenReturn(regle(7, TypeControle.DELAI_ACTION, "CRITIQUE", null, null, 5L));

        mockMvc.perform(post("/api/controle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DELAI_ACTION\",\"etat\":\"CRITIQUE\",\"valeur\":5,\"actif\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valeur").value(5));

        verify(controleService).create(argThat(c -> c.getType() == TypeControle.DELAI_ACTION
                && "CRITIQUE".equals(c.getEtat()) && Long.valueOf(5L).equals(c.getValeur())));
    }

    @Test
    void regleInvalideRenvoieLeMessageMetier() throws Exception {
        when(controleService.create(any(Controle.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce palier chevauche le palier CRITIQUE"));

        mockMvc.perform(post("/api/controle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"RUPTURE\",\"etat\":\"SURVEILLANCE\",\"minCA\":150,\"maxCA\":250}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void metAJourUneRegle() throws Exception {
        when(controleService.update(any(Long.class), any(Controle.class)))
                .thenReturn(regle(3, TypeControle.URGENCE, "ELEVE", 45L, 75L, null));

        mockMvc.perform(put("/api/controle/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"URGENCE\",\"etat\":\"ELEVE\",\"minCA\":45,\"maxCA\":75}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minCA").value(45));

        verify(controleService).update(any(Long.class), argThat(c -> Long.valueOf(45L).equals(c.getMinCA())));
    }

    @Test
    void reinitialiseUnTypeAuxReglesParDefaut() throws Exception {
        when(controleService.reinitialiser(TypeControle.FIABILITE)).thenReturn(List.of(
                regle(1, TypeControle.FIABILITE, "NON_FIABLE", 0L, 2L, null),
                regle(2, TypeControle.FIABILITE, "PARTIEL", 2L, 3L, null),
                regle(3, TypeControle.FIABILITE, "FIABLE", 3L, 1200L, null)));

        mockMvc.perform(post("/api/controle/type/FIABILITE/reinitialiser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].etat").value("FIABLE"));

        verify(controleService).reinitialiser(TypeControle.FIABILITE);
    }

    @Test
    void supprimeUneRegle() throws Exception {
        mockMvc.perform(delete("/api/controle/9"))
                .andExpect(status().isNoContent());

        verify(controleService).delete(9L);
    }
}
