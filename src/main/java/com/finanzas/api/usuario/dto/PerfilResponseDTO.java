package com.finanzas.api.usuario.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Single source of truth for the profile screens. fotoUrl and plan are part of
// the contract but always null for now: no storage backs them yet, and shipping
// them nullable lets the client bind once without a breaking change later.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfilResponseDTO {
    private Long id;
    private String nombre;
    private String email;
    private String telefono;
    private String fotoUrl;
    private String tipoNegocio;
    private String plan;
}
