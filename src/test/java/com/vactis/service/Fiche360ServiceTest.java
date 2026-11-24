package com.vactis.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.vactis.dto.medecin.Fiche360Response;
import com.vactis.dto.medecin.Fiche360Response.Evenement;
import com.vactis.dto.suiviequipe.SuiviEquipeDtos.EvolutionMedecin;
import com.vactis.dto.suiviequipe.SuiviEquipeDtos.MoisActivite;
import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.data.ExtractionDonnees;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.ObstaclePrincipal;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.StatutVisite;
import com.vactis.model.reclamation.CategorieReclamation;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.AlerteHebdomadaireRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.ReclamationRepository;
import com.vactis.repository.RetourTerrainRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Fiche360ServiceTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 24);

    @Mock private MedecinRepository medecinRepository;
    @Mock private SuiviEquipeService suiviEquipeService;
    @Mock private RetourTerrainRepository retourTerrainRepository;
    @Mock private ActionRepository actionRepository;
    @Mock private ReclamationRepository reclamationRepository;
    @Mock private ExtractionDonneesRepository extractionDonneesRepository;
    @Mock private AlerteHebdomadaireRepository alerteHebdomadaireRepository;

    @InjectMocks private Fiche360Service service;

    private Medecin medecin;

    @BeforeEach
    void setUp() {
        medecin = new Medecin();
        medecin.setId(7L);
        medecin.setNom("CHRAIBI");
        medecin.setPrenom("Imane");
        medecin.setSpecialite("Gynécologie");
        medecin.setOrganisme("Cabinet privé");
        medecin.setSegment("A");
        medecin.setStatut("ACTIF_STABLE");
        when(medecinRepository.findById(7L)).thenReturn(Optional.of(medecin));

        List<MoisActivite> mois = new ArrayList<>();
        for (int m = 1; m <= 13; m++) {
            String statut = m == 12 ? "surveillance" : m == 13 ? "actif_stable" : "surveillance";
            mois.add(MoisActivite.builder().mois(m <= 12 ? "2025-" + String.format("%02d", m) : "2026-01")
                    .ca(1000L * m).cas(m).statut(statut).visites(m == 13 ? 1 : 0).build());
        }
        when(suiviEquipeService.getEvolutionMedecin(7L)).thenReturn(EvolutionMedecin.builder().mois(mois).build());

        Action action = new Action();
        action.setId(11L);
        action.setMedecin(medecin);
        action.setCommercial("Commercial 1");
        action.setCycleMensuel("2026-01");
        action.setActionRecommandee("VISITE_RECONNAISSANCE");
        action.setEtatAction(EtatAction.PLANIFIEE);
        Action ouverte = new Action();
        ouverte.setId(12L);
        ouverte.setMedecin(medecin);
        ouverte.setCommercial("Commercial 1");
        ouverte.setCycleMensuel("2026-02");
        ouverte.setActionRecommandee("VISITE_SUIVI");
        when(actionRepository.findByMedecinId(7L)).thenReturn(new ArrayList<>(List.of(action, ouverte)));

        RetourTerrain favorable = retour(100L, LocalDate.of(2026, 1, 10), "Commercial 1", QualificationVisite.FAVORABLE);
        favorable.setAction(action);
        favorable.setCommercialAppui("Commercial 3");
        RetourTerrain defavorable = retour(101L, LocalDate.of(2025, 12, 5), "Commercial 2", QualificationVisite.DEFAVORABLE);
        defavorable.setObstaclePrincipal(ObstaclePrincipal.DELAI);
        defavorable.setCommentaire("Délais de rendu trop longs");
        RetourTerrain delai = retour(102L, LocalDate.of(2025, 11, 5), "Commercial 1", QualificationVisite.DEFAVORABLE);
        delai.setObstaclePrincipal(ObstaclePrincipal.DELAI);
        RetourTerrain prix = retour(103L, LocalDate.of(2025, 10, 5), "Commercial 1", QualificationVisite.DEFAVORABLE);
        prix.setObstaclePrincipal(ObstaclePrincipal.PRIX_TARIF);
        when(retourTerrainRepository.findByMedecinOrderByDateVisiteDescCreatedAtDesc(medecin))
                .thenReturn(List.of(favorable, defavorable, delai, prix));

        Reclamation traitee = new Reclamation();
        traitee.setId(91L);
        traitee.setMedecin(medecin);
        traitee.setCategorie(CategorieReclamation.AUTRE);
        traitee.setStatut(StatutReclamation.TRAITEE);
        traitee.setDateDeclaration(LocalDate.of(2025, 12, 6));
        traitee.setDateResolution(LocalDate.of(2025, 12, 18));
        traitee.setDescription("Délais");
        traitee.setSolutionApportee("Circuit express mis en place");
        when(reclamationRepository.findByMedecinOrderByDateDeclarationDesc(medecin)).thenReturn(List.of(traitee));
        when(alerteHebdomadaireRepository.findByMedecinOrderByDateDetectionDesc(medecin)).thenReturn(List.of());

        when(extractionDonneesRepository.findByMedecinIdOrderByDateReceptionAsc(7L)).thenReturn(List.of(
                dossier("D-OLD", LocalDate.of(2024, 12, 20), 500),
                dossier("D-1", LocalDate.of(2025, 3, 2), 800),
                dossier("D-2", LocalDate.of(2026, 1, 15), 1200)));
        when(medecinRepository.findAll()).thenReturn(List.of());
        when(actionRepository.findAll()).thenReturn(List.of());
        when(retourTerrainRepository.findAll()).thenReturn(List.of());
        when(reclamationRepository.findAll()).thenReturn(List.of());
        when(retourTerrainRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void assembleLHistoireCompleteDuMedecin() {
        Fiche360Response fiche = service.getFiche(7L, AUJOURDHUI);

        assertEquals("CHRAIBI Imane", fiche.getIdentite().getNomComplet());
        assertEquals("Actif stable", fiche.getIdentite().getStatut());
        assertEquals("Commercial 1", fiche.getIdentite().getCommercialReferent());

        // 12 derniers mois seulement : février 2025 → janvier 2026
        assertEquals(12, fiche.getMois().size());
        assertEquals("2025-02", fiche.getMois().get(0).getMois());
        assertEquals((2 + 13) * 12 / 2 * 1000L, fiche.getIndicateurs().getCa12Mois());
        assertEquals(LocalDate.of(2026, 1, 15), fiche.getIndicateurs().getDernierDossier());
        assertEquals(1, fiche.getIndicateurs().getActionsOuvertes());
        assertEquals(0, fiche.getIndicateurs().getReclamationsOuvertes());

        // Les dossiers hors fenêtre ne sont pas repris, les plus récents d'abord
        assertEquals(List.of("D-2", "D-1"), fiche.getDossiers().stream().map(Fiche360Response.Dossier::getReference).toList());

        assertEquals(List.of("Délais", "Prix / tarif"), new ArrayList<>(fiche.getObstacles().keySet()));
        assertEquals(2L, fiche.getObstacles().get("Délais"));

        assertEquals("Commercial 1", fiche.getCommerciaux().get(0).getCommercial());
        assertEquals(3, fiche.getCommerciaux().get(0).getVisites());
        assertTrue(fiche.getCommerciaux().stream().anyMatch(c -> c.getCommercial().equals("Commercial 3") && c.getAppuis() == 1));
    }

    @Test
    void laFriseRassembleVisitesStatutsActionsEtReclamations() {
        List<Evenement> frise = service.getFiche(7L, AUJOURDHUI).getEvenements();

        Evenement statut = frise.stream().filter(e -> "STATUT".equals(e.getType())).findFirst().orElseThrow();
        assertEquals("Statut : Surveillance → Actif stable", statut.getTitre());
        assertEquals("POSITIF", statut.getTonalite());
        assertEquals(LocalDate.of(2026, 1, 1), statut.getDate());

        Evenement visite = frise.stream().filter(e -> Long.valueOf(101L).equals(e.getReferenceId()) && "VISITE".equals(e.getType()))
                .findFirst().orElseThrow();
        assertEquals("Visite hors VACTIS", visite.getTitre());
        assertEquals("NEGATIF", visite.getTonalite());
        assertTrue(visite.getDetail().contains("obstacle : délais"));

        assertTrue(frise.stream().anyMatch(e -> "Réclamation #91 traitée".equals(e.getTitre()) && "POSITIF".equals(e.getTonalite())));
        assertTrue(frise.stream().anyMatch(e -> "Action recommandée : Visite reconnaissance".equals(e.getTitre())));
        for (int i = 1; i < frise.size(); i++) {
            LocalDate precedent = frise.get(i - 1).getDate();
            LocalDate courant = frise.get(i).getDate();
            if (precedent != null && courant != null) assertFalse(courant.isAfter(precedent));
        }
    }

    @Test
    void lePdfReprendToutesLesSections() throws Exception {
        Fiche360Response fiche = service.getFiche(7L, AUJOURDHUI);

        byte[] pdf = new Fiche360PdfService().generer(fiche, "direction");

        assertEquals("%PDF", new String(pdf, 0, 4));
        PdfReader reader = new PdfReader(pdf);
        StringBuilder texte = new StringBuilder();
        PdfTextExtractor extracteur = new PdfTextExtractor(reader);
        for (int page = 1; page <= reader.getNumberOfPages(); page++) texte.append(extracteur.getTextFromPage(page));
        reader.close();
        String contenu = texte.toString();
        for (String attendu : List.of("CHRAIBI Imane", "1. Identité", "2. Indicateurs clés", "3. Activité mensuelle",
                "4. Frise chronologique", "5. Visites terrain (4)", "6. Actions VACTIS (2)", "7. Réclamations (1)",
                "8. Obstacles relevés", "9. Dossiers reçus par le laboratoire (2)", "Circuit express mis en place",
                "Document confidentiel", "Page 1")) {
            assertTrue(contenu.contains(attendu), () -> "Texte absent du PDF : " + attendu);
        }
    }

    @Test
    void medecinInconnu() {
        when(medecinRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.getFiche(99L, AUJOURDHUI));
    }

    private RetourTerrain retour(Long id, LocalDate date, String visiteur, QualificationVisite q) {
        RetourTerrain r = new RetourTerrain();
        r.setId(id);
        r.setMedecin(medecin);
        r.setDateVisite(date);
        r.setVisiteur(visiteur);
        r.setStatutVisite(StatutVisite.REALISEE);
        r.setQualification(q);
        return r;
    }

    private ExtractionDonnees dossier(String reference, LocalDate date, int montant) {
        ExtractionDonnees d = new ExtractionDonnees();
        d.setMedecin(medecin);
        d.setReferenceDossier(reference);
        d.setDateReception(date);
        d.setTypeAnalyse("Bilan hormonal");
        d.setNombreAnalyses(2);
        d.setPrixAPayer(montant);
        return d;
    }
}
