package com.vactis.controller;

import com.vactis.dto.role.RoleCreateDTO;
import com.vactis.model.Roles.Roles;
import com.vactis.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/roles")
public class RoleController {
    @Autowired
    private final RoleService roleService ;

    @GetMapping("/")
    public List<Roles> getAllRoles(){
        return roleService.getAllRoles();
    }

    @PostMapping("/add")
    public void createRole(@RequestBody RoleCreateDTO dto){
        Roles role = new Roles();
        role.setNameRole(dto.getNameRole());
        role.setDescription(dto.getDescription());
        roleService.createRole(role, dto.getMenuIds());
    }

    @PutMapping("/{roleId}")
    public void updateRole(@PathVariable Long roleId, @RequestBody RoleCreateDTO dto) {
        Roles role = new Roles();
        role.setNameRole(dto.getNameRole());
        role.setDescription(dto.getDescription());
        roleService.updateRole(roleId, role, dto.getMenuIds());
    }

    @DeleteMapping("/{roleId}")
    public void deleteRole(@PathVariable Long roleId) {
        roleService.deleteRole(roleId);
    }


}
