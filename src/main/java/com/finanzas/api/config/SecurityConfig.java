package com.finanzas.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // Herramienta matemática que encriptará las contraseñas
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Le decimos a Spring Boot: "Déjanos pasar sin pedir token por ahora"
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Desactivamos protección de formularios web (somos API)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll() // Permitimos todas las rutas (Login, Registro, etc.)
                );
        return http.build();
    }
}