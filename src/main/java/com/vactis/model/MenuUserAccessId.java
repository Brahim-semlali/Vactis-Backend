package com.vactis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class MenuUserAccessId implements Serializable {
    private Long idMenu;
    private Long idUser;
}
