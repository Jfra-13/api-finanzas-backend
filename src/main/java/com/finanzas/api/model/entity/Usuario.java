package com.finanzas.api.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // Cambiado de oficio a nombre
    @Column
    private String nombre;

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    // NUEVOS CAMPOS PARA SEGURIDAD OTP
    @Column(name = "codigo_otp", length = 4)
    private String codigoOtp;

    @Column(name = "expiracion_otp")
    private LocalDateTime expiracionOtp;

    // Este campo guardará "BODEGA", "TAXI", "FREELANCE" o "PERSONALIZADO"
    @Column(name = "tipo_negocio")
    private String tipoNegocio;
}