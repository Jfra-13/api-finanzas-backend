package com.finanzas.api.exception.specific;

import com.finanzas.api.exception.AppException;
import org.springframework.http.HttpStatus;

public class TokenInvalidoException extends AppException {
    public TokenInvalidoException() {
        super("Token inválido", "TOKEN_INVALIDO", HttpStatus.UNAUTHORIZED);
    }
}
