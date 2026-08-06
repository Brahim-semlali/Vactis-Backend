package com.vactis.service;

import com.vactis.model.auth.Role;
import com.vactis.model.auth.Users;
import com.vactis.model.menu.MenuItem;
import com.vactis.model.menu.MenuUserAccess;
import com.vactis.repository.menu.MenuItemRepository;
import com.vactis.repository.menu.MenuUserAccessRepository;

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

    // Retourne les menus visibles autorisés pour l'utilisateur (tous si ADMIN, filtrés si USER)
    public List<MenuItem> getAllMenu(Users user){
        if (user.getRole() == Role.ADMIN) {
            return menuItemRepository.findByIsVisibleTrueOrderByOrder();
        }

        List<Long> allowedMenuIds = menuUserAccessRepository.findByIdUser(user.getId()).stream()
                .map(MenuUserAccess::getIdMenu)
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
