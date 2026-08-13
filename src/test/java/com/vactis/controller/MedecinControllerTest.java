package com.vactis.controller;

import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.RisqueUrgence;
import com.vactis.model.medecin.StatutPilotage;
import com.vactis.service.MedecinService;
import com.vactis.service.RetourTerrainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MedecinControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MedecinService medecinService;

    @Mock
    private RetourTerrainService retourTerrainService;

    @InjectMocks
    private MedecinController medecinController;

    private Medecin mockMedecin;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(medecinController).build();

        mockMedecin = new Medecin();
        mockMedecin.setCodeMedecin("MED001");
        mockMedecin.setNom("Alami");
        mockMedecin.setPrenom("Ahmed");
        mockMedecin.setSpecialite("Cardiologie");
        mockMedecin.setOrganisme("CHU Marrakech");
        mockMedecin.setStatut("NOUVEAU");
        mockMedecin.setStatutPilotage(StatutPilotage.ACTIF);
        mockMedecin.setRisqueUrgence(RisqueUrgence.FAIBLE);
    }

    @Test
    @DisplayName("Devrait retourner un médecin par son code unique (GET /api/medecins/code/{code})")
    void testGetMedecinByCode_Success() throws Exception {
        // Arrange
        when(medecinService.findByCodeMedecin("MED001")).thenReturn(mockMedecin);

        // Act & Assert
        mockMvc.perform(get("/api/medecins/code/MED001")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeMedecin").value("MED001"))
                .andExpect(jsonPath("$.nom").value("Alami"))
                .andExpect(jsonPath("$.prenom").value("Ahmed"))
                .andExpect(jsonPath("$.specialite").value("Cardiologie"));

        verify(medecinService, times(1)).findByCodeMedecin("MED001");
    }

    @Test
    @DisplayName("Devrait retourner un médecin par son ID (GET /api/medecins/{id})")
    void testGetMedecinById_Success() throws Exception {
        // Arrange
        when(medecinService.findById(1L)).thenReturn(mockMedecin);

        // Act & Assert
        mockMvc.perform(get("/api/medecins/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeMedecin").value("MED001"));

        verify(medecinService, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Devrait déclencher la synchronisation depuis l'Excel (POST /api/medecins/sync)")
    void testSyncFromDataFictif_Success() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/medecins/sync")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(medecinService, times(1)).syncMedecinsFromDataFictif();
    }

    @Test
    @DisplayName("Devrait mettre à jour la note_input d'un médecin (PATCH /api/medecins/{id}/note-input)")
    void testUpdateNoteInput_Success() throws Exception {
        // Arrange
        mockMedecin.setNoteInput(4.5);
        when(medecinService.updateNoteInput(eq(1L), eq(4.5))).thenReturn(mockMedecin);

        String jsonBody = "{\"noteInput\": 4.5}";

        // Act & Assert
        mockMvc.perform(patch("/api/medecins/1/note-input")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteInput").value(4.5));

        verify(medecinService, times(1)).updateNoteInput(1L, 4.5);
    }

    @Test
    @DisplayName("Devrait retourner la liste des retours terrain d'un médecin (GET /api/medecins/{id}/retours-terrain)")
    void testGetRetoursTerrain_Success() throws Exception {
        // Arrange
        RetourTerrain retour = new RetourTerrain();
        retour.setNomMedecin("Alami Ahmed");
        retour.setNote(4.0);

        when(retourTerrainService.getRetoursTerrainByMedecin(1L)).thenReturn(List.of(retour));

        // Act & Assert
        mockMvc.perform(get("/api/medecins/1/retours-terrain")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nomMedecin").value("Alami Ahmed"));

        verify(retourTerrainService, times(1)).getRetoursTerrainByMedecin(1L);
    }
}
