package com.vactis.repository.menu;

import com.vactis.model.menu.MenuItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

// Repository JPA pour les rubriques du menu de navigation
@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    // Retourne les éléments de menu visibles, triés par ordre d'affichage
    @Query("""
            SELECT m FROM MenuItem m
            WHERE m.isVisible IS NULL OR m.isVisible = true
            ORDER BY m.order
            """)
    List<MenuItem> findByIsVisibleTrueOrderByOrder();
}
