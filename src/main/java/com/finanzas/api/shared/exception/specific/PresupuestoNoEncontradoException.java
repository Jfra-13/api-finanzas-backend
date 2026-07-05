package com.finanzas.api.shared.exception.specific;

import com.finanzas.api.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class PresupuestoNoEncontradoException extends AppException {
    public PresupuestoNoEncontradoException() {
        super("Presupuesto no encontrado", "PRESUPUESTO_NO_ENCONTRADO", HttpStatus.NOT_FOUND);
    }
}
