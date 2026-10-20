package com.finanzas.api.shared.exception.specific;

import com.finanzas.api.shared.exception.AppException;
import org.springframework.http.HttpStatus;

// Same code the type-mismatch handler emits, so the client branches on a single
// PARAMETRO_INVALIDO regardless of whether Spring or the service rejected the param.
public class ParametroInvalidoException extends AppException {
    public ParametroInvalidoException(String mensaje) {
        super(mensaje, "PARAMETRO_INVALIDO", HttpStatus.BAD_REQUEST);
    }

    // Window/range params drive per-bucket queries, so they are bounded instead of clamped.
    public static int enRango(String nombre, int valor, int min, int max) {
        if (valor < min || valor > max) {
            throw new ParametroInvalidoException(nombre + " debe estar entre " + min + " y " + max);
        }
        return valor;
    }
}
