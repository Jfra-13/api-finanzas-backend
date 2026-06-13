package com.finanzas.api.shared.exception.specific;

import com.finanzas.api.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class CategoriaNoEncontradaException extends AppException {
    public CategoriaNoEncontradaException() {
        super("Categoría no encontrada", "CATEGORIA_NO_ENCONTRADA", HttpStatus.NOT_FOUND);
    }
}
