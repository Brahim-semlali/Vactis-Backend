package com.vactis.config;

import com.vactis.model.auth.AuthSettings;
import com.vactis.model.auth.Users;
import com.vactis.model.Roles.Roles;
import com.vactis.repository.RoleRepository;
import com.vactis.model.menu.MenuItem;
import com.vactis.repository.auth.AuthSettingsRepository;
import com.vactis.repository.auth.UserRepository;
import com.vactis.repository.menu.MenuItemRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

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

    @Override
    public void run(String... args) {
        initAuthSettings();
        initMenuItems();
        initRoles();
        initAdminUser();
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
        if (!userRepository.existsByUsername("admin")) {
            log.info("Création du compte administrateur VACTIS par défaut...");
            Users admin = new Users();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("password"));
            admin.setFirstName("Admin");
            admin.setLastName("VACTIS");
            admin.setEmail("admin@vactis.local");
            admin.setPhone("0600000000");
                admin.setRoles(roleRepository.findByNameRoleIgnoreCase("ADMIN")
                    .orElseThrow(() -> new IllegalStateException("Le rôle ADMIN n'existe pas")));
            admin.setEnabled(true);
            admin.setAccountLocked(false);
            admin.setFailedLoginAttempts(0);
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
            .filter(menu -> List.of("/accueil", "/medecins", "/actions", "/lecture-activite").contains(menu.getRoute()))
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
                    new String[]{"Accueil", "home", "/accueil", "1"},
                    new String[]{"Dashboard Direction", "dashboard", "/dashboard-direction", "2"},
                    new String[]{"Rapport commercial", "rapport", "/rapport-commercial", "3"},
                    new String[]{"Lecture activité", "lecture", "/lecture-activite", "4"},
                    new String[]{"Médecins", "medecins", "/medecins", "5"},
                    new String[]{"Actions", "actions", "/actions", "6"},
                    new String[]{"Alertes hebdo", "alertes", "/alertes-hebdo", "7"},
                    new String[]{"Recommandations", "recommandations", "/recommandations", "8"},
                    new String[]{"Valeur détectée", "valeur", "/valeur-detectee", "9"},
                    new String[]{"Zone intelligence", "zone", "/zone-intelligence", "10"},
                    new String[]{"Qualité & doublons", "qualite", "/qualite-doublons", "11"},
                    new String[]{"Batches", "batches", "/batches", "12"},
                    new String[]{"Exports terrain", "exports", "/exports-terrain", "13"},
                    new String[]{"Statut API", "statut", "/statut-api", "14"}
        );

        for (String[] arr : items) {
            MenuItem m = menuItemRepository.findByRoute(arr[2]).orElseGet(MenuItem::new);
            m.setLabel(arr[0]);
            m.setIcon(arr[1]);
            m.setRoute(arr[2]);
            m.setOrder(Integer.parseInt(arr[3]));
            m.setIsVisible(true);
            menuItemRepository.save(m);
        }

        ensureMenuItem("Rôles", "roles", "/roles", 15);
        ensureMenuItem("Users", "users", "/users", 16);
    }

    private void ensureMenuItem(String label, String icon, String route, int order) {
        MenuItem menu = menuItemRepository.findByRoute(route).orElseGet(MenuItem::new);
        menu.setLabel(label);
        menu.setIcon(icon);
        menu.setRoute(route);
        menu.setOrder(order);
        menu.setIsVisible(true);
        menuItemRepository.save(menu);
    }
}
