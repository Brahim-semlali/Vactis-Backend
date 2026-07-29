package com.vactis.repository.menu;

import com.vactis.model.menu.MenuUserAccess;
import com.vactis.model.menu.MenuUserAccessId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuUserAccessRepository extends JpaRepository<MenuUserAccess, MenuUserAccessId> {

    List<MenuUserAccess> findByIdUser(Long idUser);

    void deleteByIdMenu(Long idMenu);
}
