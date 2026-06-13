package com.finanzas.api.transaccion;

import com.finanzas.api.transaccion.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    // Base categories (usuario is null) plus the ones owned by the given user.
    List<Categoria> findByUsuarioIsNullOrUsuarioId(Long usuarioId);

    boolean existsByNombreIgnoreCaseAndUsuarioIsNull(String nombre);
}
