package com.finanzas.api.transaccion;

import com.finanzas.api.transaccion.model.Presupuesto;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
