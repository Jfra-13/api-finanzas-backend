package com.finanzas.api.meta;

import com.finanzas.api.meta.dto.MetaHistorialDTO;
import com.finanzas.api.meta.dto.MetaRegistroDTO;
import com.finanzas.api.meta.model.Meta;
import com.finanzas.api.transaccion.TransaccionRepository;
import com.finanzas.api.transaccion.model.TipoTransaccion;
import com.finanzas.api.usuario.model.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MetaService {

    // Every weekday (1=Monday .. 7=Sunday); used when no working days are set.
    private static final Set<Integer> TODOS_LOS_DIAS = Set.of(1, 2, 3, 4, 5, 6, 7);

    // Field injection so the pure-math unit test can build a MetaService with the
    // no-arg constructor; the repository is only needed by the persistence methods.
    @Autowired(required = false)
    private MetaRepository metaRepository;

    // Only used by 'historial'; optional for the same unit-test reason as above.
    @Autowired(required = false)
    private TransaccionRepository transaccionRepository;

    public BigDecimal calcularCuotaDiaria(BigDecimal metaMensual, BigDecimal utilidadActual, int diasRestantes) {
        if (utilidadActual.compareTo(metaMensual) >= 0) {
            return new BigDecimal("0.00");
        }

        BigDecimal faltante = metaMensual.subtract(utilidadActual);

        if (diasRestantes <= 0) {
            return faltante;
        }

        // Dividimos el faltante entre los días y redondeamos a 2 decimales para monedas
        return faltante.divide(BigDecimal.valueOf(diasRestantes), 2, RoundingMode.HALF_UP);
    }

    // Upsert the active goal for the current period: reuse the existing row if any,
    // so a user keeps a single active goal per YYYY-MM.
    @Transactional
    public Meta fijarMeta(Usuario usuario, MetaRegistroDTO dto) {
        String periodo = YearMonth.now().toString();
        Meta meta = metaRepository.findByUsuarioIdAndPeriodoAndActivaTrue(usuario.getId(), periodo)
                .orElseGet(Meta::new);

        meta.setUsuario(usuario);
        meta.setPeriodo(periodo);
        meta.setMontoObjetivo(dto.getMontoObjetivo());
        meta.setDiasLaborables(csvFromDias(dto.getDiasLaborables()));
        meta.setActiva(true);

        return metaRepository.save(meta);
    }

    public Optional<Meta> obtenerMetaActual(Long usuarioId) {
        return metaRepository.findByUsuarioIdAndPeriodoAndActivaTrue(usuarioId, YearMonth.now().toString());
    }

    // Goal history over the last N months including the current one (default 6,
    // clamped to a minimum of 1, same convention as the analytics windows). Only
    // months with a registered goal produce an item, oldest first. Net profit is
    // computed on-the-fly per month; the current month compares month-to-date.
    public List<MetaHistorialDTO> historial(Long usuarioId, Integer meses) {
        int n = Math.max(1, meses != null ? meses : 6);
        String periodoDesde = YearMonth.now().minusMonths(n - 1L).toString();

        return metaRepository
                .findByUsuarioIdAndActivaTrueAndPeriodoGreaterThanEqualOrderByPeriodoAsc(usuarioId, periodoDesde)
                .stream()
                .map(meta -> {
                    YearMonth periodo = YearMonth.parse(meta.getPeriodo());
                    LocalDateTime inicio = periodo.atDay(1).atStartOfDay();
                    LocalDateTime fin = periodo.plusMonths(1).atDay(1).atStartOfDay();
                    BigDecimal ingresos = transaccionRepository.sumarPorTipoYRango(usuarioId, TipoTransaccion.INGRESO, inicio, fin);
                    BigDecimal egresos = transaccionRepository.sumarPorTipoYRango(usuarioId, TipoTransaccion.EGRESO, inicio, fin);
                    BigDecimal utilidad = ingresos.subtract(egresos);
                    return new MetaHistorialDTO(meta.getPeriodo(), meta.getMontoObjetivo(), utilidad,
                            utilidad.compareTo(meta.getMontoObjetivo()) >= 0);
                })
                .toList();
    }

    // Parse the stored CSV ("1,2,3,4,5") into weekday numbers; empty when blank.
    public List<Integer> diasFromCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .toList();
    }

    // Count days from today (inclusive) to the end of the month that match the
    // working-day pattern. Blank pattern is treated as "every day".
    public int diasLaborablesRestantes(String csv) {
        List<Integer> dias = diasFromCsv(csv);
        Set<Integer> patron = dias.isEmpty() ? TODOS_LOS_DIAS : new HashSet<>(dias);

        LocalDate hoy = LocalDate.now();
        LocalDate finMes = hoy.withDayOfMonth(hoy.lengthOfMonth());

        int contador = 0;
        for (LocalDate dia = hoy; !dia.isAfter(finMes); dia = dia.plusDays(1)) {
            if (patron.contains(dia.getDayOfWeek().getValue())) {
                contador++;
            }
        }
        return contador;
    }

    private String csvFromDias(List<Integer> dias) {
        return dias.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
