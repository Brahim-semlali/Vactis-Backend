package com.vactis;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Test de chargement du contexte Spring Boot complet.
 * Nécessite une base de données PostgreSQL active (non disponible en CI sans service dédié).
 * Les tests unitaires et de persistance sont couverts par :
 * - MedecinControllerTest (Mockito + MockMvc)
 * - MedecinRepositoryTest (@DataJpaTest + H2)
 */
@SpringBootTest
@Disabled("Necessite PostgreSQL actif. Couverture assuree par MedecinControllerTest et MedecinRepositoryTest.")
class VactisApplicationTests {

    @Test
    void contextLoads() {
    }
}
