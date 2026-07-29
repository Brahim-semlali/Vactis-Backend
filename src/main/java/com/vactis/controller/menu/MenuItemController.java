package com.vactis.controller.menu;

import com.vactis.model.auth.Users;
import com.vactis.model.menu.MenuItem;
import com.vactis.service.menu.MenuItemService;


import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/menu")
public class MenuItemController {
    private final MenuItemService menuItemService;

    @GetMapping("/getAll")
    public List<MenuItem> getAllMenu(@AuthenticationPrincipal Users user){
        return menuItemService.getAllMenu(user);
    }

    @PostMapping("/AddMenu")
    public void AddMenu(@RequestBody MenuItem menuItem){
        menuItemService.AddMenu(menuItem);
    }

    @DeleteMapping("/Delete/{id}")
    public void DeleteMenu(@PathVariable Long id ){
        menuItemService.DeleteMenu(id);
    }

    @PutMapping("/Update/{id}")
    public void UpdateManu(@PathVariable Long id , @RequestBody MenuItem menuItem){
        menuItemService.UpdateMenu(id,menuItem);
    }

    @PostMapping("/GiveAccess/{idMenu}/{idUser}")
    public void GiveAccess(@PathVariable Long idMenu, @PathVariable Long idUser){
        menuItemService.GiveAccess(idMenu, idUser);
    }

}
