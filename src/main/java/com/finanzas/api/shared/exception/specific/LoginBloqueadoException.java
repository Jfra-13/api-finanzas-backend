package com.finanzas.api.shared.exception.specific;

import com.finanzas.api.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class LoginBloqueadoException extends AppException {
    public LoginBloqueadoException() {
        super("Demasiados intentos fallidos. Intenta de nuevo en 15 minutos.", "LOGIN_BLOQUEADO", HttpStatus.TOO_MANY_REQUESTS);
    }
}
