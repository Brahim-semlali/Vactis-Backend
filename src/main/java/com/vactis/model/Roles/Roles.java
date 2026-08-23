package com.vactis.model.Roles;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vactis.model.auth.Users;
import com.vactis.model.menu.MenuItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Roles {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idRole;

    private String nameRole;

    private String description;

    @JsonIgnore
    @OneToMany(mappedBy = "roles")
    private List<Users> users;

    @ManyToMany
    @JoinTable(
            name = "role_menu",
            joinColumns = @JoinColumn(name = "id_role"),
            inverseJoinColumns = @JoinColumn(name = "id_menu")
    )
    private List<MenuItem> menuItems;
}

