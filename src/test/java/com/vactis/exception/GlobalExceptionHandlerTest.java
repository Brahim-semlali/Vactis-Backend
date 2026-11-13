package com.vactis.exception;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.vactis.dto.common.ErrorResponse;
import com.vactis.model.reclamation.CategorieReclamation;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void invalidEnumErrorNamesFieldValueAndAcceptedCategories() {
        InvalidFormatException cause = InvalidFormatException.from(
            null,
            "Cannot deserialize category",
            "RETARD_LIVRAISON",
            CategorieReclamation.class);
        cause.prependPath(Object.class, "categorie");

        ResponseEntity<ErrorResponse> response = handler.handleHttpMessageNotReadable(
            new HttpMessageNotReadableException("Invalid request body", cause));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(
            "Valeur « RETARD_LIVRAISON » invalide pour le champ « categorie ». Valeurs autorisées : DELAIS_RESULTATS, ERREUR_TECHNIQUE, FACTURATION_TARIFS, LOGISTIQUE_COURSIER, RELATIONNEL_ACCUEIL, AUTRE.",
            response.getBody().message());
    }

    @Test
    void malformedJsonIncludesJacksonParseReason() {
        JsonParseException cause = new JsonParseException(null, "Unexpected end of input");

        ResponseEntity<ErrorResponse> response = handler.handleHttpMessageNotReadable(
            new HttpMessageNotReadableException("Invalid request body", cause));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().message().contains("Unexpected end of input"));
    }

    @Test
    void businessValidationReturnsBadRequestWithActionableMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleBusinessValidation(
                new IllegalArgumentException("Le commentaire est obligatoire en cas de réclamation."));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(AuthErrorCode.BUSINESS_RULE_VIOLATION, response.getBody().code());
        assertEquals("Le commentaire est obligatoire en cas de réclamation.", response.getBody().message());
    }

    @Test
    void dataIntegrityConflictDoesNotExposeDatabaseDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleDataConflict(
                new DataIntegrityViolationException("column note violates not-null constraint"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(AuthErrorCode.DATA_CONFLICT, response.getBody().code());
        assertTrue(response.getBody().message().contains("Vérifiez les informations et réessayez"));
        assertTrue(!response.getBody().message().contains("not-null"));
    }

    @Test
    void businessStateConflictReturnsConflictWithActionableMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleBusinessConflict(
                new IllegalStateException("Cette action est déjà réservée par un autre utilisateur."));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(AuthErrorCode.DATA_CONFLICT, response.getBody().code());
        assertEquals("Cette action est déjà réservée par un autre utilisateur.", response.getBody().message());
    }
}