package com.vactis.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.vactis.dto.medecin.Fiche360Response;
import com.vactis.dto.medecin.Fiche360Response.ActionLigne;
import com.vactis.dto.medecin.Fiche360Response.CommercialLigne;
import com.vactis.dto.medecin.Fiche360Response.Dossier;
import com.vactis.dto.medecin.Fiche360Response.Evenement;
import com.vactis.dto.medecin.Fiche360Response.Identite;
import com.vactis.dto.medecin.Fiche360Response.Indicateurs;
import com.vactis.dto.medecin.Fiche360Response.Mois;
import com.vactis.dto.medecin.Fiche360Response.ReclamationLigne;
import com.vactis.dto.medecin.Fiche360Response.Visite;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Export PDF de la fiche médecin 360°. Le document reprend, section par section, exactement
 * les données de {@link Fiche360Response} : il ne recalcule rien.
 */
@Service
public class Fiche360PdfService {

    private static final Color TEAL = new Color(0, 155, 131);
    private static final Color ENCRE = new Color(15, 23, 42);
    private static final Color GRIS = new Color(100, 116, 139);
    private static final Color BORDURE = new Color(226, 232, 240);
    private static final Color FOND = new Color(248, 250, 252);
    private static final Color FOND_ENTETE = new Color(241, 245, 249);
    private static final Color VERT_CLAIR = new Color(236, 253, 245);
    private static final Color ROUGE_CLAIR = new Color(255, 241, 242);
    private static final Color VERT = new Color(5, 150, 105);
    private static final Color ROUGE = new Color(225, 29, 72);

    private static final Font TITRE = new Font(Font.HELVETICA, 18, Font.BOLD, Color.WHITE);
    private static final Font SOUS_TITRE = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(204, 251, 241));
    private static final Font SECTION = new Font(Font.HELVETICA, 11, Font.BOLD, ENCRE);
    private static final Font ETIQUETTE = new Font(Font.HELVETICA, 7, Font.BOLD, GRIS);
    private static final Font VALEUR = new Font(Font.HELVETICA, 9, Font.BOLD, ENCRE);
    private static final Font KPI = new Font(Font.HELVETICA, 13, Font.BOLD, ENCRE);
    private static final Font ENTETE = new Font(Font.HELVETICA, 7, Font.BOLD, GRIS);
    private static final Font TEXTE = new Font(Font.HELVETICA, 8, Font.NORMAL, ENCRE);
    private static final Font TEXTE_GRAS = new Font(Font.HELVETICA, 8, Font.BOLD, ENCRE);
    private static final Font TEXTE_DISCRET = new Font(Font.HELVETICA, 7, Font.NORMAL, GRIS);
    private static final Font PIED = new Font(Font.HELVETICA, 7, Font.NORMAL, GRIS);

    private static final DateTimeFormatter JJ_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] MOIS = {"Janv.", "Févr.", "Mars", "Avr.", "Mai", "Juin", "Juil.", "Août",
            "Sept.", "Oct.", "Nov.", "Déc."};

    public byte[] generer(Fiche360Response fiche, String auteur) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 48);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, sortie);
            writer.setPageEvent(new PiedDePage(fiche, auteur));
            Identite id = fiche.getIdentite();
            document.addTitle("Fiche médecin 360° — " + id.getNomComplet());
            document.addAuthor("VACTIS");
            document.addSubject("Historique complet du médecin");
            document.open();

            enTete(document, fiche);
            int n = 1;
            section(document, n++ + ". Identité");
            document.add(identite(id));
            section(document, n++ + ". Indicateurs clés");
            document.add(indicateurs(fiche.getIndicateurs()));
            section(document, n++ + ". Activité mensuelle (12 derniers mois)");
            document.add(activite(fiche.getMois()));
            section(document, n++ + ". Frise chronologique");
            document.add(frise(fiche.getEvenements()));
            section(document, n++ + ". Visites terrain (" + taille(fiche.getVisites()) + ")");
            document.add(visites(fiche.getVisites()));
            section(document, n++ + ". Actions VACTIS (" + taille(fiche.getActions()) + ")");
            document.add(actions(fiche.getActions()));
            section(document, n++ + ". Réclamations (" + taille(fiche.getReclamations()) + ")");
            document.add(reclamations(fiche.getReclamations()));
            section(document, n++ + ". Obstacles relevés et commerciaux intervenus");
            document.add(obstaclesEtCommerciaux(fiche.getObstacles(), fiche.getCommerciaux()));
            section(document, n + ". Dossiers reçus par le laboratoire (" + taille(fiche.getDossiers()) + ")");
            document.add(dossiers(fiche.getDossiers()));
        } catch (DocumentException e) {
            throw new IllegalStateException("Impossible de générer le PDF de la fiche médecin", e);
        } finally {
            if (document.isOpen()) document.close();
        }
        return sortie.toByteArray();
    }

    // ── En-tête et sections ──────────────────────────────────────────────────

    private void enTete(Document document, Fiche360Response fiche) throws DocumentException {
        Identite id = fiche.getIdentite();
        PdfPTable bandeau = new PdfPTable(1);
        bandeau.setWidthPercentage(100);
        PdfPCell cellule = new PdfPCell();
        cellule.setBackgroundColor(TEAL);
        cellule.setBorder(Rectangle.NO_BORDER);
        cellule.setPadding(14);
        cellule.addElement(new Paragraph("VACTIS · FICHE MÉDECIN 360°", new Font(Font.HELVETICA, 8, Font.BOLD, new Color(204, 251, 241))));
        Paragraph nom = new Paragraph(texte(id.getNomComplet()), TITRE);
        nom.setSpacingBefore(2);
        cellule.addElement(nom);
        String ligne = joindre(" · ", id.getSpecialite(), id.getOrganisme(), id.getVille());
        cellule.addElement(new Paragraph(ligne.isEmpty() ? "—" : ligne, SOUS_TITRE));
        cellule.addElement(new Paragraph("Édité le " + date(fiche.getDateEdition()), SOUS_TITRE));
        bandeau.addCell(cellule);
        document.add(bandeau);
    }

    private void section(Document document, String titre) throws DocumentException {
        // Titre dans un bloc à hauteur propre : le tableau qui suit ne peut pas le chevaucher
        PdfPTable bloc = new PdfPTable(new float[]{1.2f, 98.8f});
        bloc.setWidthPercentage(100);
        bloc.setSpacingBefore(14);
        bloc.setSpacingAfter(6);
        bloc.setKeepTogether(true);
        PdfPCell repere = new PdfPCell(new Phrase(" ", SECTION));
        repere.setBorder(Rectangle.NO_BORDER);
        repere.setBackgroundColor(TEAL);
        bloc.addCell(repere);
        PdfPCell libelle = new PdfPCell(new Phrase(titre, SECTION));
        libelle.setBorder(Rectangle.NO_BORDER);
        libelle.setPaddingLeft(6);
        libelle.setPaddingTop(1);
        libelle.setPaddingBottom(4);
        bloc.addCell(libelle);
        document.add(bloc);
    }

    // ── Blocs ────────────────────────────────────────────────────────────────

    private PdfPTable identite(Identite id) {
        PdfPTable t = grille(4);
        champ(t, "Code médecin", id.getCodeMedecin());
        champ(t, "Statut VACTIS", id.getStatut());
        champ(t, "Segment", id.getSegment() == null ? null : "Segment " + id.getSegment());
        champ(t, "Note de potentiel", id.getNotePotentiel() == null ? null : nombre(id.getNotePotentiel()) + " / 5");
        champ(t, "Commercial référent", id.getCommercialReferent());
        champ(t, "Téléphone", id.getTelephone());
        champ(t, "E-mail", id.getEmail());
        champ(t, "Ville", id.getVille());
        return t;
    }

    private PdfPTable indicateurs(Indicateurs k) {
        PdfPTable t = grille(4);
        kpi(t, "CA sur 12 mois", mad(k.getCa12Mois()));
        kpi(t, "CA moyen mensuel", mad(Math.round(k.getCaMoyenMensuel())));
        kpi(t, "Dossiers sur 12 mois", entier(k.getDossiers12Mois()));
        kpi(t, "Dernier dossier", date(k.getDernierDossier()));
        kpi(t, "Visites (réalisées)", entier(k.getVisites()) + " (" + entier(k.getVisitesRealisees()) + ")");
        kpi(t, "Dernière visite", date(k.getDerniereVisite()));
        kpi(t, "Actions (ouvertes)", entier(k.getActions()) + " (" + entier(k.getActionsOuvertes()) + ")");
        kpi(t, "Réclamations (ouvertes)", entier(k.getReclamations()) + " (" + entier(k.getReclamationsOuvertes()) + ")");
        return t;
    }

    private PdfPTable activite(List<Mois> mois) {
        if (mois == null || mois.isEmpty()) return vide("Aucune activité enregistrée par le laboratoire.");
        long max = mois.stream().mapToLong(Mois::getCa).max().orElse(0);
        PdfPTable t = tableau(new float[]{14, 10, 16, 34, 18, 8}, "Mois", "Dossiers", "CA (MAD)", "", "Statut", "Visites");
        for (Mois m : mois) {
            t.addCell(cellule(libelleMois(m.getMois()), TEXTE_GRAS));
            t.addCell(nombreCellule(entier(m.getDossiers())));
            t.addCell(nombreCellule(entier(m.getCa())));
            PdfPCell barre = new PdfPCell(new Phrase(" ", TEXTE));
            barre.setBorderColor(BORDURE);
            barre.setCellEvent(new Barre(max == 0 ? 0 : (float) m.getCa() / max));
            t.addCell(barre);
            t.addCell(cellule(libelle(m.getStatut()), TEXTE));
            t.addCell(nombreCellule(m.getVisites() == 0 ? "—" : String.valueOf(m.getVisites())));
        }
        return t;
    }

    private PdfPTable frise(List<Evenement> evenements) {
        if (evenements == null || evenements.isEmpty()) return vide("Aucun événement enregistré.");
        PdfPTable t = tableau(new float[]{12, 12, 58, 18}, "Date", "Type", "Événement", "Commercial");
        for (Evenement e : evenements) {
            Color fond = "POSITIF".equals(e.getTonalite()) ? VERT_CLAIR : "NEGATIF".equals(e.getTonalite()) ? ROUGE_CLAIR : null;
            t.addCell(fond(cellule(date(e.getDate()), TEXTE), fond));
            t.addCell(fond(cellule(typeEvenement(e.getType()), TEXTE_GRAS), fond));
            Phrase contenu = new Phrase();
            contenu.add(new Chunk(texte(e.getTitre()), TEXTE_GRAS));
            if (e.getDetail() != null) contenu.add(new Chunk("\n" + texte(e.getDetail()), TEXTE_DISCRET));
            t.addCell(fond(cellule(contenu), fond));
            t.addCell(fond(cellule(texte(e.getCommercial()), TEXTE), fond));
        }
        return t;
    }

    private PdfPTable visites(List<Visite> visites) {
        if (visites == null || visites.isEmpty()) return vide("Aucune visite terrain enregistrée.");
        PdfPTable t = tableau(new float[]{11, 15, 17, 11, 14, 16, 8, 8},
                "Date", "Nature", "Commercial", "Statut", "Qualification", "Obstacle", "Note", "Récl.");
        for (Visite v : visites) {
            t.addCell(cellule(date(v.getDate()), TEXTE_GRAS));
            t.addCell(cellule(texte(v.getTypeVisite()), TEXTE));
            String commercial = texte(v.getCommercial()) + (v.getCommercialAppui() == null ? "" : "\nappui : " + v.getCommercialAppui());
            t.addCell(cellule(commercial, TEXTE));
            t.addCell(cellule(texte(v.getStatutVisite()), TEXTE));
            t.addCell(cellule(texte(v.getQualification()), TEXTE));
            t.addCell(cellule(texte(v.getObstaclePrincipal()), TEXTE));
            t.addCell(nombreCellule(v.getNote() == null ? "—" : nombre(v.getNote()) + "/5"));
            t.addCell(cellule(v.isReclamation() ? "Oui" : "Non", TEXTE));
            String suite = joindre("   ",
                    v.getCommentaire() == null ? null : "Commentaire : " + v.getCommentaire().trim(),
                    v.getMotifNonRealisation() == null ? null : "Motif de non-réalisation : " + v.getMotifNonRealisation(),
                    v.getProchaineAction() == null ? null : "Prochaine action : " + v.getProchaineAction()
                            + (v.getDateProchaineAction() == null ? "" : " (" + date(v.getDateProchaineAction()) + ")"));
            if (!suite.isEmpty()) {
                PdfPCell detail = cellule(suite, TEXTE_DISCRET);
                detail.setColspan(8);
                detail.setBackgroundColor(FOND);
                t.addCell(detail);
            }
        }
        return t;
    }

    private PdfPTable actions(List<ActionLigne> actions) {
        if (actions == null || actions.isEmpty()) return vide("Aucune action VACTIS recommandée.");
        PdfPTable t = tableau(new float[]{10, 28, 11, 17, 12, 11, 11},
                "Cycle", "Action recommandée", "Urgence", "Commercial", "Échéance", "État", "Visitée");
        for (ActionLigne a : actions) {
            t.addCell(cellule(libelleMois(a.getCycle()), TEXTE_GRAS));
            t.addCell(cellule(texte(a.getActionRecommandee()), TEXTE));
            t.addCell(cellule(texte(a.getUrgence()), TEXTE));
            t.addCell(cellule(texte(a.getCommercial()), TEXTE));
            t.addCell(cellule(date(a.getDateEcheance()), TEXTE));
            t.addCell(cellule(texte(a.getEtat()), TEXTE));
            t.addCell(cellule(a.isVisitee() ? "Oui" : "Non", a.isVisitee() ? TEXTE : new Font(Font.HELVETICA, 8, Font.BOLD, ROUGE)));
        }
        return t;
    }

    private PdfPTable reclamations(List<ReclamationLigne> reclamations) {
        if (reclamations == null || reclamations.isEmpty()) return vide("Aucune réclamation déclarée.");
        PdfPTable t = tableau(new float[]{7, 11, 15, 10, 15, 15, 27},
                "N°", "Déclarée le", "Catégorie", "Priorité", "Statut", "Commercial", "Description et traitement");
        for (ReclamationLigne r : reclamations) {
            t.addCell(cellule("#" + r.getId(), TEXTE_GRAS));
            t.addCell(cellule(date(r.getDateDeclaration()), TEXTE));
            t.addCell(cellule(texte(r.getCategorie()), TEXTE));
            t.addCell(cellule(texte(r.getPriorite()), TEXTE));
            t.addCell(cellule(texte(r.getStatut()), new Font(Font.HELVETICA, 8, Font.BOLD, r.isOuverte() ? ROUGE : VERT)));
            t.addCell(cellule(texte(r.getCommercial()), TEXTE));
            Phrase contenu = new Phrase();
            contenu.add(new Chunk(texte(r.getDescription()), TEXTE));
            if (r.getSolution() != null) contenu.add(new Chunk("\nSolution : " + r.getSolution(), TEXTE_DISCRET));
            if (r.getResponsable() != null || r.getDateResolution() != null) {
                contenu.add(new Chunk("\n" + joindre(" · ",
                        r.getResponsable() == null ? null : "Responsable : " + r.getResponsable(),
                        r.getDateResolution() == null ? null : "traitée le " + date(r.getDateResolution())), TEXTE_DISCRET));
            }
            t.addCell(cellule(contenu));
        }
        return t;
    }

    private PdfPTable obstaclesEtCommerciaux(Map<String, Long> obstacles, List<CommercialLigne> commerciaux) {
        PdfPTable conteneur = new PdfPTable(new float[]{40, 4, 56});
        conteneur.setWidthPercentage(100);

        PdfPTable gauche = tableau(new float[]{75, 25}, "Obstacle principal", "Visites");
        if (obstacles == null || obstacles.isEmpty()) {
            PdfPCell aucun = cellule("Aucun obstacle relevé.", TEXTE_DISCRET);
            aucun.setColspan(2);
            gauche.addCell(aucun);
        } else {
            obstacles.forEach((obstacle, nb) -> {
                gauche.addCell(cellule(obstacle, TEXTE));
                gauche.addCell(nombreCellule(String.valueOf(nb)));
            });
        }
        PdfPTable droite = tableau(new float[]{44, 16, 16, 24}, "Commercial", "Visites", "Appuis", "Dernier passage");
        if (commerciaux == null || commerciaux.isEmpty()) {
            PdfPCell aucun = cellule("Aucun commercial intervenu.", TEXTE_DISCRET);
            aucun.setColspan(4);
            droite.addCell(aucun);
        } else {
            for (CommercialLigne c : commerciaux) {
                droite.addCell(cellule(c.getCommercial(), TEXTE_GRAS));
                droite.addCell(nombreCellule(entier(c.getVisites())));
                droite.addCell(nombreCellule(entier(c.getAppuis())));
                droite.addCell(cellule(date(c.getDerniereVisite()), TEXTE));
            }
        }
        conteneur.addCell(colonne(gauche));
        conteneur.addCell(sansBordure(new PdfPCell(new Phrase(""))));
        conteneur.addCell(colonne(droite));
        return conteneur;
    }

    private PdfPTable dossiers(List<Dossier> dossiers) {
        if (dossiers == null || dossiers.isEmpty()) return vide("Aucun dossier reçu sur la période.");
        PdfPTable t = tableau(new float[]{11, 16, 33, 7, 13, 13, 7},
                "Reçu le", "Référence", "Type d'analyse", "Nb", "Statut", "Montant (MAD)", "Urg.");
        long total = 0;
        long analyses = 0;
        for (Dossier d : dossiers) {
            t.addCell(cellule(date(d.getDateReception()), TEXTE));
            t.addCell(cellule(texte(d.getReference()), TEXTE));
            t.addCell(cellule(texte(d.getTypeAnalyse()), TEXTE));
            t.addCell(nombreCellule(d.getNombreAnalyses() == null ? "—" : String.valueOf(d.getNombreAnalyses())));
            t.addCell(cellule(texte(d.getStatut()), TEXTE));
            t.addCell(nombreCellule(d.getMontant() == null ? "—" : entier(d.getMontant())));
            t.addCell(cellule(d.isUrgent() ? "Oui" : "", TEXTE));
            total += d.getMontant() == null ? 0 : d.getMontant();
            analyses += d.getNombreAnalyses() == null ? 0 : d.getNombreAnalyses();
        }
        PdfPCell libelleTotal = cellule("Total : " + dossiers.size() + " dossier(s)", TEXTE_GRAS);
        libelleTotal.setColspan(3);
        libelleTotal.setBackgroundColor(FOND_ENTETE);
        t.addCell(libelleTotal);
        t.addCell(fond(nombreCellule(entier(analyses)), FOND_ENTETE));
        t.addCell(fond(cellule("", TEXTE), FOND_ENTETE));
        PdfPCell montant = nombreCellule(entier(total));
        montant.setPhrase(new Phrase(entier(total), TEXTE_GRAS));
        t.addCell(fond(montant, FOND_ENTETE));
        t.addCell(fond(cellule("", TEXTE), FOND_ENTETE));
        return t;
    }

    // ── Briques de mise en page ──────────────────────────────────────────────

    private static PdfPTable grille(int colonnes) {
        PdfPTable t = new PdfPTable(colonnes);
        t.setWidthPercentage(100);
        return t;
    }

    private static void champ(PdfPTable t, String etiquette, String valeur) {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(BORDURE);
        c.setPadding(6);
        c.addElement(new Paragraph(etiquette.toUpperCase(Locale.FRENCH), ETIQUETTE));
        c.addElement(new Paragraph(texte(valeur), VALEUR));
        t.addCell(c);
    }

    private static void kpi(PdfPTable t, String etiquette, String valeur) {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(BORDURE);
        c.setBackgroundColor(FOND);
        c.setPadding(7);
        c.addElement(new Paragraph(etiquette.toUpperCase(Locale.FRENCH), ETIQUETTE));
        c.addElement(new Paragraph(valeur, KPI));
        t.addCell(c);
    }

    private static PdfPTable tableau(float[] largeurs, String... entetes) {
        PdfPTable t = new PdfPTable(largeurs);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (String e : entetes) {
            PdfPCell c = new PdfPCell(new Phrase(e.toUpperCase(Locale.FRENCH), ENTETE));
            c.setBackgroundColor(FOND_ENTETE);
            c.setBorderColor(BORDURE);
            c.setPadding(5);
            t.addCell(c);
        }
        return t;
    }

    private static PdfPTable vide(String message) {
        PdfPTable t = grille(1);
        PdfPCell c = cellule(message, TEXTE_DISCRET);
        c.setBackgroundColor(FOND);
        c.setPadding(8);
        t.addCell(c);
        return t;
    }

    private static PdfPCell cellule(String texte, Font police) {
        return cellule(new Phrase(texte(texte), police));
    }

    private static PdfPCell cellule(Phrase phrase) {
        PdfPCell c = new PdfPCell(phrase);
        c.setBorderColor(BORDURE);
        c.setPadding(4.5f);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return c;
    }

    private static PdfPCell nombreCellule(String valeur) {
        PdfPCell c = cellule(valeur, TEXTE);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private static PdfPCell fond(PdfPCell c, Color couleur) {
        if (couleur != null) c.setBackgroundColor(couleur);
        return c;
    }

    /** Cellule de mise en page qui garde la hauteur naturelle du tableau qu'elle contient. */
    private static PdfPCell colonne(PdfPTable contenu) {
        PdfPCell c = sansBordure(new PdfPCell());
        c.setVerticalAlignment(Element.ALIGN_TOP);
        c.addElement(contenu);
        return c;
    }

    private static PdfPCell sansBordure(PdfPCell c) {
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(0);
        return c;
    }

    /** Barre horizontale proportionnelle au CA du mois. */
    private record Barre(float ratio) implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            if (ratio <= 0) return;
            PdfContentByte canvas = canvases[PdfPTable.BACKGROUNDCANVAS];
            float marge = 4;
            float largeur = (position.getWidth() - 2 * marge) * Math.min(1f, ratio);
            float hauteur = 6;
            float y = position.getBottom() + (position.getHeight() - hauteur) / 2;
            canvas.saveState();
            canvas.setColorFill(TEAL);
            canvas.roundRectangle(position.getLeft() + marge, y, Math.max(1.5f, largeur), hauteur, 2);
            canvas.fill();
            canvas.restoreState();
        }
    }

    /** Pied de page : confidentialité, auteur de l'export et numéro de page. */
    private static final class PiedDePage extends PdfPageEventHelper {
        private final String gauche;

        PiedDePage(Fiche360Response fiche, String auteur) {
            this.gauche = "Document confidentiel — usage interne VACTIS · " + texte(fiche.getIdentite().getNomComplet())
                    + " · exporté le " + date(fiche.getDateEdition()) + (auteur == null || auteur.isBlank() ? "" : " par " + auteur);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            float y = document.bottomMargin() - 20;
            canvas.saveState();
            canvas.setColorStroke(BORDURE);
            canvas.moveTo(document.left(), y + 10);
            canvas.lineTo(document.right(), y + 10);
            canvas.stroke();
            canvas.restoreState();
            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, new Phrase(gauche, PIED), document.left(), y, 0);
            ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT, new Phrase("Page " + writer.getPageNumber(), PIED),
                    document.right(), y, 0);
        }
    }

    // ── Formats ──────────────────────────────────────────────────────────────

    /** Remplace les caractères absents des polices PDF standard (flèches, espaces fines). */
    static String texte(String valeur) {
        if (valeur == null || valeur.isBlank()) return "—";
        return valeur.replace("→", "->").replace(' ', ' ').replace(' ', ' ');
    }

    private static String date(LocalDate d) {
        return d == null ? "—" : d.format(JJ_MM_AAAA);
    }

    private static String libelleMois(String mois) {
        try {
            YearMonth ym = YearMonth.parse(mois);
            return MOIS[ym.getMonthValue() - 1] + " " + ym.getYear();
        } catch (RuntimeException e) {
            return texte(mois);
        }
    }

    private static String libelle(String code) {
        return Fiche360Service.libelle(code);
    }

    private static String typeEvenement(String type) {
        return switch (type == null ? "" : type) {
            case "VISITE" -> "Visite";
            case "ACTION" -> "Action";
            case "RECLAMATION" -> "Réclamation";
            case "STATUT" -> "Statut";
            case "ALERTE" -> "Alerte";
            default -> "—";
        };
    }

    private static String entier(long valeur) {
        return texte(NumberFormat.getIntegerInstance(Locale.FRANCE).format(valeur));
    }

    private static String mad(long valeur) {
        return entier(valeur) + " MAD";
    }

    private static String nombre(double valeur) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.FRANCE);
        f.setMaximumFractionDigits(1);
        return texte(f.format(valeur));
    }

    private static int taille(List<?> liste) {
        return liste == null ? 0 : liste.size();
    }

    private static String joindre(String separateur, String... morceaux) {
        StringBuilder sb = new StringBuilder();
        for (String m : morceaux) {
            if (m == null || m.isBlank()) continue;
            if (!sb.isEmpty()) sb.append(separateur);
            sb.append(m.trim());
        }
        return sb.toString();
    }
}
