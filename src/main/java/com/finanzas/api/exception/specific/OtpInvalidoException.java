package com.finanzas.api.exception.specific;

import com.finanzas.api.exception.AppException;
import org.springframework.http.HttpStatus;

public class OtpInvalidoException extends AppException {
    public OtpInvalidoException() {
        super("OTP inválido", "OTP_INVALIDO", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
