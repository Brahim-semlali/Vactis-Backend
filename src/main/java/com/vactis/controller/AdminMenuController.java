package com.vactis.controller;

import com.vactis.dto.menu.MenuTreeResponse;
import com.vactis.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminMenuController {
    private final MenuService menuService;

    @GetMapping("/menu-tree-complet")
    public List<MenuTreeResponse> getMenuTreeComplet() {
        return menuService.getMenuTreeComplet();
    }
}