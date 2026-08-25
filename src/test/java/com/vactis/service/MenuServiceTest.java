package com.vactis.service;

import com.vactis.model.Roles.Roles;
import com.vactis.model.auth.Users;
import com.vactis.model.menu.MenuItem;
import com.vactis.model.menu.MenuPrincipal;
import com.vactis.repository.RoleRepository;
import com.vactis.repository.menu.MenuPrincipalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock private MenuPrincipalRepository principalRepository;
    @Mock private RoleRepository roleRepository;
    @InjectMocks private MenuService service;

    @Test
    void userWithoutRoleGetsEmptyMenu() {
        assertEquals(List.of(), service.getMenuPourUtilisateur(new Users()));
    }

    @Test
    void userMenuIsGroupedAndSortedByPrincipalAndItemOrder() {
        MenuPrincipal principal = new MenuPrincipal();
        principal.setIdMenuPrinc(1L);
        principal.setNom("Principal");
        principal.setIcone("home");
        principal.setOrdre(1);

        MenuItem later = item("Later", "/later", 2, principal);
        MenuItem first = item("First", "/first", 1, principal);
        Roles role = new Roles();
        role.setIdRole(2L);
        role.setMenuItems(List.of(later, first));
        Users user = new Users();
        user.setRoles(role);
        when(roleRepository.findWithMenuItemsById(2L)).thenReturn(Optional.of(role));

        var tree = service.getMenuPourUtilisateur(user);

        assertEquals(1, tree.size());
        assertEquals("Principal", tree.get(0).getNom());
        assertEquals("First", tree.get(0).getSousMenus().get(0).getLabel());
        assertEquals("Later", tree.get(0).getSousMenus().get(1).getLabel());
    }

    private MenuItem item(String label, String route, int order, MenuPrincipal principal) {
        MenuItem item = new MenuItem();
        item.setLabel(label);
        item.setRoute(route);
        item.setOrder(order);
        item.setMenuPrincipal(principal);
        item.setIsVisible(true);
        return item;
    }
}
