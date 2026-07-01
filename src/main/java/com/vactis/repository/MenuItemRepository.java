package com.vactis.repository;

import com.vactis.model.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    @Query("""
            SELECT m FROM MenuItem m
            WHERE m.isVisible IS NULL OR m.isVisible = true
            ORDER BY m.order
            """)
    List<MenuItem> findByIsVisibleTrueOrderByOrder();
}
