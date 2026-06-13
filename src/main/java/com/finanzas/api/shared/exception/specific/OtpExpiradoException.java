package com.finanzas.api.shared.exception.specific;

import com.finanzas.api.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class OtpExpiradoException extends AppException {
    public OtpExpiradoException() {
        super("OTP expirado", "OTP_EXPIRADO", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
