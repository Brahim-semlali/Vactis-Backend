package com.vactis.repository;

import com.vactis.model.MenuUserAccess;
import com.vactis.model.MenuUserAccessId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuUserAccessRepository extends JpaRepository<MenuUserAccess, MenuUserAccessId> {

    List<MenuUserAccess> findByIdUser(Long idUser);

    void deleteByIdMenu(Long idMenu);
}
