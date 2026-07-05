package com.finanzas.api.transaccion;

import com.finanzas.api.shared.exception.specific.AccesoDenegadoException;
import com.finanzas.api.shared.exception.specific.CategoriaNoEncontradaException;
import com.finanzas.api.shared.exception.specific.PresupuestoNoEncontradoException;
import com.finanzas.api.transaccion.dto.PresupuestoRegistroDTO;
import com.finanzas.api.transaccion.dto.PresupuestoResponseDTO;
import com.finanzas.api.transaccion.model.Categoria;
import com.finanzas.api.transaccion.model.Presupuesto;
import com.finanzas.api.usuario.model.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PresupuestoService {

    private final PresupuestoRepository presupuestoRepository;
    private final CategoriaRepository categoriaRepository;
    private final TransaccionRepository transaccionRepository;

    public PresupuestoService(PresupuestoRepository presupuestoRepository,
                              CategoriaRepository categoriaRepository,
                              TransaccionRepository transaccionRepository) {
        this.presupuestoRepository = presupuestoRepository;
        this.categoriaRepository = categoriaRepository;
        this.transaccionRepository = transaccionRepository;
    }

    // Upsert: one budget per (usuario, categoria); re-sending replaces the cap.
    @Transactional
    public PresupuestoResponseDTO guardar(Usuario usuario, PresupuestoRegistroDTO dto) {
        Categoria categoria = categoriaRepository.findVisible(dto.getCategoriaId(), usuario.getId())
                .orElseThrow(CategoriaNoEncontradaException::new);

        Presupuesto presupuesto = presupuestoRepository
                .findByUsuarioIdAndCategoriaId(usuario.getId(), categoria.getId())
                .orElseGet(() -> {
                    Presupuesto nuevo = new Presupuesto();
                    nuevo.setUsuario(usuario);
                    nuevo.setCategoria(categoria);
                    return nuevo;
                });
        presupuesto.setMontoMensual(dto.getMontoMensual());

        return toResponse(presupuestoRepository.save(presupuesto));
    }

    public List<PresupuestoResponseDTO> listar(Long usuarioId) {
        return presupuestoRepository.findByUsuarioId(usuarioId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void eliminar(Long usuarioId, Long id) {
        Presupuesto presupuesto = presupuestoRepository.findById(id)
                .orElseThrow(PresupuestoNoEncontradoException::new);
        if (!presupuesto.getUsuario().getId().equals(usuarioId)) {
            throw new AccesoDenegadoException();
        }
        presupuestoRepository.delete(presupuesto);
    }

    // Attaches the current-month spend and the derived indicators. consumoPct has
    // no upper bound (can exceed 100); montoMensual is >= 0.01 so it never divides by 0.
    private PresupuestoResponseDTO toResponse(Presupuesto p) {
        LocalDateTime inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime finMes = inicioMes.plusMonths(1);

        BigDecimal gastadoMes = transaccionRepository.sumarEgresoPorCategoriaYRango(
                p.getUsuario().getId(), p.getCategoria().getId(), inicioMes, finMes);
        BigDecimal restante = p.getMontoMensual().subtract(gastadoMes);
        BigDecimal consumoPct = gastadoMes
                .multiply(BigDecimal.valueOf(100))
                .divide(p.getMontoMensual(), 1, RoundingMode.HALF_UP);
        boolean excedido = gastadoMes.compareTo(p.getMontoMensual()) > 0;

        return new PresupuestoResponseDTO(
                p.getId(),
                p.getCategoria().getId(),
                p.getCategoria().getNombre(),
                p.getMontoMensual(),
                gastadoMes,
                restante,
                consumoPct,
                excedido);
    }
}
