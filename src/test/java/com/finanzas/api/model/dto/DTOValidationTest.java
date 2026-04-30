package com.finanzas.api.model.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DTOValidationTest {

    private Validator validator;

    @BeforeEach
    public void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    public void testUsuarioRegistroDTO_Valid() {
        UsuarioRegistroDTO dto = new UsuarioRegistroDTO();
        dto.setNombre("Juan Perez");
        dto.setEmail("juan@example.com");
        dto.setPassword("Password123");
        dto.setTipoNegocio("BODEGA");

        Set<ConstraintViolation<UsuarioRegistroDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty());
    }

    @Test
    public void testUsuarioRegistroDTO_InvalidEmail() {
        UsuarioRegistroDTO dto = new UsuarioRegistroDTO();
        dto.setNombre("Juan Perez");
        dto.setEmail("email-invalido");
        dto.setPassword("Password123");

        Set<ConstraintViolation<UsuarioRegistroDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    public void testUsuarioRegistroDTO_WeakPassword() {
        UsuarioRegistroDTO dto = new UsuarioRegistroDTO();
        dto.setNombre("Juan Perez");
        dto.setEmail("juan@example.com");
        dto.setPassword("123"); // Muy corta y sin letras

        Set<ConstraintViolation<UsuarioRegistroDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    public void testUsuarioRegistroDTO_InvalidTipoNegocio() {
        UsuarioRegistroDTO dto = new UsuarioRegistroDTO();
        dto.setNombre("Juan Perez");
        dto.setEmail("juan@example.com");
        dto.setPassword("Password123");
        dto.setTipoNegocio("INVALIDO");

        Set<ConstraintViolation<UsuarioRegistroDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }
}
