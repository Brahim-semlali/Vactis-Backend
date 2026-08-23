package com.vactis.service;

import com.vactis.model.Roles.Roles;
import com.vactis.model.menu.MenuItem;
import com.vactis.model.auth.Users;
import com.vactis.repository.RoleRepository;
import com.vactis.repository.auth.UserRepository;
import com.vactis.repository.menu.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;

    @Transactional
    public void createRole(Roles role, List<Long> menuIds) {
        role.setMenuItems(findMenuItems(menuIds));
        roleRepository.save(role);
    }

    @Transactional
    public void updateRole(Long roleId, Roles roleData, List<Long> menuIds) {
        Roles role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Rôle introuvable : " + roleId));
        role.setNameRole(roleData.getNameRole());
        role.setDescription(roleData.getDescription());
        role.setMenuItems(findMenuItems(menuIds));
        roleRepository.save(role);
    }

    @Transactional
    public void deleteRole(Long roleId) {
        Roles role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Rôle introuvable : " + roleId));

        List<Users> users = userRepository.findAll().stream()
                .filter(user -> user.getRoles() != null && roleId.equals(user.getRoles().getIdRole()))
                .toList();
        users.forEach(user -> user.setRoles(null));
        userRepository.saveAll(users);
        roleRepository.delete(role);
    }

    private List<MenuItem> findMenuItems(List<Long> menuIds) {
        return menuIds == null ? List.of() : menuItemRepository.findAllById(menuIds);
    }

    public List<Roles> getAllRoles() {
        return roleRepository.findAll();
    }

}
