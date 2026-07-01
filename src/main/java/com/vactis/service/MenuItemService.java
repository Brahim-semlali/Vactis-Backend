package com.vactis.service;

import com.vactis.model.MenuItem;
import com.vactis.model.MenuUserAccess;
import com.vactis.model.Role;
import com.vactis.model.Users;
import com.vactis.repository.MenuItemRepository;
import com.vactis.repository.MenuUserAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuItemService {
    private final MenuItemRepository menuItemRepository;
    private final MenuUserAccessRepository menuUserAccessRepository;

    //Ajouter Menu
    @Transactional
    public void AddMenu(MenuItem menuItem){
        if (menuItem.getIsVisible() == null) {
            menuItem.setIsVisible(true);
        }
        MenuItem saved = menuItemRepository.save(menuItem);
        saveAccess(saved.getIdMenu(), menuItem.getAllowedUserIds());
    }

    //supprimer Menu
    @Transactional
    public void DeleteMenu(Long id ){
        menuUserAccessRepository.deleteByIdMenu(id);
        menuItemRepository.deleteById(id);
    }

    //modifier Menu
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

    //afficher Menu
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

    //donner acces a un user pour un menu
    @Transactional
    public void GiveAccess(Long idMenu, Long idUser){
        MenuUserAccess access = new MenuUserAccess();
        access.setIdMenu(idMenu);
        access.setIdUser(idUser);
        menuUserAccessRepository.save(access);
    }

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
