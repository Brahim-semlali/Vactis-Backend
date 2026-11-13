package com.vactis.service;

import com.vactis.dto.bridgetogoal.BridgeGoalTargetRequest;
import com.vactis.dto.bridgetogoal.BridgeGoalTargetResponse;
import com.vactis.model.system.BridgeGoalTarget;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.system.BridgeGoalTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BridgeGoalTargetService {
    private final BridgeGoalTargetRepository targetRepository;
    private final ExtractionDonneesRepository extractionDonneesRepository;

    public BridgeGoalTargetResponse get(String period) {
        YearMonth month = (period == null || period.isBlank()) ? determinerMoisReference(null) : parsePeriod(period);
        return targetRepository.findByPeriod(month.toString())
            .map(this::toResponse)
            .orElseGet(() -> {
                long previousRevenue = extractionDonneesRepository.sumPrixAPayerByDateRange(
                    month.minusMonths(1).atDay(1), month.minusMonths(1).atEndOfMonth());
                return new BridgeGoalTargetResponse(month.toString(), "DIRECT", null,
                    previousRevenue, null);
            });
    }

    public BridgeGoalTargetResponse save(BridgeGoalTargetRequest request) {
        YearMonth month = (request == null || request.mois() == null || request.mois().isBlank())
            ? determinerMoisReference(null)
            : parsePeriod(request.mois());
        String method = request.method() == null ? "" : request.method().trim().toUpperCase();
        BigDecimal value = request.value();
        if (value == null || ("DIRECT".equals(method) ? value.signum() <= 0 : value.signum() < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La valeur de l’objectif est invalide pour la méthode choisie.");
        }

        long previousRevenue = extractionDonneesRepository.sumPrixAPayerByDateRange(
            month.minusMonths(1).atDay(1), month.minusMonths(1).atEndOfMonth());
        long targetAmount;
        if ("DIRECT".equals(method)) {
            targetAmount = value.setScale(0, RoundingMode.HALF_UP).longValueExact();
        } else if ("PERCENTAGE".equals(method)) {
            if (previousRevenue <= 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Le CA du mois précédent doit être positif pour calculer un objectif en pourcentage.");
            }
            targetAmount = BigDecimal.valueOf(previousRevenue)
                .multiply(BigDecimal.ONE.add(value.movePointLeft(2)))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La méthode doit être DIRECT ou PERCENTAGE.");
        }
        if (targetAmount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L’objectif calculé doit être strictement positif.");
        }

        BridgeGoalTarget target = targetRepository.findByPeriod(month.toString())
            .orElseGet(BridgeGoalTarget::new);
        target.setPeriod(month.toString());
        target.setMethod(method);
        target.setInputValue(value);
        target.setPreviousMonthRevenue(previousRevenue);
        target.setTargetAmount(targetAmount);
        target.setUpdatedAt(java.time.LocalDateTime.now());
        return toResponse(targetRepository.save(target));
    }

    public BridgeGoalTargetResponse toResponse(BridgeGoalTarget target) {
        return new BridgeGoalTargetResponse(target.getPeriod(), target.getMethod(), target.getInputValue(),
            target.getPreviousMonthRevenue(), target.getTargetAmount());
    }

    private YearMonth parsePeriod(String period) {
        try {
            return YearMonth.parse(period);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le mois doit être au format AAAA-MM.");
        }
    }

    private YearMonth determinerMoisReference(String moisParam) {
        if (moisParam != null && !moisParam.isBlank()) {
            try {
                return YearMonth.parse(moisParam.trim());
            } catch (Exception ignored) {
            }
        }
        List<LocalDate> dates = extractionDonneesRepository.findAllDatesDescending();
        if (dates != null && !dates.isEmpty() && dates.get(0) != null) {
            return YearMonth.from(dates.get(0));
        }
        return YearMonth.now();
    }
}