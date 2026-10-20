package com.finanzas.api.shared.exception.specific;

import com.finanzas.api.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class RateLimitExcedidoException extends AppException {
    public RateLimitExcedidoException() {
        super("Demasiadas solicitudes. Intenta de nuevo en un minuto.", "RATE_LIMIT_EXCEDIDO", HttpStatus.TOO_MANY_REQUESTS);
    }
}
