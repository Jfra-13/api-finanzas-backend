package com.finanzas.api.security;

import com.finanzas.api.support.IntegrationTestSupport;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.security.Key;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthSecurityIntegrationTest extends IntegrationTestSupport {

    private static final String PROTEGIDO = "/api/v1/finanzas/transacciones";

    @Value("${jwt.secret}")
    private String secret;

    @Test
    void sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(PROTEGIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tokenBasura_devuelve401() throws Exception {
        mockMvc.perform(get(PROTEGIDO).header(AUTH, "Bearer esto-no-es-un-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tokenExpirado_devuelve401() throws Exception {
        var usuario = crearUsuario();
        mockMvc.perform(get(PROTEGIDO).header(AUTH, "Bearer " + tokenExpirado(usuario.getEmail())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tokenValido_devuelve200() throws Exception {
        var usuario = crearUsuario();
        mockMvc.perform(get(PROTEGIDO).header(AUTH, tokenDe(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TRANSACTIONS_OK"));
    }

    // Signs with the real test key but with an expiry already in the past, so the
    // filter hits ExpiredJwtException — the exact case Fase 1 turned from 500 into 401.
    private String tokenExpirado(String email) {
        Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date(now - 100_000))
                .setExpiration(new Date(now - 50_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
