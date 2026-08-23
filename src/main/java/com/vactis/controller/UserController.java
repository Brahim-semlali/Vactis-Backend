package com.vactis.controller;

import com.vactis.model.auth.Users;
import com.vactis.dto.auth.UserAdminRequest;
import com.vactis.service.auth.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    @Autowired
    private final UserDetailsServiceImpl userDetailsService;

    @GetMapping("/")
    public List<Users> getAllUsers(){
        return userDetailsService.getAllUsers();
    }

    @PostMapping
    public ResponseEntity<Users> createUser(@Valid @RequestBody UserAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userDetailsService.createUser(request));
    }

    @PutMapping("/{userId}")
    public Users updateUser(@PathVariable Long userId, @Valid @RequestBody UserAdminRequest request) {
        return userDetailsService.updateUser(userId, request);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long userId) {
        userDetailsService.deleteUser(userId);
    }

    @PutMapping("/{userId}/role/{roleId}")
    public void assignRole(@PathVariable Long userId, @PathVariable Long roleId) {
        userDetailsService.assignRole(userId, roleId);
    }

}
