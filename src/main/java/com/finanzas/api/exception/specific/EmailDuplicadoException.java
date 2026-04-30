package com.finanzas.api.exception.specific;

import com.finanzas.api.exception.AppException;
import org.springframework.http.HttpStatus;

public class EmailDuplicadoException extends AppException {
    public EmailDuplicadoException() {
        super("Email ya registrado", "EMAIL_DUPLICADO", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
