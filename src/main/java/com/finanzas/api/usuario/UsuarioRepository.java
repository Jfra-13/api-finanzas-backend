package com.finanzas.api.usuario;

import com.finanzas.api.usuario.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Con solo nombrar el método así, Spring Boot ya sabe que debe hacer
    // un "SELECT * FROM usuarios WHERE email = ?"
    Optional<Usuario> findByEmail(String email);

    // Accounts whose grace period already expired; input is the cutoff timestamp
    // (now minus the grace days), so the query itself stays date-math free.
    List<Usuario> findByEliminadoEnBefore(LocalDateTime limite);

    // Atomic check-and-increment of the OTP attempt counter: 0 rows means the
    // lock is already exhausted. Read-then-save would let parallel guesses through.
    @Transactional
    @Modifying
    @Query("UPDATE Usuario u SET u.intentosOtp = u.intentosOtp + 1 WHERE u.id = :id AND u.intentosOtp < :max")
    int reservarIntentoOtp(@Param("id") Long id, @Param("max") int max);

    @Transactional
    @Modifying
    @Query("UPDATE Usuario u SET u.intentosOtp = u.intentosOtp - 1 WHERE u.id = :id AND u.intentosOtp > 0")
    void liberarIntentoOtp(@Param("id") Long id);

}
