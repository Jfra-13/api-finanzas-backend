package com.finanzas.api.transaccion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// A single financial-health signal. tipo is "ALERTA" or "FELICITACION";
// the frontend branches on the stable code, never on the message text.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AlertaDTO {
    private String tipo;
    private String code;
    private String mensaje;
}
