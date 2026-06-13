package com.finanzas.api.shared.exception.specific;

import com.finanzas.api.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class RefreshTokenInvalidoException extends AppException {
    public RefreshTokenInvalidoException() {
        super("El refresh token es inválido, expiró o ya fue usado", "REFRESH_TOKEN_INVALIDO", HttpStatus.UNAUTHORIZED);
    }
}
