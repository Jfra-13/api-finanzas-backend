package com.finanzas.api.exception.specific;

import com.finanzas.api.exception.AppException;
import org.springframework.http.HttpStatus;

public class TransaccionNoEncontradaException extends AppException {
    public TransaccionNoEncontradaException() {
        super("Transacción no encontrada", "TRANSACCION_NO_ENCONTRADA", HttpStatus.NOT_FOUND);
    }
}
