package com.finanzas.api.controller;

import com.finanzas.api.model.dto.UsuarioRegistroDTO;
import com.finanzas.api.model.entity.Usuario;
import com.finanzas.api.repository.UsuarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;

    public UsuarioController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/registro")
    public ResponseEntity<String> registrarUsuario(@RequestBody UsuarioRegistroDTO dto) {

        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setEmail(dto.getEmail());
        nuevoUsuario.setPasswordHash(dto.getPassword());
        nuevoUsuario.setOficio(dto.getOficio());

        usuarioRepository.save(nuevoUsuario);

        return ResponseEntity.ok("¡Usuario registrado con exito!");
    }
    // Este es nuestro Health Check
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("¡El servidor backend de Finanzas está vivo y funcionando!");
    }
}