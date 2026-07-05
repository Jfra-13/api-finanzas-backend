package com.finanzas.api.shared.exception.specific;

import com.finanzas.api.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class RangoFechasInvalidoException extends AppException {
    public RangoFechasInvalidoException() {
        super("El rango de fechas es inválido: 'desde' no puede ser posterior a 'hasta'", "RANGO_FECHAS_INVALIDO", HttpStatus.BAD_REQUEST);
    }
}
