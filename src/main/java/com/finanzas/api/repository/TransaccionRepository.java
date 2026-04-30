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


    @Query("SELECT SUM(t.monto) FROM Transaccion t WHERE t.usuario.id = :usuarioId AND t.tipo = 'INGRESO'")
    Optional<BigDecimal> sumarIngresosPorUsuario(@Param("usuarioId") Long usuarioId);

    // 👇 NUEVA CONSULTA: Solo suma los ingresos de un día específico
    @Query("SELECT COALESCE(SUM(t.monto), 0) FROM Transaccion t WHERE t.usuario.id = :usuarioId AND t.tipo = 'INGRESO' AND t.fecha >= :inicioDia AND t.fecha < :finDia")
    BigDecimal sumarIngresosDeHoy(
            @Param("usuarioId") Long usuarioId,
            @Param("inicioDia") java.time.LocalDateTime inicioDia,
            @Param("finDia") java.time.LocalDateTime finDia
    );
}