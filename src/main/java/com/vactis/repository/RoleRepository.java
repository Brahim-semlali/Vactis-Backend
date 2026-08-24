package com.vactis.repository;

import com.vactis.model.Roles.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Roles, Long> {

	java.util.Optional<Roles> findByNameRoleIgnoreCase(String nameRole);

	@Query("""
	       SELECT DISTINCT role
	       FROM Roles role
	       LEFT JOIN FETCH role.menuItems item
	       LEFT JOIN FETCH item.menuPrincipal
	       WHERE role.idRole = :roleId
	       """)
	Optional<Roles> findWithMenuItemsById(@Param("roleId") Long roleId);
}
