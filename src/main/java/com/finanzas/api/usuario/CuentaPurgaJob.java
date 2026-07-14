package com.finanzas.api.usuario;

import com.finanzas.api.meta.MetaRepository;
import com.finanzas.api.transaccion.CategoriaRepository;
import com.finanzas.api.transaccion.PresupuestoRepository;
import com.finanzas.api.transaccion.TransaccionRepository;
import com.finanzas.api.usuario.model.Usuario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// Permanently removes accounts whose soft-delete grace period expired.
// Children are deleted first (bulk statements) because the schema defines no
// ON DELETE CASCADE; order follows the FK graph: sessions and budgets, then
// movements, goals, own categories, and finally the user row.
@Slf4j
@Component
public class CuentaPurgaJob {

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PresupuestoRepository presupuestoRepository;
    private final TransaccionRepository transaccionRepository;
    private final MetaRepository metaRepository;
    private final CategoriaRepository categoriaRepository;

    public CuentaPurgaJob(UsuarioRepository usuarioRepository,
                          RefreshTokenRepository refreshTokenRepository,
                          PresupuestoRepository presupuestoRepository,
                          TransaccionRepository transaccionRepository,
                          MetaRepository metaRepository,
                          CategoriaRepository categoriaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.presupuestoRepository = presupuestoRepository;
        this.transaccionRepository = transaccionRepository;
        this.metaRepository = metaRepository;
        this.categoriaRepository = categoriaRepository;
    }

    // Daily at 03:00 server time: expired accounts are already invisible to the
    // API (login rejects them), so a quiet-hours batch is more than enough.
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgarCuentasVencidas() {
        LocalDateTime limite = LocalDateTime.now().minusDays(UsuarioService.DIAS_GRACIA_ELIMINACION);
        List<Usuario> vencidas = usuarioRepository.findByEliminadoEnBefore(limite);
        for (Usuario usuario : vencidas) {
            Long id = usuario.getId();
            refreshTokenRepository.eliminarDeUsuario(id);
            presupuestoRepository.eliminarDeUsuario(id);
            transaccionRepository.eliminarDeUsuario(id);
            metaRepository.eliminarDeUsuario(id);
            categoriaRepository.eliminarDeUsuario(id);
            // The bulk deletes above cleared the persistence context, so the user
            // is removed by id and flushed right away instead of via the (now
            // detached) entity loaded before them.
            usuarioRepository.deleteById(id);
            log.info("Cuenta {} purgada definitivamente (gracia vencida)", id);
        }
        if (!vencidas.isEmpty()) {
            usuarioRepository.flush();
            log.info("Purga de cuentas: {} eliminadas", vencidas.size());
        }
    }
}
