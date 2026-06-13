package com.finanzas.api.transaccion;

import com.finanzas.api.meta.dto.DiaResumenDTO;
import com.finanzas.api.meta.dto.ProgresoMetasDTO;
import com.finanzas.api.meta.model.Meta;
import com.finanzas.api.transaccion.dto.TransaccionRegistroDTO;
import com.finanzas.api.transaccion.dto.TransaccionResponseDTO;
import com.finanzas.api.transaccion.dto.TransaccionUpdateDTO;
import com.finanzas.api.transaccion.model.Categoria;
import com.finanzas.api.transaccion.model.TipoTransaccion;
import com.finanzas.api.transaccion.model.Transaccion;
import com.finanzas.api.shared.exception.specific.AccesoDenegadoException;
import com.finanzas.api.shared.exception.specific.CategoriaNoEncontradaException;
import com.finanzas.api.shared.exception.specific.TransaccionNoEncontradaException;
import com.finanzas.api.usuario.model.Usuario;
import com.finanzas.api.usuario.UsuarioRepository;
import com.finanzas.api.meta.MetaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TransaccionService {

    private final TransaccionRepository transaccionRepository;
    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final MetaService metaService;

    public TransaccionService(TransaccionRepository transaccionRepository, UsuarioRepository usuarioRepository, CategoriaRepository categoriaRepository, MetaService metaService) {
        this.transaccionRepository = transaccionRepository;
        this.usuarioRepository = usuarioRepository;
        this.categoriaRepository = categoriaRepository;
        this.metaService = metaService;
    }

    @Transactional
    public Transaccion registrar(Usuario usuario, TransaccionRegistroDTO dto) {
        Transaccion nuevaTransaccion = new Transaccion();
        nuevaTransaccion.setMonto(dto.getMonto());
        nuevaTransaccion.setTipo(TipoTransaccion.valueOf(dto.getTipo().toUpperCase()));
        nuevaTransaccion.setDescripcion(dto.getDescripcion());
        nuevaTransaccion.setFecha(dto.getFecha() != null ? dto.getFecha() : LocalDateTime.now());
        nuevaTransaccion.setCategoria(resolverCategoria(usuario.getId(), dto.getCategoriaId()));
        nuevaTransaccion.setUsuario(usuario);

        return transaccionRepository.save(nuevaTransaccion);
    }

    // Net profit of the current month: incomes minus expenses, strictly this month.
    public BigDecimal utilidadNetaDelMes(Long usuarioId) {
        LocalDateTime inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime finMes = inicioMes.plusMonths(1);

        BigDecimal ingresos = transaccionRepository.sumarPorTipoYRango(usuarioId, TipoTransaccion.INGRESO, inicioMes, finMes);
        BigDecimal egresos = transaccionRepository.sumarPorTipoYRango(usuarioId, TipoTransaccion.EGRESO, inicioMes, finMes);
        return ingresos.subtract(egresos);
    }

    // Daily profit quota driven by net profit. When meta/dias are null they are
    // resolved from the active Meta in the DB (goal amount + remaining working days).
    public BigDecimal obtenerCuotaDiaria(Long usuarioId, BigDecimal metaMensual, Integer diasRestantes) {
        Optional<Meta> metaActiva = (metaMensual == null || diasRestantes == null)
                ? metaService.obtenerMetaActual(usuarioId)
                : Optional.empty();

        BigDecimal meta = metaMensual != null
                ? metaMensual
                : metaActiva.map(Meta::getMontoObjetivo).orElse(BigDecimal.ZERO);

        int dias = diasRestantes != null
                ? diasRestantes
                : metaService.diasLaborablesRestantes(metaActiva.map(Meta::getDiasLaborables).orElse(null));

        BigDecimal utilidadNeta = utilidadNetaDelMes(usuarioId);

        // Already above the goal: return the negative surplus so the client shows "Superada".
        if (utilidadNeta.compareTo(meta) > 0) {
            return meta.subtract(utilidadNeta);
        }

        return metaService.calcularCuotaDiaria(meta, utilidadNeta, dias);
    }

    public BigDecimal obtenerIngresosHoy(Long usuarioId) {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime finDia = inicioDia.plusDays(1);
        return transaccionRepository.sumarPorTipoYRango(usuarioId, TipoTransaccion.INGRESO, inicioDia, finDia);
    }

    public List<DiaResumenDTO> obtenerResumenSemanal(Long usuarioId) {
        LocalDate hoy = LocalDate.now();
        LocalDateTime inicioSemana = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime finSemana = hoy.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusDays(1).atStartOfDay();

        List<Transaccion> transacciones = transaccionRepository.obtenerTransaccionesPorRango(usuarioId, inicioSemana, finSemana);

        List<DiaResumenDTO> resumen = new ArrayList<>();
        String[] nombresDias = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
        for (String nombreDia : nombresDias) {
            resumen.add(new DiaResumenDTO(nombreDia, BigDecimal.ZERO, BigDecimal.ZERO));
        }

        for (Transaccion t : transacciones) {
            int indexDia = t.getFecha().getDayOfWeek().getValue() - 1;
            DiaResumenDTO diaDto = resumen.get(indexDia);

            if (t.getTipo() == TipoTransaccion.INGRESO) {
                diaDto.setIngresos(diaDto.getIngresos().add(t.getMonto()));
            } else if (t.getTipo() == TipoTransaccion.EGRESO) {
                diaDto.setEgresos(diaDto.getEgresos().add(t.getMonto()));
            }
        }

        return resumen;
    }

    // Indicators stay GROSS (income only); only metaDiaria uses the net engine.
    public ProgresoMetasDTO obtenerProgresoMetas(Long usuarioId, BigDecimal metaMensual, Integer diasRestantes) {
        ProgresoMetasDTO progreso = new ProgresoMetasDTO();

        Optional<Meta> metaActiva = (metaMensual == null) ? metaService.obtenerMetaActual(usuarioId) : Optional.empty();
        BigDecimal meta = metaMensual != null
                ? metaMensual
                : metaActiva.map(Meta::getMontoObjetivo).orElse(BigDecimal.ZERO);

        LocalDate hoy = LocalDate.now();

        // 1. DAILY (gross income)
        LocalDateTime inicioDia = hoy.atStartOfDay();
        LocalDateTime finDia = inicioDia.plusDays(1);
        BigDecimal ingresoDiario = transaccionRepository.sumarPorTipoYRango(usuarioId, TipoTransaccion.INGRESO, inicioDia, finDia);
        BigDecimal metaDiaria = obtenerCuotaDiaria(usuarioId, metaMensual, diasRestantes);

        // 2. WEEKLY (gross income)
        LocalDateTime inicioSemana = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime finSemana = inicioSemana.plusDays(7);
        BigDecimal ingresoSemanal = transaccionRepository.sumarPorTipoYRango(usuarioId, TipoTransaccion.INGRESO, inicioSemana, finSemana);
        BigDecimal metaSemanal = metaDiaria.multiply(BigDecimal.valueOf(7));

        // 3. MONTHLY (gross income)
        LocalDateTime inicioMes = hoy.withDayOfMonth(1).atStartOfDay();
        LocalDateTime finMes = inicioMes.plusMonths(1);
        BigDecimal ingresoMensual = transaccionRepository.sumarPorTipoYRango(usuarioId, TipoTransaccion.INGRESO, inicioMes, finMes);

        progreso.setIngresoDiario(ingresoDiario);
        progreso.setMetaDiaria(metaDiaria);
        progreso.setIngresoSemanal(ingresoSemanal);
        progreso.setMetaSemanal(metaSemanal);
        progreso.setIngresoMensual(ingresoMensual);
        progreso.setMetaMensual(meta);

        return progreso;
    }

    // ---- CRUD (operational history) ----

    public Page<TransaccionResponseDTO> listar(Long usuarioId, String tipo, Long categoriaId, Pageable pageable) {
        TipoTransaccion tipoEnum = (tipo != null && !tipo.isBlank()) ? TipoTransaccion.valueOf(tipo.toUpperCase()) : null;
        return transaccionRepository.buscar(usuarioId, tipoEnum, categoriaId, pageable).map(this::toResponse);
    }

    @Transactional
    public TransaccionResponseDTO actualizar(Long usuarioId, Long id, TransaccionUpdateDTO dto) {
        Transaccion transaccion = obtenerPropia(usuarioId, id);
        transaccion.setMonto(dto.getMonto());
        transaccion.setTipo(TipoTransaccion.valueOf(dto.getTipo().toUpperCase()));
        transaccion.setDescripcion(dto.getDescripcion());
        if (dto.getFecha() != null) {
            transaccion.setFecha(dto.getFecha());
        }
        transaccion.setCategoria(resolverCategoria(usuarioId, dto.getCategoriaId()));
        return toResponse(transaccionRepository.save(transaccion));
    }

    @Transactional
    public void eliminar(Long usuarioId, Long id) {
        Transaccion transaccion = obtenerPropia(usuarioId, id);
        transaccionRepository.delete(transaccion);
    }

    // 404 if it does not exist, 403 if it belongs to another user.
    // Null id leaves the movement uncategorized; a non-null id must point to a
    // base category or one owned by this user, otherwise 404.
    private Categoria resolverCategoria(Long usuarioId, Long categoriaId) {
        if (categoriaId == null) {
            return null;
        }
        return categoriaRepository.findVisible(categoriaId, usuarioId)
                .orElseThrow(CategoriaNoEncontradaException::new);
    }

    private Transaccion obtenerPropia(Long usuarioId, Long id) {
        Transaccion transaccion = transaccionRepository.findById(id)
                .orElseThrow(TransaccionNoEncontradaException::new);
        if (!transaccion.getUsuario().getId().equals(usuarioId)) {
            throw new AccesoDenegadoException();
        }
        return transaccion;
    }

    private TransaccionResponseDTO toResponse(Transaccion t) {
        TransaccionResponseDTO dto = new TransaccionResponseDTO();
        dto.setId(t.getId());
        dto.setMonto(t.getMonto());
        dto.setTipo(t.getTipo().name());
        dto.setDescripcion(t.getDescripcion());
        dto.setFecha(t.getFecha());
        if (t.getCategoria() != null) {
            dto.setCategoriaId(t.getCategoria().getId());
            dto.setCategoriaNombre(t.getCategoria().getNombre());
        }
        dto.setUsuarioId(t.getUsuario().getId());
        return dto;
    }
}
