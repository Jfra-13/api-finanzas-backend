package com.finanzas.api.transaccion;

import com.finanzas.api.transaccion.model.Transaccion;
import com.finanzas.api.transaccion.model.TipoTransaccion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // Expense total for one specific category within a range; used to read a
    // budget's current-month spend. COALESCE keeps it at 0 when there are none.
    @Query("SELECT COALESCE(SUM(t.monto), 0) FROM Transaccion t " +
            "WHERE t.usuario.id = :usuarioId AND t.categoria.id = :categoriaId " +
            "AND t.tipo = com.finanzas.api.transaccion.model.TipoTransaccion.EGRESO " +
            "AND t.fecha >= :inicio AND t.fecha < :fin")
    BigDecimal sumarEgresoPorCategoriaYRango(
            @Param("usuarioId") Long usuarioId,
            @Param("categoriaId") Long categoriaId,
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

    // Income and expense totals per calendar day within a range; only days with
    // movements come back. Fuels the monthly calendar (resumen-diario).
    @Query("SELECT CAST(t.fecha AS date), " +
            "SUM(CASE WHEN t.tipo = com.finanzas.api.transaccion.model.TipoTransaccion.INGRESO THEN t.monto ELSE 0 END), " +
            "SUM(CASE WHEN t.tipo = com.finanzas.api.transaccion.model.TipoTransaccion.EGRESO THEN t.monto ELSE 0 END) " +
            "FROM Transaccion t WHERE t.usuario.id = :usuarioId " +
            "AND t.fecha >= :inicio AND t.fecha < :fin " +
            "GROUP BY CAST(t.fecha AS date) ORDER BY CAST(t.fecha AS date)")
    List<Object[]> resumenPorDia(
            @Param("usuarioId") Long usuarioId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    // All movements in a range, used to build the weekly summary.
    @Query("SELECT t FROM Transaccion t WHERE t.usuario.id = :usuarioId " +
            "AND t.fecha >= :inicio AND t.fecha < :fin")
    List<Transaccion> obtenerTransaccionesPorRango(
            @Param("usuarioId") Long usuarioId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    // Used when a category is deleted: its movements survive as "Sin categoría"
    // instead of losing financial history. The bulk update bypasses the persistence
    // context, so it is flushed before and cleared after to keep entities consistent.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaccion t SET t.categoria = null WHERE t.categoria.id = :categoriaId")
    int desasociarCategoria(@Param("categoriaId") Long categoriaId);

    // Paged history with optional type/category/date-range filters; sort comes from
    // Pageable. Range bounds are half-open: [desde, hasta), so the caller passes the
    // exclusive upper bound (start of the day after the inclusive 'hasta').
    // 'sinCategoria' is either true (only uncategorized rows) or null (no filter);
    // the service never passes false, so the query only needs the two-state check.
    @Query("SELECT t FROM Transaccion t WHERE t.usuario.id = :usuarioId " +
            "AND (:tipo IS NULL OR t.tipo = :tipo) " +
            "AND (:categoriaId IS NULL OR t.categoria.id = :categoriaId) " +
            "AND (:sinCategoria IS NULL OR t.categoria IS NULL) " +
            "AND (:desde IS NULL OR t.fecha >= :desde) " +
            "AND (:hasta IS NULL OR t.fecha < :hasta)")
    Page<Transaccion> buscar(
            @Param("usuarioId") Long usuarioId,
            @Param("tipo") TipoTransaccion tipo,
            @Param("categoriaId") Long categoriaId,
            @Param("sinCategoria") Boolean sinCategoria,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            Pageable pageable
    );

    // Account purge: removes the user's whole movement history in one statement.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Transaccion t WHERE t.usuario.id = :usuarioId")
    int eliminarDeUsuario(@Param("usuarioId") Long usuarioId);
}
