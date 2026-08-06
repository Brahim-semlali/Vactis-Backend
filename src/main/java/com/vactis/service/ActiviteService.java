package com.vactis.service;

import com.vactis.dto.activite.ComparaisonMetriqueResponse;
import com.vactis.dto.activite.ComparaisonResponse;
import com.vactis.dto.activite.KpiMensuelResponse;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Service métier pour l'analyse de l'activité mensuelle (KPIs et comparaisons)
@Service
@RequiredArgsConstructor
@Slf4j
public class ActiviteService {

    private final ExtractionDonneesRepository extractionDonneesRepository;
    private final MedecinRepository medecinRepository;

    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM");

    // Retourne la liste des mois disponibles en base, du plus récent au plus ancien
    public List<String> getMoisDisponibles() {
        List<LocalDate> dates = extractionDonneesRepository.findAllDatesDescending();
        if (dates.isEmpty()) {
            return List.of(YearMonth.now().format(YYYY_MM));
        }

        List<String> months = new ArrayList<>();
        for (LocalDate d : dates) {
            String ym = d.format(YYYY_MM);
            if (!months.contains(ym)) {
                months.add(ym);
            }
        }
        return months;
    }

    // Calcule les KPIs d'activité (CA, cas, médecins actifs, non affectés) pour un mois donné
    public KpiMensuelResponse getKpisMensuels(String moisParam) {
        YearMonth ym = parseOrGetDefaultMois(moisParam);
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();

        Long caMoisTotal = extractionDonneesRepository.sumPrixAPayerByDateRange(startDate, endDate);
        Long casMoisTotal = extractionDonneesRepository.countCasByDateRange(startDate, endDate);
        Long medecinsAvecActivite = extractionDonneesRepository.countMedecinsDistinctsByDateRange(startDate, endDate);
        Long portefeuilleMedecins = medecinRepository.count();
        Long portefeuilleCA = extractionDonneesRepository.sumPrixAPayerWithMedecinByDateRange(startDate, endDate);
        Long nonAffectesCount = extractionDonneesRepository.countNonAffectesByDateRange(startDate, endDate);
        Long nonAffectesCA = extractionDonneesRepository.sumPrixAPayerNonAffectesByDateRange(startDate, endDate);

        double nonAffectesPct = 0.0;
        if (casMoisTotal != null && casMoisTotal > 0) {
            nonAffectesPct = Math.round((nonAffectesCount * 100.0 / casMoisTotal) * 10.0) / 10.0;
        }

        return KpiMensuelResponse.builder()
                .mois(ym.format(YYYY_MM))
                .caMoisTotal(caMoisTotal != null ? caMoisTotal : 0L)
                .casMoisTotal(casMoisTotal != null ? casMoisTotal : 0L)
                .medecinsAvecActivite(medecinsAvecActivite != null ? medecinsAvecActivite : 0L)
                .portefeuilleMedecins(portefeuilleMedecins != null ? portefeuilleMedecins : 0L)
                .portefeuilleCA(portefeuilleCA != null ? portefeuilleCA : 0L)
                .nonAffectesCount(nonAffectesCount != null ? nonAffectesCount : 0L)
                .nonAffectesPct(nonAffectesPct)
                .nonAffectesCA(nonAffectesCA != null ? nonAffectesCA : 0L)
                .build();
    }

    // Compare les métriques du mois M avec M-1 et une fenêtre de référence glissante
    public ComparaisonResponse getComparaison(String moisParam, String metrique, Integer fenetreRef) {
        YearMonth ymM = parseOrGetDefaultMois(moisParam);
        YearMonth ymMMinus1 = ymM.minusMonths(1);
        int window = (fenetreRef == null || fenetreRef <= 0) ? 3 : fenetreRef;

        // Valeurs M
        double casM = getCasForMonth(ymM);
        double caM = getCaForMonth(ymM);

        // Valeurs M-1
        double casMMinus1 = getCasForMonth(ymMMinus1);
        double caMMinus1 = getCaForMonth(ymMMinus1);

        // Référence récente (moyenne des N mois précédents : M-1, M-2, ... M-N)
        double totalCasRef = 0.0;
        double totalCaRef = 0.0;
        int countRef = 0;

        for (int i = 1; i <= window; i++) {
            YearMonth pastMonth = ymM.minusMonths(i);
            totalCasRef += getCasForMonth(pastMonth);
            totalCaRef += getCaForMonth(pastMonth);
            countRef++;
        }

        double refCas = countRef > 0 ? (totalCasRef / countRef) : 0.0;
        double refCa = countRef > 0 ? (totalCaRef / countRef) : 0.0;

        refCas = Math.round(refCas * 100.0) / 100.0;
        refCa = Math.round(refCa * 100.0) / 100.0;

        ComparaisonMetriqueResponse casComp = buildMetriqueResponse(casM, casMMinus1, refCas);
        ComparaisonMetriqueResponse caComp = buildMetriqueResponse(caM, caMMinus1, refCa);

        return ComparaisonResponse.builder()
                .mois(ymM.format(YYYY_MM))
                .moisPrecedent(ymMMinus1.format(YYYY_MM))
                .fenetreRefMois(window)
                .cas("ca".equalsIgnoreCase(metrique) ? null : casComp)
                .ca("cas".equalsIgnoreCase(metrique) ? null : caComp)
                .build();
    }

    // Construit la structure de réponse avec les variations en valeur et en pourcentage
    private ComparaisonMetriqueResponse buildMetriqueResponse(double m, double mMinus1, double ref) {
        double diffMMinus1 = Math.round((m - mMinus1) * 100.0) / 100.0;
        double pctMMinus1 = mMinus1 > 0 ? Math.round(((m - mMinus1) / mMinus1 * 100.0) * 10.0) / 10.0 : 0.0;

        double diffRef = Math.round((m - ref) * 100.0) / 100.0;
        double pctRef = ref > 0 ? Math.round(((m - ref) / ref * 100.0) * 10.0) / 10.0 : 0.0;

        return ComparaisonMetriqueResponse.builder()
                .moisCourant(m)
                .moisPrecedent(mMinus1)
                .referenceRecente(ref)
                .variationVsMPrecedentVal(diffMMinus1)
                .variationVsMPrecedentPct(pctMMinus1)
                .variationVsRefVal(diffRef)
                .variationVsRefPct(pctRef)
                .build();
    }

    // Compte le nombre de cas pour un mois donné
    private double getCasForMonth(YearMonth ym) {
        Long count = extractionDonneesRepository.countCasByDateRange(ym.atDay(1), ym.atEndOfMonth());
        return count != null ? count.doubleValue() : 0.0;
    }

    // Calcule le CA pour un mois donné
    private double getCaForMonth(YearMonth ym) {
        Long sum = extractionDonneesRepository.sumPrixAPayerByDateRange(ym.atDay(1), ym.atEndOfMonth());
        return sum != null ? sum.doubleValue() : 0.0;
    }

    // Résout le mois à analyser depuis le paramètre ou détermine le mois par défaut
    private YearMonth parseOrGetDefaultMois(String moisParam) {
        if (moisParam != null && !moisParam.isBlank()) {
            try {
                return YearMonth.parse(moisParam.trim(), YYYY_MM);
            } catch (Exception e) {
                log.warn("Format de mois invalide: {}, fallback au mois disponible ou actuel.", moisParam);
            }
        }
        List<String> disponibles = getMoisDisponibles();
        if (!disponibles.isEmpty()) {
            try {
                return YearMonth.parse(disponibles.get(0), YYYY_MM);
            } catch (Exception ignored) {
            }
        }
        return YearMonth.now();
    }
}
