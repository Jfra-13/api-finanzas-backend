package com.finanzas.api.model.dto;

import lombok.Data;

@Data
public class UsuarioRegistroDTO {
    private String email;
    private String password;
    private String oficio;
}