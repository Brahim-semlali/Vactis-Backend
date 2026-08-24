package com.vactis.service;

import com.vactis.dto.menu.MenuTreeResponse;
import com.vactis.dto.menu.SousMenuDto;
import com.vactis.model.auth.Users;
import com.vactis.model.menu.MenuItem;
import com.vactis.model.menu.MenuPrincipal;
import com.vactis.repository.menu.MenuPrincipalRepository;
import com.vactis.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {
    private final MenuPrincipalRepository menuPrincipalRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<MenuTreeResponse> getMenuPourUtilisateur(Users user) {
        if (user == null || user.getRoles() == null || user.getRoles().getIdRole() == null) {
            return List.of();
        }

        return roleRepository.findWithMenuItemsById(user.getRoles().getIdRole())
            .map(role -> buildTree(role.getMenuItems() == null ? List.of() : role.getMenuItems(), false))
            .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public List<MenuTreeResponse> getMenuTreeComplet() {
        return buildTree(menuPrincipalRepository.findAll().stream()
                .flatMap(principal -> principal.getSousMenus().stream())
                .toList(), true);
    }

    private List<MenuTreeResponse> buildTree(List<MenuItem> items, boolean includeInvisible) {
        Map<Long, List<MenuItem>> grouped = items.stream()
                .filter(item -> includeInvisible || Boolean.TRUE.equals(item.getIsVisible()) || item.getIsVisible() == null)
                .filter(item -> item.getMenuPrincipal() != null)
            .collect(Collectors.groupingBy(item -> item.getMenuPrincipal().getIdMenuPrinc(), LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
            .sorted(Comparator.comparingInt(entry -> entry.getValue().get(0).getMenuPrincipal().getOrdre()))
                .map(entry -> new MenuTreeResponse(
                entry.getValue().get(0).getMenuPrincipal().getIdMenuPrinc(),
                entry.getValue().get(0).getMenuPrincipal().getNom(),
                entry.getValue().get(0).getMenuPrincipal().getIcone(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparingInt(MenuItem::getOrder))
                                .map(item -> new SousMenuDto(item.getIdMenu(), item.getLabel(), item.getIcon(), item.getRoute()))
                                .toList()))
                .toList();
    }
}