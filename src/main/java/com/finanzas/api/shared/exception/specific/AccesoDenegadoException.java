package com.finanzas.api.shared.exception.specific;

import com.finanzas.api.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class AccesoDenegadoException extends AppException {
    public AccesoDenegadoException() {
        super("Acceso denegado", "ACCESO_DENEGADO", HttpStatus.FORBIDDEN);
    }
}
