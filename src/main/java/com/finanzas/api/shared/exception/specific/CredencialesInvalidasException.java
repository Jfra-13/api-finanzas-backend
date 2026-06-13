package com.finanzas.api.shared.exception.specific;

import com.finanzas.api.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class CredencialesInvalidasException extends AppException {
    public CredencialesInvalidasException() {
        super("Credenciales incorrectas", "CREDENCIALES_INVALIDAS", HttpStatus.UNAUTHORIZED);
    }
}
