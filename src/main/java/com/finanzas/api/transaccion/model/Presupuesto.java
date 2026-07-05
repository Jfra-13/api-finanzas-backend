package com.finanzas.api.transaccion.model;

import com.finanzas.api.usuario.model.Usuario;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// A monthly spending cap for a single category, owned by one user. The current
// month's spend and the derived indicators are computed on read, not stored.
@Entity
@Table(name = "presupuestos",
        uniqueConstraints = @UniqueConstraint(name = "uq_presupuestos_usuario_categoria",
                columnNames = {"usuario_id", "categoria_id"}))
@Data
@NoArgsConstructor
public class Presupuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(name = "monto_mensual", nullable = false)
    private BigDecimal montoMensual;
}
