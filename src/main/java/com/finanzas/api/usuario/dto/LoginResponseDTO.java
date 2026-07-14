package com.finanzas.api.usuario.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponseDTO {
    private String token;
    private String refreshToken;
    private Long usuarioId;
    private String nombre;
    private String email;
    private String tipoNegocio;

    // True only when THIS login reactivated an account that was pending deletion,
    // so the client can tell the user the scheduled deletion was cancelled.
    private boolean cuentaReactivada;
}
