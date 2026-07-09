package com.finanzas.api.transaccion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// A single financial-health signal. tipo is "ALERTA" or "FELICITACION";
// the frontend branches on the stable code, never on the message text.
// severidad (ALTA|MEDIA|BAJA) lets the client sort and colour; categoriaId is
// present only when the signal points at a specific category.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AlertaDTO {
    private String tipo;
    private String code;
    private String severidad;
    private String mensaje;
    private Long categoriaId;

    // Signals not tied to a category leave categoriaId null.
    public AlertaDTO(String tipo, String code, String severidad, String mensaje) {
        this(tipo, code, severidad, mensaje, null);
    }
}
