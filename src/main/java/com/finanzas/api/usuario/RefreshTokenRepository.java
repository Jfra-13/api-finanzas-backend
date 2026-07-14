package com.finanzas.api.usuario;

import com.finanzas.api.usuario.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Kills every live session of the user at once (account deletion). Bulk ops
    // bypass the persistence context: flush before, clear after (same reasoning
    // as TransaccionRepository.desasociarCategoria).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revocado = true WHERE rt.usuario.id = :usuarioId AND rt.revocado = false")
    int revocarTodosDeUsuario(@Param("usuarioId") Long usuarioId);

    // Hard cleanup when the account itself is purged.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken rt WHERE rt.usuario.id = :usuarioId")
    int eliminarDeUsuario(@Param("usuarioId") Long usuarioId);
}
