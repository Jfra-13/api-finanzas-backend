package com.finanzas.api.repository;

import com.finanzas.api.model.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Con solo nombrar el método así, Spring Boot ya sabe que debe hacer
    // un "SELECT * FROM usuarios WHERE email = ?"
    Optional<Usuario> findByEmail(String email);

}