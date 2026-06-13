package com.finanzas.api.shared.exception.specific;

import com.finanzas.api.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class MetaNoEncontradaException extends AppException {
    public MetaNoEncontradaException() {
        super("No hay una meta activa para el período actual", "META_NO_ENCONTRADA", HttpStatus.NOT_FOUND);
    }
}
