package com.finanzas.api.security;

import com.finanzas.api.shared.exception.specific.RateLimitExcedidoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// Per-IP limit on the public auth endpoints: one fixed window per IP shared by
// all of them, so an attacker cannot dodge it by switching endpoints. Covers
// registro too, because a sign-up with an existing email mails its owner.
// An interceptor (not a servlet filter) so the exception goes through
// GlobalExceptionHandler. The client IP is getRemoteAddr(): behind a proxy it
// needs server.forward-headers-strategy=native (see application-prod.properties.example).
// ponytail: in-memory, per instance and reset on restart; move to a shared
// store (DB/Redis) together with LoginIntentos once there is more than one instance.
@Component
public class RateLimitPorIp implements HandlerInterceptor, WebMvcConfigurer {

    static final Duration VENTANA = Duration.ofMinutes(1);

    private record Registro(int solicitudes, Instant inicio) {}

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();
    private final int maxPorMinuto;

    public RateLimitPorIp(@Value("${rate-limit.max-por-minuto:20}") int maxPorMinuto) {
        this.maxPorMinuto = maxPorMinuto;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns(
                "/api/v1/usuarios/registro",
                "/api/v1/usuarios/login",
                "/api/v1/usuarios/forgot-password",
                "/api/v1/usuarios/verify-otp",
                "/api/v1/usuarios/reset-password");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Registro registro = registros.compute(request.getRemoteAddr(), (k, r) -> r == null || vencido(r)
                ? new Registro(1, Instant.now())
                : new Registro(r.solicitudes() + 1, r.inicio()));
        if (registro.solicitudes() > maxPorMinuto) {
            throw new RateLimitExcedidoException();
        }
        return true;
    }

    // Keeps the map bounded when requests come from many IPs.
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    void purgarVencidos() {
        registros.values().removeIf(this::vencido);
    }

    private boolean vencido(Registro registro) {
        return registro.inicio().plus(VENTANA).isBefore(Instant.now());
    }
}
