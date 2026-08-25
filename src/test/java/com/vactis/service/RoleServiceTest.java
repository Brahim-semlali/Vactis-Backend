package com.vactis.service;

import com.vactis.model.Roles.Roles;
import com.vactis.model.auth.Users;
import com.vactis.model.menu.MenuItem;
import com.vactis.repository.RoleRepository;
import com.vactis.repository.auth.UserRepository;
import com.vactis.repository.menu.MenuItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private RoleService roleService;

    @Test
    void createRoleAssignsSelectedMenus() {
        Roles role = new Roles();
        MenuItem menu = new MenuItem();
        when(menuItemRepository.findAllById(List.of(1L))).thenReturn(List.of(menu));

        roleService.createRole(role, List.of(1L));

        assertEquals(List.of(menu), role.getMenuItems());
        verify(roleRepository).save(role);
    }

    @Test
    void deleteRoleDetachesUsersBeforeDeletingRole() {
        Roles role = new Roles();
        role.setIdRole(4L);
        Users user = new Users();
        user.setRoles(role);
        when(roleRepository.findById(4L)).thenReturn(Optional.of(role));
        when(userRepository.findAll()).thenReturn(List.of(user));

        roleService.deleteRole(4L);

        assertEquals(null, user.getRoles());
        verify(userRepository).saveAll(List.of(user));
        verify(roleRepository).delete(role);
    }

    @Test
    void updateRoleRejectsUnknownRole() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> roleService.updateRole(99L, new Roles(), List.of()));
        verify(roleRepository, never()).save(any());
    }
}
