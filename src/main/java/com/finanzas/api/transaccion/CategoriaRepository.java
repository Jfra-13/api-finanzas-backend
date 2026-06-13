package com.finanzas.api.transaccion;

import com.finanzas.api.transaccion.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    // Base categories (usuario is null) plus the ones owned by the given user.
    List<Categoria> findByUsuarioIsNullOrUsuarioId(Long usuarioId);

    boolean existsByNombreIgnoreCaseAndUsuarioIsNull(String nombre);

    // A category the user is allowed to use: a base category or one they own.
    @Query("SELECT c FROM Categoria c WHERE c.id = :id " +
            "AND (c.usuario IS NULL OR c.usuario.id = :usuarioId)")
    Optional<Categoria> findVisible(@Param("id") Long id, @Param("usuarioId") Long usuarioId);
}
