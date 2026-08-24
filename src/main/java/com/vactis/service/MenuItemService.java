package com.vactis.service;

import com.vactis.model.menu.MenuItem;
import com.vactis.repository.menu.MenuItemRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Service métier pour la gestion du menu dynamique et des accès utilisateurs
@Service
@RequiredArgsConstructor
public class MenuItemService {
    private final MenuItemRepository menuItemRepository;

    // Ajoute une nouvelle rubrique de menu et configure ses accès
    @Transactional
    public void AddMenu(MenuItem menuItem){
        if (menuItem.getIsVisible() == null) {
            menuItem.setIsVisible(true);
        }
        menuItemRepository.save(menuItem);
    }

    // Supprime une rubrique de menu et toutes ses autorisations associées
    @Transactional
    public void DeleteMenu(Long id ){
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
    }
}
