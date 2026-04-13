package com.finanzas.api.model.dto;
import lombok.Data;

@Data
public class LoginDTO {
    private String email;
    private String password;
}