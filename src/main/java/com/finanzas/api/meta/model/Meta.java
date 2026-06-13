package com.finanzas.api.meta.model;

import com.finanzas.api.usuario.model.Usuario;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "metas")
@Data
@NoArgsConstructor
public class Meta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "monto_objetivo", nullable = false)
    private BigDecimal montoObjetivo;

    // Calendar period the goal applies to, stored as "YYYY-MM" (e.g. "2026-06").
    @Column(nullable = false, length = 7)
    private String periodo;

    // Business rule: at most one active goal per user per periodo.
    @Column(nullable = false)
    private boolean activa = true;

    // Working days for this goal, stored as a CSV of weekday numbers 1..7
    // (1=Monday .. 7=Sunday), e.g. "1,2,3,4,5". Drives the dynamic daily quota.
    // Null/blank is treated as "every day" by the engine.
    @Column(name = "dias_laborables", length = 20)
    private String diasLaborables;
}
