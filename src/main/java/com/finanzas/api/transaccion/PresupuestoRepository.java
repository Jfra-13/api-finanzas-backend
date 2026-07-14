package com.finanzas.api.transaccion;

import com.finanzas.api.transaccion.model.Presupuesto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PresupuestoRepository extends JpaRepository<Presupuesto, Long> {

    // Backs the upsert: at most one budget per (usuario, categoria).
    Optional<Presupuesto> findByUsuarioIdAndCategoriaId(Long usuarioId, Long categoriaId);

    List<Presupuesto> findByUsuarioId(Long usuarioId);

    // Used when a category is deleted: a budget without its category is meaningless.
    void deleteByCategoriaId(Long categoriaId);

    // Account purge: removes every budget of the user in one statement.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Presupuesto p WHERE p.usuario.id = :usuarioId")
    int eliminarDeUsuario(@Param("usuarioId") Long usuarioId);
}
