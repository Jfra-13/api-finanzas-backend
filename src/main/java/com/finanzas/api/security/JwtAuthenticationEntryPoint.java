package com.finanzas.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) 
            throws IOException {
        
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
        String path = request.getRequestURI();
        String message = "No autorizado: " + authException.getMessage();
        
        String jsonResponse = String.format(
            "{\"timestamp\":\"%s\", \"status\":401, \"code\":\"UNAUTHORIZED\", \"message\":\"%s\", \"path\":\"%s\"}",
            timestamp, message, path
        );

        response.getWriter().write(jsonResponse);
    }
}
