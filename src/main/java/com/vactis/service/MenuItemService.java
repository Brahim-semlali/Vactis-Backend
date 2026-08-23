package com.vactis.service;

import com.vactis.model.auth.Users;
import com.vactis.model.Roles.Roles;
import com.vactis.model.menu.MenuItem;
import com.vactis.model.menu.MenuUserAccess;
import com.vactis.repository.menu.MenuItemRepository;
import com.vactis.repository.menu.MenuUserAccessRepository;
import com.vactis.repository.RoleRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Service métier pour la gestion du menu dynamique et des accès utilisateurs
@Service
@RequiredArgsConstructor
public class MenuItemService {
    private final MenuItemRepository menuItemRepository;
    private final MenuUserAccessRepository menuUserAccessRepository;
    private final RoleRepository roleRepository;

    // Ajoute une nouvelle rubrique de menu et configure ses accès
    @Transactional
    public void AddMenu(MenuItem menuItem){
        if (menuItem.getIsVisible() == null) {
            menuItem.setIsVisible(true);
        }
        MenuItem saved = menuItemRepository.save(menuItem);
        saveAccess(saved.getIdMenu(), menuItem.getAllowedUserIds());
    }

    // Supprime une rubrique de menu et toutes ses autorisations associées
    @Transactional
    public void DeleteMenu(Long id ){
        menuUserAccessRepository.deleteByIdMenu(id);
        menuItemRepository.deleteById(id);
    }

    // Met à jour les propriétés d'un menu et réassocie ses accès
    @Transactional
    public void UpdateMenu(Long id , MenuItem menuItem){
        MenuItem Existed = menuItemRepository.findById(id).orElseThrow();

        Existed.setIcon(menuItem.getIcon());
        Existed.setRoute(menuItem.getRoute());
        Existed.setOrder(menuItem.getOrder());
        Existed.setLabel(menuItem.getLabel());
        Existed.setIsVisible(menuItem.getIsVisible());

        menuItemRepository.save(Existed);

        menuUserAccessRepository.deleteByIdMenu(id);
        saveAccess(id, menuItem.getAllowedUserIds());
    }

    // Retourne les menus visibles associés au rôle de l'utilisateur connecté.
    @Transactional(readOnly = true)
    public List<MenuItem> getAllMenu(Users user){
        if (user == null || user.getRoles() == null) {
            return List.of();
        }

        Roles role = roleRepository.findById(user.getRoles().getIdRole()).orElse(null);
        if (role == null || role.getMenuItems() == null) {
            return List.of();
        }

        List<Long> allowedMenuIds = role.getMenuItems().stream()
                .map(MenuItem::getIdMenu)
                .toList();

        return menuItemRepository.findByIsVisibleTrueOrderByOrder().stream()
                .filter(item -> allowedMenuIds.contains(item.getIdMenu()))
                .toList();
    }

    // Accorde l'accès à une rubrique de menu pour un utilisateur donné
    @Transactional
    public void GiveAccess(Long idMenu, Long idUser){
        MenuUserAccess access = new MenuUserAccess();
        access.setIdMenu(idMenu);
        access.setIdUser(idUser);
        menuUserAccessRepository.save(access);
    }

    // Enregistre les autorisations d'accès pour une liste d'utilisateurs
    private void saveAccess(Long idMenu, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        for (Long idUser : userIds) {
            MenuUserAccess access = new MenuUserAccess();
            access.setIdMenu(idMenu);
            access.setIdUser(idUser);
            menuUserAccessRepository.save(access);
        }
    }
}
