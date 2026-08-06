package com.vactis.controller;

import com.vactis.model.auth.Users;
import com.vactis.model.menu.MenuItem;
import com.vactis.service.MenuItemService;

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

    // Retourne les éléments de menu autorisés pour l'utilisateur connecté
    @GetMapping("/getAll")
    public List<MenuItem> getAllMenu(@AuthenticationPrincipal Users user){
        return menuItemService.getAllMenu(user);
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

    // Accorde l'accès à une rubrique de menu pour un utilisateur donné
    @PostMapping("/GiveAccess/{idMenu}/{idUser}")
    public void GiveAccess(@PathVariable Long idMenu, @PathVariable Long idUser){
        menuItemService.GiveAccess(idMenu, idUser);
    }
}
