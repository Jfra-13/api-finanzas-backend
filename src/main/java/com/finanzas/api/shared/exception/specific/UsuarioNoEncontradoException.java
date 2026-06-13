package com.finanzas.api.shared.exception.specific;

import com.finanzas.api.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class UsuarioNoEncontradoException extends AppException {
    public UsuarioNoEncontradoException() {
        super("Usuario no encontrado", "USUARIO_NO_ENCONTRADO", HttpStatus.NOT_FOUND);
    }
}
