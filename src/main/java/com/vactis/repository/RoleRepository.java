package com.vactis.repository;

import com.vactis.model.Roles.Roles;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Roles, Long> {

	java.util.Optional<Roles> findByNameRoleIgnoreCase(String nameRole);
}
