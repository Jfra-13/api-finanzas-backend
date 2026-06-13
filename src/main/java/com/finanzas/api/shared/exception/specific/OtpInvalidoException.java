package com.finanzas.api.shared.exception.specific;

import com.finanzas.api.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class OtpInvalidoException extends AppException {
    public OtpInvalidoException() {
        super("OTP inválido", "OTP_INVALIDO", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
