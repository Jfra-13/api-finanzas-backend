package com.finanzas.api.shared.exception.specific;

import com.finanzas.api.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class OtpBloqueadoException extends AppException {
    public OtpBloqueadoException() {
        super("Demasiados intentos fallidos. Solicita un nuevo código.", "OTP_BLOQUEADO", HttpStatus.TOO_MANY_REQUESTS);
    }
}
