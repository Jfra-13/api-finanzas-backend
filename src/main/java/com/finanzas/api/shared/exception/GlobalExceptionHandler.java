package com.finanzas.api.shared.exception;

import com.finanzas.api.shared.dto.ErrorDetailDTO;
import com.finanzas.api.shared.dto.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponseDTO> handleAppException(AppException ex, HttpServletRequest request) {
        log.error("AppException: {} - {}", ex.getCode(), ex.getMessage());
        ErrorResponseDTO response = ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatus().value())
                .code(ex.getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation error at {}: {}", request.getRequestURI(), ex.getMessage());
        List<ErrorDetailDTO> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> ErrorDetailDTO.builder()
                        .field(error.getField())
                        .rejectedValue(error.getRejectedValue())
                        .message(error.getDefaultMessage())
                        .build())
                .collect(Collectors.toList());

        ErrorResponseDTO response = ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .code("VALIDATION_ERROR")
                .message("Validación fallida")
                .details(details)
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.error("Malformed JSON: {}", ex.getMessage());
        ErrorResponseDTO response = ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .code("MALFORMED_JSON")
                .message("Formato de solicitud JSON inválido")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // A bad query-param type (e.g. a malformed 'desde'/'hasta' date) is a client
    // error, not a 500. Date-range params map to the range code; anything else to a
    // generic param-type code so the client still branches deterministically.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Type mismatch at {} for param '{}': {}", request.getRequestURI(), ex.getName(), ex.getMessage());
        boolean esRango = "desde".equals(ex.getName()) || "hasta".equals(ex.getName());
        String code = esRango ? "RANGO_FECHAS_INVALIDO" : "PARAMETRO_INVALIDO";
        String message = esRango
                ? "El formato de fecha es inválido; use YYYY-MM-DD"
                : "El parámetro '" + ex.getName() + "' tiene un formato inválido";
        ErrorResponseDTO response = ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .code(code)
                .message(message)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponseDTO> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        // Fixed messages: the raw Spring Security text stays in the server log.
        log.warn("Authentication error at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponseDTO response = ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .code("UNAUTHORIZED")
                .message(ex instanceof BadCredentialsException ? "Credenciales inválidas" : "Autenticación requerida")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.error("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponseDTO response = ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .code("FORBIDDEN")
                .message("No tiene permisos para acceder a este recurso")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(Exception ex, HttpServletRequest request) {
        // Framework-detected client errors (unknown route, wrong method, wrong
        // content type, missing param...) carry their own 4xx status: keep it
        // instead of masking them as a 500.
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            log.warn("Client error at {}: {}", request.getRequestURI(), ex.getMessage());
            ErrorResponseDTO response = ErrorResponseDTO.builder()
                    .timestamp(LocalDateTime.now())
                    .status(status.value())
                    .code(status.name())
                    .message(status == HttpStatus.NOT_FOUND ? "Recurso no encontrado" : "Solicitud no válida para este recurso")
                    .path(request.getRequestURI())
                    .build();
            return ResponseEntity.status(status).body(response);
        }
        log.error("Unexpected error at {}: ", request.getRequestURI(), ex);
        ErrorResponseDTO response = ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .code("INTERNAL_SERVER_ERROR")
                .message("Ha ocurrido un error inesperado en el servidor")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
