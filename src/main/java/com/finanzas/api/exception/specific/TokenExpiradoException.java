package com.finanzas.api.exception.specific;

import com.finanzas.api.exception.AppException;
import org.springframework.http.HttpStatus;

public class TokenExpiradoException extends AppException {
    public TokenExpiradoException() {
        super("Token expirado", "TOKEN_EXPIRADO", HttpStatus.UNAUTHORIZED);
    }
}
