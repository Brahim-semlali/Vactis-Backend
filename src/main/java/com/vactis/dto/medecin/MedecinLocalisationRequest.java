package com.vactis.dto.medecin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record MedecinLocalisationRequest(
        @DecimalMin(value = "-90.0", message = "La latitude doit être supérieure ou égale à -90")
        @DecimalMax(value = "90.0", message = "La latitude doit être inférieure ou égale à 90")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "La longitude doit être supérieure ou égale à -180")
        @DecimalMax(value = "180.0", message = "La longitude doit être inférieure ou égale à 180")
        Double longitude
) {}
