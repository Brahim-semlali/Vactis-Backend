package com.vactis.repository.menu;

import com.vactis.model.menu.MenuUserAccess;
import com.vactis.model.menu.MenuUserAccessId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// Repository JPA pour la table des habilitations d'accès menu par utilisateur
@Repository
public interface MenuUserAccessRepository extends JpaRepository<MenuUserAccess, MenuUserAccessId> {

    // Retourne tous les accès menu accordés à un utilisateur
    List<MenuUserAccess> findByIdUser(Long idUser);

    // Supprime tous les accès associés à un élément de menu
    void deleteByIdMenu(Long idMenu);
}
