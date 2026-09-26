package com.vactis.config;

import com.vactis.model.auth.AuthSettings;
import com.vactis.model.auth.Users;
import com.vactis.model.Roles.Roles;
import com.vactis.repository.RoleRepository;
import com.vactis.model.menu.MenuItem;
import com.vactis.repository.auth.AuthSettingsRepository;
import com.vactis.repository.auth.UserRepository;
import com.vactis.repository.menu.MenuItemRepository;
import com.vactis.repository.menu.MenuPrincipalRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import com.vactis.model.system.SystemSettings;
import com.vactis.repository.system.SystemSettingsRepository;

import java.util.List;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DefaultSystemDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthSettingsRepository authSettingsRepository;
    private final MenuItemRepository menuItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemSettingsRepository systemSettingsRepository;
    private final MenuPrincipalRepository menuPrincipalRepository;

    @Value("${VACTIS_ADMIN_PASSWORD:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        initAuthSettings();
        initSystemSettings();
        initMenuItems();
        initRoles();
        initAdminUser();
    }

    private void initSystemSettings() {
        systemSettingsRepository.findFirstOrCreateDefault();
    }

    private void initAuthSettings() {
        if (authSettingsRepository.count() == 0) {
            log.info("Initialisation des paramètres d'authentification par défaut...");
            AuthSettings settings = new AuthSettings();
            settings.setMaxFailedAttempts(3);
            settings.setLockDurationMinutes(2);
            authSettingsRepository.save(settings);
        }
    }

    private void initAdminUser() {
        Roles adminRole = roleRepository.findByNameRoleIgnoreCase("ADMIN")
            .orElseThrow(() -> new IllegalStateException("Le rôle ADMIN n'existe pas"));
        Users admin = userRepository.findByUsername("admin").orElse(null);

        if (admin == null) {
            if (adminPassword == null || adminPassword.isBlank()) {
                throw new IllegalStateException("VACTIS_ADMIN_PASSWORD doit être défini pour créer le compte admin");
            }
            log.info("Création du compte administrateur VACTIS par défaut...");
            admin = new Users();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setFirstName("Admin");
            admin.setLastName("VACTIS");
            admin.setEmail("admin@vactis.local");
            admin.setPhone("0600000000");
            admin.setEnabled(true);
            admin.setAccountLocked(false);
            admin.setFailedLoginAttempts(0);
        }

        if (admin.getRoles() == null || !adminRole.getIdRole().equals(admin.getRoles().getIdRole())) {
            log.info("Association du rôle ADMIN au compte administrateur existant");
            admin.setRoles(adminRole);
            userRepository.save(admin);
        }
    }

    private void initRoles() {
        createRoleIfMissing("ADMIN", "Administrateur du système");
        createRoleIfMissing("USER", "Utilisateur standard");
        createRoleIfMissing("COMMERCIALE", "Commerciale du système");
        Roles admin = roleRepository.findByNameRoleIgnoreCase("ADMIN")
            .orElseThrow(() -> new IllegalStateException("Le rôle ADMIN n'existe pas"));
        admin.setMenuItems(menuItemRepository.findAll());
        roleRepository.save(admin);

        Roles commerciale = roleRepository.findByNameRoleIgnoreCase("COMMERCIALE")
            .orElseThrow(() -> new IllegalStateException("Le rôle COMMERCIALE n'existe pas"));
        commerciale.setMenuItems(menuItemRepository.findAll().stream()
            .filter(menu -> List.of("/accueil", "/medecins", "/actions", "/lecture-activite", "/alertes-hebdo", "/recommandations", "/bridge-to-goal").contains(menu.getRoute()))
            .toList());
        roleRepository.save(commerciale);
    }

    private void createRoleIfMissing(String name, String description) {
        if (roleRepository.findByNameRoleIgnoreCase(name).isEmpty()) {
            Roles role = new Roles();
            role.setNameRole(name);
            role.setDescription(description);
            role.setMenuItems(List.of());
            roleRepository.save(role);
        }
    }

    private void initMenuItems() {
        log.info("Vérification des éléments de menu de la barre latérale...");
        List<String[]> items = List.of(
                    new String[]{"Accueil", "home", "/accueil", "1", "Pilotage"},
                    new String[]{"Dashboard Direction", "dashboard", "/dashboard-direction", "2", "Pilotage"},
                    new String[]{"Rapport commercial", "rapport", "/rapport-commercial", "3", "Pilotage"},
                    new String[]{"Lecture activité", "lecture", "/lecture-activite", "4", "Pilotage"},
                    new String[]{"Bridge to Goal", "chart", "/bridge-to-goal", "5", "Pilotage"},
                    new String[]{"Médecins", "medecins", "/medecins", "6", "Portefeuille médecins"},
                    new String[]{"Actions", "actions", "/actions", "7", "Terrain & Actions"},
                    new String[]{"Alertes hebdo", "alertes", "/alertes-hebdo", "8", "Terrain & Actions"},
                    new String[]{"Recommandations", "recommandations", "/recommandations", "9", "Terrain & Actions"},
                    new String[]{"Valeur détectée", "valeur", "/valeur-detectee", "10", "Portefeuille médecins"},
                    new String[]{"Zone intelligence", "zone", "/zone-intelligence", "11", "Portefeuille médecins"},
                    new String[]{"Qualité & doublons", "qualite", "/qualite-doublons", "12", "Qualité des données"},
                    new String[]{"Batches", "batches", "/batches", "13", "Qualité des données"},
                    new String[]{"Exports terrain", "exports", "/exports-terrain", "14", "Administration"},
                    new String[]{"Statut API", "statut", "/statut-api", "15", "Qualité des données"}
        );

        for (String[] arr : items) {
            MenuItem m = menuItemRepository.findByRoute(arr[2]).orElseGet(MenuItem::new);
            m.setLabel(arr[0]);
            m.setIcon(arr[1]);
            m.setRoute(arr[2]);
            m.setOrder(Integer.parseInt(arr[3]));
            m.setIsVisible(true);
            if (arr.length > 4) {
                menuPrincipalRepository.findByNomIgnoreCase(arr[4]).ifPresent(m::setMenuPrincipal);
            }
            menuItemRepository.save(m);
        }

        ensureMenuItem("Rôles", "roles", "/roles", 16, "Administration");
        ensureMenuItem("Users", "users", "/users", 17, "Administration");
        ensureMenuItem("Paramètres système", "settings", "/parametres-systeme", 18, "Administration");
    }

    private void ensureMenuItem(String label, String icon, String route, int order, String principalNom) {
        MenuItem menu = menuItemRepository.findByRoute(route).orElseGet(MenuItem::new);
        menu.setLabel(label);
        menu.setIcon(icon);
        menu.setRoute(route);
        menu.setOrder(order);
        menu.setIsVisible(true);
        if (principalNom != null) {
            menuPrincipalRepository.findByNomIgnoreCase(principalNom).ifPresent(menu::setMenuPrincipal);
        }
        menuItemRepository.save(menu);
    }
}
