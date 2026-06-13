package com.finanzas.api.shared.exception.specific;

import com.finanzas.api.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class EmailDuplicadoException extends AppException {
    public EmailDuplicadoException() {
        super("Email ya registrado", "EMAIL_DUPLICADO", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
