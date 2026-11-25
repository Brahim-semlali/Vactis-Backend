package com.vactis.config;

import com.vactis.service.auth.JwtService;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matrice des droits d'accès de l'API, testée à travers la vraie chaîne de sécurité
 * ({@link SecurityConfig} + {@link JwtAuthFilter}) : pour chaque route protégée, qui obtient 401, 403 ou l'accès.
 * Un contrôleur « sonde » répond 200 à toutes les routes, seul le filtrage de sécurité est donc mesuré.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SecurityRulesTest.Contexte.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost:5173,http://127.0.0.1:5173")
class SecurityRulesTest {

    private static final String ANONYME = "ANONYME";
    private static final String CONNECTE = "CONNECTE";

    @Autowired private WebApplicationContext contexte;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(contexte).apply(springSecurity()).build();
    }

    /**
     * methode, route, droits de l'appelant (séparés par « | »), statut attendu.
     * ANONYME = sans jeton ; CONNECTE = connecté sans aucun droit particulier.
     */
    @ParameterizedTest(name = "{0} {1} [{2}] → {3}")
    @CsvSource({
            // Routes publiques
            "POST,   /api/auth/login,                         ANONYME,                     200",
            "POST,   /api/auth/register,                      ANONYME,                     200",
            "GET,    /api/auth/account-status,                ANONYME,                     200",
            "GET,    /actuator/health,                        ANONYME,                     200",
            // Authentification requise
            "GET,    /api/auth/profile,                       ANONYME,                     401",
            "PUT,    /api/auth/profile,                       ANONYME,                     401",
            "POST,   /api/auth/change-password,               ANONYME,                     401",
            "POST,   /api/auth/logout,                        ANONYME,                     401",
            "GET,    /api/auth/profile,                       CONNECTE,                    200",
            "GET,    /api/medecins,                           ANONYME,                     401",
            "GET,    /api/actions,                            ANONYME,                     401",
            "GET,    /api/reclamations,                       ANONYME,                     401",
            "GET,    /api/activite/kpis-mensuels,             ANONYME,                     401",
            "GET,    /api/alertes/fin-cycle,                  ANONYME,                     401",
            "GET,    /api/medecins/7/fiche-360/pdf,           ANONYME,                     401",
            "GET,    /api/medecins,                           CONNECTE,                    200",
            "GET,    /api/alertes/fin-cycle,                  CONNECTE,                    200",
            // Utilisateurs et rôles : droit de menu obligatoire (ROLE_ADMIN seul ne suffit pas)
            "GET,    /users/,                                 CONNECTE,                    403",
            "GET,    /users/,                                 ROLE_ADMIN,                  403",
            "GET,    /users/,                                 MENU:/users,                 200",
            "PUT,    /users/5/role/2,                         MENU:/roles,                 403",
            "PUT,    /users/5/role/2,                         MENU:/users,                 200",
            "POST,   /roles/add,                              CONNECTE,                    403",
            "POST,   /roles/add,                              MENU:/users,                 403",
            "POST,   /roles/add,                              MENU:/roles,                 200",
            "DELETE, /roles/3,                                MENU:/roles,                 200",
            // Administration
            "GET,    /api/admin/settings,                     CONNECTE,                    403",
            "GET,    /api/admin/settings,                     MENU:/users,                 403",
            "GET,    /api/admin/settings,                     ROLE_ADMIN,                  200",
            "PUT,    /api/admin/settings,                     MENU:/parametres-systeme,    200",
            "POST,   /api/admin/excel/import,                 CONNECTE,                    403",
            "POST,   /api/admin/excel/import,                 MENU:/parametres-systeme,    403",
            "POST,   /api/admin/excel/import,                 MENU:/import-export-excel,   200",
            "GET,    /api/admin/excel/export,                 ROLE_ADMIN,                  200",
            "GET,    /api/admin/connexion-logs,               CONNECTE,                    403",
            "GET,    /api/admin/connexion-logs,               MENU:/roles,                 200",
            "GET,    /api/admin/menu-tree-complet,            MENU:/users,                 200",
            "GET,    /api/admin/menu-tree-complet,            MENU:/medecins,              403",
            // Pilotage direction
            "GET,    /api/dashboard-direction,                CONNECTE,                    403",
            "GET,    /api/dashboard-direction,                MENU:/suivi-equipe,          403",
            "GET,    /api/dashboard-direction,                MENU:/dashboard-direction,   200",
            "GET,    /api/dashboard-direction,                ROLE_ADMIN,                  200",
            "GET,    /api/suivi-equipe/commerciaux,           CONNECTE,                    403",
            "GET,    /api/suivi-equipe/commerciaux,           MENU:/dashboard-direction,   403",
            "GET,    /api/suivi-equipe/commerciaux,           MENU:/suivi-equipe,          200",
            // Contrôle qualité : droit de menu uniquement
            "GET,    /api/controle/type/QUALITE,              ROLE_ADMIN,                  403",
            "POST,   /api/controle,                           MENU:/controle,              200",
            "POST,   /api/controle/type/RUPTURE/reinitialiser, MENU:/controle,             200",
            "POST,   /api/controle/type/RUPTURE/reinitialiser, CONNECTE,                   403",
            "POST,   /api/controle/type/RUPTURE/reinitialiser, MENU:/parametres-systeme,   403",
            // Notifications : réservées aux utilisateurs ayant accès aux réclamations
            "GET,    /api/notifications,                      CONNECTE,                    403",
            "GET,    /api/notifications/count,                MENU:/reclamations,          200",
            // Gestion des menus
            "POST,   /api/menu/AddMenu,                       MENU:/users,                 403",
            "POST,   /api/menu/AddMenu,                       ROLE_ADMIN,                  200",
            "PUT,    /api/menu/Update/4,                      CONNECTE,                    403",
            "DELETE, /api/menu/Delete/4,                      ROLE_ADMIN,                  200",
            "GET,    /api/menu/mon-menu,                      CONNECTE,                    200",
            // Zone intelligence et concurrence
            "GET,    /api/agences-concurrentes,               CONNECTE,                    403",
            "GET,    /api/agences-concurrentes,               MENU:/medecins,              200",
            "GET,    /api/agences-concurrentes/2,             MENU:/zone-intelligence,     200",
            "POST,   /api/agences-concurrentes,               MENU:/zone-intelligence,     403",
            "POST,   /api/agences-concurrentes,               ROLE_ADMIN,                  200",
            "PUT,    /api/agences-concurrentes/2,             MENU:/medecins,              403",
            "DELETE, /api/agences-concurrentes/2,             MENU:/zone-intelligence,     403",
            "DELETE, /api/agences-concurrentes/2,             ROLE_ADMIN,                  200",
            "GET,    /api/zone-intelligence/laboratoire,      CONNECTE,                    403",
            "GET,    /api/zone-intelligence/laboratoire,      MENU:/zone-intelligence,     200",
            "PUT,    /api/zone-intelligence/laboratoire,      MENU:/zone-intelligence,     403",
            "PUT,    /api/zone-intelligence/laboratoire,      ROLE_ADMIN,                  200",
            "GET,    /api/medecins/geolocalises,              MENU:/actions,               403",
            "GET,    /api/medecins/geolocalises,              MENU:/medecins,              200",
            "GET,    /api/medecins/sans-localisation,         MENU:/zone-intelligence,     200",
            "PATCH,  /api/medecins/7/localisation,            MENU:/medecins,              403",
            "PATCH,  /api/medecins/7/localisation,            ROLE_ADMIN,                  200",
    })
    void matriceDesDroits(String methode, String route, String droits, int attendu) throws Exception {
        MockHttpServletRequestBuilder requete = request(HttpMethod.valueOf(methode), route);
        if (CONNECTE.equals(droits)) {
            requete.with(user("utilisateur").authorities(AuthorityUtils.NO_AUTHORITIES));
        } else if (!ANONYME.equals(droits)) {
            requete.with(user("utilisateur").authorities(AuthorityUtils.createAuthorityList(droits.split("\\|"))));
        }
        mvc.perform(requete).andExpect(status().is(attendu));
    }

    /**
     * Constat d'audit (critique) : l'inscription est publique et donne le rôle USER sans aucun menu.
     * Ce compte accède pourtant à toutes les routes protégées seulement par « authentifié ».
     * Ce test documente le comportement actuel ; il devra être inversé (403) quand les routes seront restreintes.
     */
    @ParameterizedTest(name = "compte auto-inscrit (ROLE_USER) : {0} {1} → {2}")
    @CsvSource({
            "GET,   /api/medecins,                    200",
            "GET,   /api/medecins/7/fiche-360/pdf,    200",
            "GET,   /api/reclamations,                200",
            "GET,   /api/activite/equipe-commerciale, 200",
            "POST,  /api/alertes/batch-run,           200",
            "PATCH, /api/medecins/7/note-input,       200",
            "POST,  /api/recommandations/7/creer-action, 200",
    })
    void constatCompteAutoInscrit(String methode, String route, int attendu) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(methode), route)
                        .with(user("inscrit").authorities(AuthorityUtils.createAuthorityList("ROLE_USER"))))
                .andExpect(status().is(attendu));
    }

    @Test
    @DisplayName("Les refus renvoient un JSON homogène : 401 sans jeton, 403 sans droit")
    void lesRefusSontEnJson() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/api/medecins"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentification requise"));
        mvc.perform(request(HttpMethod.GET, "/api/dashboard-direction").with(user("c").authorities(AuthorityUtils.NO_AUTHORITIES)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("Un jeton JWT valide authentifie l'utilisateur avec les droits de son rôle")
    void jetonValide() throws Exception {
        var compte = User.withUsername("direction").password("x")
                .authorities("ROLE_DIRECTION", "MENU:/dashboard-direction").build();
        when(jwtService.extractUsername("bon-jeton")).thenReturn("direction");
        when(userDetailsService.loadUserByUsername("direction")).thenReturn(compte);
        when(jwtService.isTokenValid(eq("bon-jeton"), any())).thenReturn(true);

        mvc.perform(request(HttpMethod.GET, "/api/dashboard-direction").header("Authorization", "Bearer bon-jeton"))
                .andExpect(status().isOk());
        mvc.perform(request(HttpMethod.GET, "/api/suivi-equipe/commerciaux").header("Authorization", "Bearer bon-jeton"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un jeton invalide, expiré ou d'un compte verrouillé est traité comme anonyme (401)")
    void jetonsRefuses() throws Exception {
        when(jwtService.extractUsername("jeton-casse")).thenThrow(new MalformedJwtException("jeton illisible"));
        mvc.perform(request(HttpMethod.GET, "/api/medecins").header("Authorization", "Bearer jeton-casse"))
                .andExpect(status().isUnauthorized());

        var verrouille = User.withUsername("bloque").password("x").authorities("MENU:/medecins").accountLocked(true).build();
        when(jwtService.extractUsername("jeton-bloque")).thenReturn("bloque");
        when(userDetailsService.loadUserByUsername("bloque")).thenReturn(verrouille);
        when(jwtService.isTokenValid(anyString(), any())).thenReturn(true);
        mvc.perform(request(HttpMethod.GET, "/api/medecins").header("Authorization", "Bearer jeton-bloque"))
                .andExpect(status().isUnauthorized());

        var actif = User.withUsername("expire").password("x").authorities("MENU:/medecins").build();
        when(jwtService.extractUsername("jeton-expire")).thenReturn("expire");
        when(userDetailsService.loadUserByUsername("expire")).thenReturn(actif);
        when(jwtService.isTokenValid(eq("jeton-expire"), any())).thenReturn(false);
        mvc.perform(request(HttpMethod.GET, "/api/medecins").header("Authorization", "Bearer jeton-expire"))
                .andExpect(status().isUnauthorized());

        mvc.perform(request(HttpMethod.GET, "/api/medecins").header("Authorization", "Basic abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CORS : le front autorisé reçoit les en-têtes, une origine inconnue est refusée")
    void cors() throws Exception {
        mvc.perform(options("/api/medecins/7/fiche-360/pdf")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mvc.perform(request(HttpMethod.GET, "/api/medecins").header("Origin", "http://localhost:5173")
                        .with(user("u").authorities(AuthorityUtils.NO_AUTHORITIES)))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers",
                        org.hamcrest.Matchers.containsString("Content-Disposition")));

        mvc.perform(options("/api/medecins")
                        .header("Origin", "https://site-malveillant.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    // ── Contexte de test ─────────────────────────────────────────────────────

    /** Répond 200 à toute route : seul le filtrage de sécurité décide du statut. */
    @RestController
    static class Sonde {
        @RequestMapping({"/api/**", "/users/**", "/roles/**", "/actuator/health"})
        String ok() {
            return "ok";
        }
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtAuthFilter.class, PasswordConfig.class,
            RestAccessDeniedHandler.class, RestAuthenticationEntryPoint.class, Sonde.class})
    static class Contexte {
    }
}
