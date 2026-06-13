package com.finanzas.api.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshRequestDTO {
    @NotBlank(message = "{refreshToken.notblank}")
    private String refreshToken;
}
