package com.vactis.controller;

import com.vactis.model.auth.Users;
import com.vactis.model.menu.MenuItem;
import com.vactis.dto.menu.MenuTreeResponse;
import com.vactis.service.MenuItemService;
import com.vactis.service.MenuService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Contrôleur REST pour la gestion du menu dynamique et des accès utilisateurs
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/menu")
public class MenuItemController {
    private final MenuItemService menuItemService;
    private final MenuService menuService;

    @GetMapping("/mon-menu")
    public List<MenuTreeResponse> getMonMenu(@AuthenticationPrincipal Users user) {
        return menuService.getMenuPourUtilisateur(user);
    }

    // Ajoute une nouvelle rubrique de menu
    @PostMapping("/AddMenu")
    public void AddMenu(@RequestBody MenuItem menuItem){
        menuItemService.AddMenu(menuItem);
    }

    // Supprime une rubrique de menu et ses accès associés
    @DeleteMapping("/Delete/{id}")
    public void DeleteMenu(@PathVariable Long id ){
        menuItemService.DeleteMenu(id);
    }

    // Met à jour les propriétés d'un élément de menu
    @PutMapping("/Update/{id}")
    public void UpdateManu(@PathVariable Long id , @RequestBody MenuItem menuItem){
        menuItemService.UpdateMenu(id,menuItem);
    }

}
