package com.vactis.controller;

import com.vactis.model.Controle.Controle;
import com.vactis.model.Controle.TypeControle;
import com.vactis.service.ControleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/controle")
public class ControleController {
    private final ControleService controleService;

    @GetMapping("/type/{type}")
    public List<Controle> findByType(@PathVariable TypeControle type) {
        return controleService.findByType(type);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Controle createControle(@RequestBody Controle controle) {
        return controleService.create(controle);
    }

    @PutMapping("/{idControle}")
    public Controle updateControle(@PathVariable Long idControle, @RequestBody Controle controle) {
        return controleService.update(idControle, controle);
    }

    @DeleteMapping("/{idControle}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteControle(@PathVariable Long idControle) {
        controleService.delete(idControle);
    }
}
