package com.finanzas.api.shared.exception.specific;

import com.finanzas.api.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class TransaccionNoEncontradaException extends AppException {
    public TransaccionNoEncontradaException() {
        super("Transacción no encontrada", "TRANSACCION_NO_ENCONTRADA", HttpStatus.NOT_FOUND);
    }
}
