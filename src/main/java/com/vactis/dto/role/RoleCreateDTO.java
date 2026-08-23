package com.vactis.dto.role;

import lombok.Data;

import java.util.List;

@Data
public class RoleCreateDTO {
    private String nameRole;
    private String description;
    private List<Long> menuIds;
}
