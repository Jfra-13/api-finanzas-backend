package com.finanzas.api.exception.specific;

import com.finanzas.api.exception.AppException;
import org.springframework.http.HttpStatus;

public class UsuarioNoEncontradoException extends AppException {
    public UsuarioNoEncontradoException() {
        super("Usuario no encontrado", "USUARIO_NO_ENCONTRADO", HttpStatus.NOT_FOUND);
    }
}
