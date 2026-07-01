package com.vactis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "menu_user_access")
@IdClass(MenuUserAccessId.class)
@NoArgsConstructor
public class MenuUserAccess {

    @Id
    @Column(name = "id_menu")
    private Long idMenu;

    @Id
    @Column(name = "id_user")
    private Long idUser;
}
