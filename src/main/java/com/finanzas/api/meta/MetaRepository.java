package com.finanzas.api.meta;

import com.finanzas.api.meta.model.Meta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MetaRepository extends JpaRepository<Meta, Long> {

    // Enforces the "one active goal per user per period" rule at the query level.
    Optional<Meta> findByUsuarioIdAndPeriodoAndActivaTrue(Long usuarioId, String periodo);
}
