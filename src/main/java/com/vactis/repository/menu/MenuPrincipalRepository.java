package com.vactis.repository.menu;

import com.vactis.model.menu.MenuPrincipal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MenuPrincipalRepository extends JpaRepository<MenuPrincipal, Long> {
	java.util.Optional<MenuPrincipal> findByNomIgnoreCase(String nom);
}