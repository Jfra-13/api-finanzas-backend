package com.finanzas.api.usuario;

import com.finanzas.api.shared.exception.specific.LoginBloqueadoException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// Login brute-force guard: after MAX_FALLOS failed logins for an email within
// VENTANA, that email is locked until the window ends. Keyed by the submitted
// email (known or not), so a lock never reveals whether an account exists.
// ponytail: in-memory, per instance and reset on restart; move to a shared
// store (DB/Redis) once the app runs on more than one instance.
@Component
public class LoginIntentos {

    static final int MAX_FALLOS = 5;
    static final Duration VENTANA = Duration.ofMinutes(15);

    private record Registro(int fallos, Instant inicio) {}

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();

    public void verificar(String email) {
        Registro registro = registros.get(clave(email));
        if (registro != null && registro.fallos() >= MAX_FALLOS && !vencido(registro)) {
            throw new LoginBloqueadoException();
        }
    }

    public void registrarFallo(String email) {
        registros.compute(clave(email), (k, r) -> r == null || vencido(r)
                ? new Registro(1, Instant.now())
                : new Registro(r.fallos() + 1, r.inicio()));
    }

    public void limpiar(String email) {
        registros.remove(clave(email));
    }

    // Keeps the map bounded when someone sprays random emails.
    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES)
    void purgarVencidos() {
        registros.values().removeIf(this::vencido);
    }

    private boolean vencido(Registro registro) {
        return registro.inicio().plus(VENTANA).isBefore(Instant.now());
    }

    private String clave(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
