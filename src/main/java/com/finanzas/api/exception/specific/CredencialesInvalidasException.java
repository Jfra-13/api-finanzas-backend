package com.finanzas.api.exception.specific;

import com.finanzas.api.exception.AppException;
import org.springframework.http.HttpStatus;

public class CredencialesInvalidasException extends AppException {
    public CredencialesInvalidasException() {
        super("Credenciales incorrectas", "CREDENCIALES_INVALIDAS", HttpStatus.UNAUTHORIZED);
    }
}
