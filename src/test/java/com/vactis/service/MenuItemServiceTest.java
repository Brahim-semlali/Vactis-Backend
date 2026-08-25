package com.vactis.service;

import com.vactis.model.menu.MenuItem;
import com.vactis.repository.menu.MenuItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuItemServiceTest {

    @Mock private MenuItemRepository repository;
    @InjectMocks private MenuItemService service;

    @Test
    void addMenuDefaultsVisibilityToTrue() {
        MenuItem menu = new MenuItem();

        service.AddMenu(menu);

        assertEquals(true, menu.getIsVisible());
        verify(repository).save(menu);
    }

    @Test
    void updateMenuCopiesEditableFields() {
        MenuItem existing = new MenuItem();
        MenuItem payload = new MenuItem();
        payload.setIcon("chart");
        payload.setRoute("/rapport");
        payload.setOrder(3);
        payload.setLabel("Rapport");
        payload.setIsVisible(false);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.UpdateMenu(1L, payload);

        assertEquals("chart", existing.getIcon());
        assertEquals("/rapport", existing.getRoute());
        assertEquals(3, existing.getOrder());
        assertEquals("Rapport", existing.getLabel());
        assertEquals(false, existing.getIsVisible());
        verify(repository).save(existing);
    }
}
