package com.finanzas.api.repository;

import com.finanzas.api.model.entity.Transaccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface TransaccionRepository extends JpaRepository<Transaccion, Long> {

    // Magia de JPA: Suma automáticamente todos los ingresos de un usuario
    @Query("SELECT SUM(t.monto) FROM Transaccion t WHERE t.usuario.id = :usuarioId AND t.tipo = 'INGRESO'")
    Optional<BigDecimal> sumarIngresosPorUsuario(@Param("usuarioId") Long usuarioId);
}