package com.vactis.service;

import com.vactis.dto.reclamation.ReclamationRequest;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.notification.Notification;
import com.vactis.model.reclamation.CategorieReclamation;
import com.vactis.model.reclamation.PrioriteReclamation;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.ReclamationRepository;
import com.vactis.repository.ReclamationHistoriqueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReclamationServiceTest {

    @Mock
    private ReclamationRepository reclamationRepository;

    @Mock
    private ReclamationHistoriqueRepository historiqueRepository;

    @Mock
    private MedecinRepository medecinRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ReclamationService reclamationService;

    private Medecin medecin;

    @BeforeEach
    void setUp() {
        medecin = new Medecin();
        medecin.setId(5L);
        medecin.setNom("Lemoine");
        // La propagation MANDATORY est ignorée dans les tests unitaires Mockito.
        // NotificationService est mocké avec lenient() car tous les tests n'appellent pas creerReclamation.
        lenient().when(notificationService.notifierDirectionPourReclamation(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Création d'un ticket réclamation avec statut A_TRAITER")
    void testCreerReclamation() {
        ReclamationRequest request = new ReclamationRequest();
        request.setMedecinId(5L);
        request.setDateVisite(LocalDate.now());
        request.setDescription("Délais trop longs sur les biopsies");
        request.setCategorie(CategorieReclamation.DELAIS_RESULTATS);
        request.setPriorite(PrioriteReclamation.HAUTE);

        when(medecinRepository.findById(5L)).thenReturn(Optional.of(medecin));
        when(reclamationRepository.save(any(Reclamation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reclamation rec = reclamationService.creerReclamation(request, "commercial1");

        assertNotNull(rec);
        assertEquals(StatutReclamation.A_TRAITER, rec.getStatut());
        assertEquals("commercial1", rec.getCommercialDeclarant());
        assertEquals(CategorieReclamation.DELAIS_RESULTATS, rec.getCategorie());
        assertEquals(PrioriteReclamation.HAUTE, rec.getPriorite());
        assertEquals(StatutReclamation.A_TRAITER, rec.getStatut());
        verify(historiqueRepository).save(any());
    }

    @Test
    @DisplayName("La création exige une date de visite valide")
    void creationRequiresVisitDate() {
        ReclamationRequest request = new ReclamationRequest();
        request.setMedecinId(5L);
        request.setDescription("Réclamation sans date de visite");

        assertThrows(IllegalArgumentException.class,
                () -> reclamationService.creerReclamation(request, "commercial1"));
        verifyNoInteractions(medecinRepository, reclamationRepository);
    }

    @Test
    @DisplayName("Mise à jour du statut vers RESOLUE avec horodatage de résolution")
    void testMettreAJourStatut_VersResolue() {
        Reclamation rec = new Reclamation();
        rec.setId(10L);
        rec.setStatut(StatutReclamation.A_TRAITER);

        when(reclamationRepository.findById(10L)).thenReturn(Optional.of(rec));
        when(reclamationRepository.save(any(Reclamation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reclamation miseAJour = reclamationService.mettreAJourStatut(10L, StatutReclamation.TRAITEE, "Protocole express activé", "Dr Responsable Qualité");

        assertEquals(StatutReclamation.TRAITEE, miseAJour.getStatut());
        assertEquals("Protocole express activé", miseAJour.getSolutionApportee());
        assertNotNull(miseAJour.getDateResolution());
        verify(historiqueRepository).save(any());
    }

    @Test
    @DisplayName("Statut 1 : A_TRAITER - mise à jour autorisée sans commentaire obligatoire")
    void testStatut_ATraiter() {
        Reclamation rec = new Reclamation();
        rec.setId(21L);
        rec.setStatut(StatutReclamation.EN_COURS);
        when(reclamationRepository.findById(21L)).thenReturn(Optional.of(rec));
        when(reclamationRepository.save(any(Reclamation.class))).thenAnswer(i -> i.getArgument(0));

        Reclamation r = reclamationService.mettreAJourStatut(21L, StatutReclamation.A_TRAITER, null, "Direction");
        assertEquals(StatutReclamation.A_TRAITER, r.getStatut());
    }

    @Test
    @DisplayName("Statut 2 : EN_COURS - mise à jour autorisée sans commentaire obligatoire")
    void testStatut_EnCours() {
        Reclamation rec = new Reclamation();
        rec.setId(22L);
        rec.setStatut(StatutReclamation.A_TRAITER);
        when(reclamationRepository.findById(22L)).thenReturn(Optional.of(rec));
        when(reclamationRepository.save(any(Reclamation.class))).thenAnswer(i -> i.getArgument(0));

        Reclamation r = reclamationService.mettreAJourStatut(22L, StatutReclamation.EN_COURS, null, "Direction");
        assertEquals(StatutReclamation.EN_COURS, r.getStatut());
    }

    @Test
    @DisplayName("Le passage En cours exige un responsable affecté")
    void enCoursRequiresAssignedResponsible() {
        Reclamation rec = new Reclamation();
        rec.setId(26L);
        rec.setStatut(StatutReclamation.A_TRAITER);
        when(reclamationRepository.findById(26L)).thenReturn(Optional.of(rec));

        assertThrows(IllegalArgumentException.class, () -> reclamationService.mettreAJourStatut(
                26L, StatutReclamation.EN_COURS, null, " "));
        verify(reclamationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Statut 3 : TRAITEE - commentaire de solution obligatoire et date de résolution renseignée")
    void testStatut_Traitee() {
        Reclamation rec = new Reclamation();
        rec.setId(23L);
        rec.setStatut(StatutReclamation.EN_COURS);
        when(reclamationRepository.findById(23L)).thenReturn(Optional.of(rec));
        when(reclamationRepository.save(any(Reclamation.class))).thenAnswer(i -> i.getArgument(0));

        // Avec solution valide
        Reclamation r = reclamationService.mettreAJourStatut(23L, StatutReclamation.TRAITEE, "Protocole express validé", "Dr Qualité");
        assertEquals(StatutReclamation.TRAITEE, r.getStatut());
        assertEquals("Protocole express validé", r.getSolutionApportee());
        assertNotNull(r.getDateResolution());

        // Sans solution -> exception obligatoire
        assertThrows(IllegalArgumentException.class, () -> reclamationService.mettreAJourStatut(
                23L, StatutReclamation.TRAITEE, "", "Dr Qualité"));
        assertThrows(IllegalArgumentException.class, () -> reclamationService.mettreAJourStatut(
                23L, StatutReclamation.TRAITEE, null, "Dr Qualité"));
    }

    @Test
    @DisplayName("Statut 4 : EN_ATTENTE_INFORMATION - mise à jour autorisée sans commentaire obligatoire")
    void testStatut_EnAttenteInformation() {
        Reclamation rec = new Reclamation();
        rec.setId(24L);
        rec.setStatut(StatutReclamation.EN_COURS);
        when(reclamationRepository.findById(24L)).thenReturn(Optional.of(rec));
        when(reclamationRepository.save(any(Reclamation.class))).thenAnswer(i -> i.getArgument(0));

        Reclamation r = reclamationService.mettreAJourStatut(24L, StatutReclamation.EN_ATTENTE_INFORMATION, null, "Direction", "Attente retour labo", "admin");
        assertEquals(StatutReclamation.EN_ATTENTE_INFORMATION, r.getStatut());
        assertEquals("Attente retour labo", r.getProchaineInstruction());
    }

    @Test
    @DisplayName("Statut 5 : CLOTURE_SANS_ACTION - commentaire de réponse obligatoire et date de résolution")
    void testStatut_ClotureSansAction() {
        Reclamation rec = new Reclamation();
        rec.setId(25L);
        rec.setStatut(StatutReclamation.EN_COURS);
        when(reclamationRepository.findById(25L)).thenReturn(Optional.of(rec));
        when(reclamationRepository.save(any(Reclamation.class))).thenAnswer(i -> i.getArgument(0));

        // Avec motif valide
        Reclamation r = reclamationService.mettreAJourStatut(25L, StatutReclamation.CLOTURE_SANS_ACTION, "Prescripteur non joignable après 3 relances", "Direction");
        assertEquals(StatutReclamation.CLOTURE_SANS_ACTION, r.getStatut());
        assertEquals("Prescripteur non joignable après 3 relances", r.getSolutionApportee());
        assertNotNull(r.getDateResolution());

        // Sans solution -> exception obligatoire
        assertThrows(IllegalArgumentException.class, () -> reclamationService.mettreAJourStatut(
                25L, StatutReclamation.CLOTURE_SANS_ACTION, "   ", "Direction"));
        assertThrows(IllegalArgumentException.class, () -> reclamationService.mettreAJourStatut(
                25L, StatutReclamation.CLOTURE_SANS_ACTION, null, "Direction"));
    }
}
