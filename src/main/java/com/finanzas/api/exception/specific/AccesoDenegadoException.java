package com.finanzas.api.exception.specific;

import com.finanzas.api.exception.AppException;
import org.springframework.http.HttpStatus;

public class AccesoDenegadoException extends AppException {
    public AccesoDenegadoException() {
        super("Acceso denegado", "ACCESO_DENEGADO", HttpStatus.FORBIDDEN);
    }
}
