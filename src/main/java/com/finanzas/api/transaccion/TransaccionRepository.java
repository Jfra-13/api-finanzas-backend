package com.finanzas.api.transaccion;

import com.finanzas.api.transaccion.model.Transaccion;
import com.finanzas.api.transaccion.model.TipoTransaccion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransaccionRepository extends JpaRepository<Transaccion, Long> {

    // Sum of a single movement type within a date range; COALESCE keeps it at 0
    // instead of null when the user has no matching movements.
    @Query("SELECT COALESCE(SUM(t.monto), 0) FROM Transaccion t " +
            "WHERE t.usuario.id = :usuarioId AND t.tipo = :tipo " +
            "AND t.fecha >= :inicio AND t.fecha < :fin")
    BigDecimal sumarPorTipoYRango(
            @Param("usuarioId") Long usuarioId,
            @Param("tipo") TipoTransaccion tipo,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    // Expense totals grouped by category name within a range. Uncategorized rows
    // come back with a null name; the caller buckets them as "Sin categoría".
    @Query("SELECT c.nombre, SUM(t.monto) FROM Transaccion t LEFT JOIN t.categoria c " +
            "WHERE t.usuario.id = :usuarioId AND t.tipo = com.finanzas.api.transaccion.model.TipoTransaccion.EGRESO " +
            "AND t.fecha >= :inicio AND t.fecha < :fin GROUP BY c.nombre")
    List<Object[]> sumarEgresosPorCategoria(
            @Param("usuarioId") Long usuarioId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    // One income total per calendar day across all history; used to find the
    // user's best single day for the "meta en riesgo" health rule.
    @Query("SELECT SUM(t.monto) FROM Transaccion t " +
            "WHERE t.usuario.id = :usuarioId AND t.tipo = com.finanzas.api.transaccion.model.TipoTransaccion.INGRESO " +
            "GROUP BY CAST(t.fecha AS date)")
    List<BigDecimal> sumasDiariasIngreso(@Param("usuarioId") Long usuarioId);

    // All movements in a range, used to build the weekly summary.
    @Query("SELECT t FROM Transaccion t WHERE t.usuario.id = :usuarioId " +
            "AND t.fecha >= :inicio AND t.fecha < :fin")
    List<Transaccion> obtenerTransaccionesPorRango(
            @Param("usuarioId") Long usuarioId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    // Paged history with optional type/category filters; sort comes from Pageable.
    @Query("SELECT t FROM Transaccion t WHERE t.usuario.id = :usuarioId " +
            "AND (:tipo IS NULL OR t.tipo = :tipo) " +
            "AND (:categoriaId IS NULL OR t.categoria.id = :categoriaId)")
    Page<Transaccion> buscar(
            @Param("usuarioId") Long usuarioId,
            @Param("tipo") TipoTransaccion tipo,
            @Param("categoriaId") Long categoriaId,
            Pageable pageable
    );
}
