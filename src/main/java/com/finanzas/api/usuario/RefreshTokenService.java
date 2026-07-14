package com.finanzas.api.usuario;

import com.finanzas.api.shared.exception.specific.RefreshTokenInvalidoException;
import com.finanzas.api.usuario.model.RefreshToken;
import com.finanzas.api.usuario.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // Issues a new refresh token for the user and returns the raw value (shown to
    // the client once). Only its hash is persisted.
    @Transactional
    public String emitir(Usuario usuario) {
        String raw = generarValorAleatorio();

        RefreshToken token = new RefreshToken();
        token.setTokenHash(hash(raw));
        token.setUsuario(usuario);
        token.setExpiracion(LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000));
        token.setRevocado(false);
        refreshTokenRepository.save(token);

        return raw;
    }

    // Validates the raw refresh token, rotates it (revokes the old one, issues a
    // new one) and returns the rotation result. Throws if it is unknown, revoked
    // or expired.
    @Transactional
    public Rotacion rotar(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new RefreshTokenInvalidoException();
        }

        RefreshToken actual = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(RefreshTokenInvalidoException::new);

        if (actual.isRevocado() || actual.getExpiracion().isBefore(LocalDateTime.now())) {
            throw new RefreshTokenInvalidoException();
        }

        actual.setRevocado(true);
        refreshTokenRepository.save(actual);

        Usuario usuario = actual.getUsuario();
        String nuevoRaw = emitir(usuario);
        return new Rotacion(usuario, nuevoRaw);
    }

    // Server-side logout: revokes the token if it exists. Idempotent by design —
    // an unknown, expired or already-revoked token is not an error, the outcome
    // ("this token can no longer be used") is the same.
    @Transactional
    public void revocar(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.setRevocado(true);
            refreshTokenRepository.save(token);
        });
    }

    // Account deletion: every live session dies immediately. The access token
    // keeps its remaining <=15 min of life, same tradeoff documented on logout.
    @Transactional
    public void revocarTodosDe(Long usuarioId) {
        refreshTokenRepository.revocarTodosDeUsuario(usuarioId);
    }

    private String generarValorAleatorio() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JLS; this never happens.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record Rotacion(Usuario usuario, String rawToken) {
    }
}
