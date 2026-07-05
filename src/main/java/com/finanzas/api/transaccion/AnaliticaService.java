package com.finanzas.api.transaccion;

import com.finanzas.api.meta.MetaService;
import com.finanzas.api.meta.model.Meta;
import com.finanzas.api.shared.exception.specific.MetaNoEncontradaException;
import com.finanzas.api.shared.exception.specific.RangoFechasInvalidoException;
import com.finanzas.api.transaccion.dto.AlertaDTO;
import com.finanzas.api.transaccion.dto.ComparacionCategoriasDTO;
import com.finanzas.api.transaccion.dto.ProyeccionMensualDTO;
import com.finanzas.api.transaccion.dto.TendenciaMensualDTO;
import com.finanzas.api.transaccion.model.TipoTransaccion;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class AnaliticaService {

    private static final BigDecimal UMBRAL_FELICITACION = new BigDecimal("0.80");

    private final TransaccionRepository transaccionRepository;
    private final TransaccionService transaccionService;
    private final MetaService metaService;

    public AnaliticaService(TransaccionRepository transaccionRepository,
                            TransaccionService transaccionService,
                            MetaService metaService) {
        this.transaccionRepository = transaccionRepository;
        this.transaccionService = transaccionService;
        this.metaService = metaService;
    }

    // A. Pie chart: expenses grouped by category; uncategorized as "Sin categoría".
    // Without a range it defaults to the current month (backward compatible). Range
    // bounds are inclusive calendar days, resolved to a half-open [inicio, fin) interval.
    public Map<String, BigDecimal> resumenCategorias(Long usuarioId, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new RangoFechasInvalidoException();
        }
        LocalDateTime inicio = desde != null
                ? desde.atStartOfDay()
                : LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime fin = hasta != null
                ? hasta.plusDays(1).atStartOfDay()
                : LocalDate.now().withDayOfMonth(1).plusMonths(1).atStartOfDay();

        List<Object[]> filas = transaccionRepository.sumarEgresosPorCategoria(usuarioId, inicio, fin);

        Map<String, BigDecimal> resumen = new LinkedHashMap<>();
        for (Object[] fila : filas) {
            String nombre = fila[0] != null ? (String) fila[0] : "Sin categoría";
            BigDecimal total = (BigDecimal) fila[1];
            resumen.merge(nombre, total, BigDecimal::add);
        }
        return resumen;
    }

    // A.2 Category deltas: this period's expenses per category vs a reference period.
    // Reference = same-length window immediately before (PERIODO_ANTERIOR, default) or
    // the same dates one year earlier (MISMO_PERIODO_ANIO_ANTERIOR). Same-length keeps
    // the rule deterministic and explainable for any range, not just whole months.
    public ComparacionCategoriasDTO comparacionCategorias(Long usuarioId, LocalDate desde, LocalDate hasta, String compararCon) {
        LocalDate inicioActual = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate finActual = hasta != null ? hasta : LocalDate.now();
        if (inicioActual.isAfter(finActual)) {
            throw new RangoFechasInvalidoException();
        }

        LocalDate inicioRef;
        LocalDate finRef;
        if ("MISMO_PERIODO_ANIO_ANTERIOR".equalsIgnoreCase(compararCon)) {
            inicioRef = inicioActual.minusYears(1);
            finRef = finActual.minusYears(1);
        } else { // PERIODO_ANTERIOR (default)
            long duracionDias = ChronoUnit.DAYS.between(inicioActual, finActual) + 1;
            finRef = inicioActual.minusDays(1);
            inicioRef = finRef.minusDays(duracionDias - 1);
        }

        Map<String, BigDecimal> actual = egresosPorCategoria(usuarioId, inicioActual, finActual);
        Map<String, BigDecimal> anterior = egresosPorCategoria(usuarioId, inicioRef, finRef);

        List<ComparacionCategoriasDTO.CategoriaDelta> categorias = new ArrayList<>();
        BigDecimal totalActual = BigDecimal.ZERO;
        BigDecimal totalAnterior = BigDecimal.ZERO;

        // Union of categories seen in either period, actual first then any new ones.
        Set<String> nombres = new LinkedHashSet<>(actual.keySet());
        nombres.addAll(anterior.keySet());
        for (String nombre : nombres) {
            BigDecimal a = actual.getOrDefault(nombre, BigDecimal.ZERO);
            BigDecimal p = anterior.getOrDefault(nombre, BigDecimal.ZERO);
            categorias.add(new ComparacionCategoriasDTO.CategoriaDelta(
                    nombre, a, p, a.subtract(p), deltaPct(a, p)));
            totalActual = totalActual.add(a);
            totalAnterior = totalAnterior.add(p);
        }

        return new ComparacionCategoriasDTO(
                new ComparacionCategoriasDTO.Periodo(inicioActual, finActual),
                new ComparacionCategoriasDTO.Periodo(inicioRef, finRef),
                categorias,
                totalActual,
                totalAnterior,
                deltaPct(totalActual, totalAnterior));
    }

    // Expense totals per category name in an inclusive [desde, hasta] day range.
    private Map<String, BigDecimal> egresosPorCategoria(Long usuarioId, LocalDate desde, LocalDate hasta) {
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay();
        Map<String, BigDecimal> resumen = new LinkedHashMap<>();
        for (Object[] fila : transaccionRepository.sumarEgresosPorCategoria(usuarioId, inicio, fin)) {
            String nombre = fila[0] != null ? (String) fila[0] : "Sin categoría";
            resumen.merge(nombre, (BigDecimal) fila[1], BigDecimal::add);
        }
        return resumen;
    }

    // Signed percentage change, 1 decimal, HALF_UP; null when the base is 0 so the
    // client shows "nuevo" instead of dividing by zero.
    private BigDecimal deltaPct(BigDecimal actual, BigDecimal anterior) {
        if (anterior.signum() == 0) {
            return null;
        }
        return actual.subtract(anterior)
                .multiply(BigDecimal.valueOf(100))
                .divide(anterior, 1, RoundingMode.HALF_UP);
    }

    // A.3 End-of-month projection from the current run-rate. Uses the persisted
    // active goal and the current month. Amounts are projected linearly by elapsed
    // calendar days (deterministic and explainable): actual * diasDelMes / diasTranscurridos.
    public ProyeccionMensualDTO proyeccionMensual(Long usuarioId) {
        Meta meta = metaService.obtenerMetaActual(usuarioId)
                .orElseThrow(MetaNoEncontradaException::new);

        LocalDate hoy = LocalDate.now();
        int diasTranscurridos = hoy.getDayOfMonth();
        int diasDelMes = hoy.lengthOfMonth();
        int diasHabilesRestantes = metaService.diasLaborablesRestantes(meta.getDiasLaborables());

        LocalDateTime inicioMes = hoy.withDayOfMonth(1).atStartOfDay();
        LocalDateTime finMes = inicioMes.plusMonths(1);
        BigDecimal ingresoActual = transaccionRepository.sumarPorTipoYRango(usuarioId, TipoTransaccion.INGRESO, inicioMes, finMes);
        BigDecimal egresoActual = transaccionRepository.sumarPorTipoYRango(usuarioId, TipoTransaccion.EGRESO, inicioMes, finMes);

        BigDecimal ingresoProyectado = proyectar(ingresoActual, diasTranscurridos, diasDelMes);
        BigDecimal egresoProyectado = proyectar(egresoActual, diasTranscurridos, diasDelMes);
        BigDecimal utilidadProyectada = ingresoProyectado.subtract(egresoProyectado);
        BigDecimal metaMensual = meta.getMontoObjetivo();

        return new ProyeccionMensualDTO(
                YearMonth.now().toString(),
                diasTranscurridos,
                diasDelMes,
                diasHabilesRestantes,
                ingresoActual,
                egresoActual,
                ingresoActual.subtract(egresoActual),
                ingresoProyectado,
                egresoProyectado,
                utilidadProyectada,
                metaMensual,
                utilidadProyectada.subtract(metaMensual),
                utilidadProyectada.compareTo(metaMensual) >= 0);
    }

    // Linear run-rate to full month, 2 decimals HALF_UP. diasTranscurridos is the
    // day of month (>= 1), so it never divides by zero.
    private BigDecimal proyectar(BigDecimal actual, int diasTranscurridos, int diasDelMes) {
        return actual.multiply(BigDecimal.valueOf(diasDelMes))
                .divide(BigDecimal.valueOf(diasTranscurridos), 2, RoundingMode.HALF_UP);
    }

    // B. Line chart: income/expense totals for the last N months, oldest first.
    public TendenciaMensualDTO tendenciaMensual(Long usuarioId, int meses) {
        int ventana = Math.max(1, meses);
        List<String> etiquetas = new ArrayList<>();
        List<BigDecimal> ingresos = new ArrayList<>();
        List<BigDecimal> egresos = new ArrayList<>();

        YearMonth actual = YearMonth.now();
        for (int i = ventana - 1; i >= 0; i--) {
            YearMonth ym = actual.minusMonths(i);
            LocalDateTime inicio = ym.atDay(1).atStartOfDay();
            LocalDateTime fin = ym.plusMonths(1).atDay(1).atStartOfDay();

            etiquetas.add(ym.toString());
            ingresos.add(transaccionRepository.sumarPorTipoYRango(usuarioId, TipoTransaccion.INGRESO, inicio, fin));
            egresos.add(transaccionRepository.sumarPorTipoYRango(usuarioId, TipoTransaccion.EGRESO, inicio, fin));
        }

        return new TendenciaMensualDTO(etiquetas, ingresos, egresos);
    }

    // C. Financial health: a fixed, deterministic set of three rules.
    public List<AlertaDTO> saludFinanciera(Long usuarioId) {
        List<AlertaDTO> alertas = new ArrayList<>();

        Optional<Meta> metaOpt = metaService.obtenerMetaActual(usuarioId);
        BigDecimal metaMensual = metaOpt.map(Meta::getMontoObjetivo).orElse(BigDecimal.ZERO);
        String diasCsv = metaOpt.map(Meta::getDiasLaborables).orElse(null);

        BigDecimal utilidadNeta = transaccionService.utilidadNetaDelMes(usuarioId);
        BigDecimal cuotaDiaria = transaccionService.obtenerCuotaDiaria(usuarioId, null, null);

        // Rule 1: today's expenses exceed the daily profit quota.
        if (cuotaDiaria.signum() > 0 && egresosHoy(usuarioId).compareTo(cuotaDiaria) > 0) {
            alertas.add(new AlertaDTO("ALERTA", "GASTO_DIARIO_ALTO",
                    "Tus gastos de hoy superan tu meta de ganancia diaria."));
        }

        // Rule 2: net profit reached 80% of the monthly goal.
        if (metaMensual.signum() > 0
                && utilidadNeta.compareTo(metaMensual.multiply(UMBRAL_FELICITACION)) >= 0) {
            alertas.add(new AlertaDTO("FELICITACION", "META_CERCA",
                    "¡Vas excelente! Alcanzaste el 80% de tu meta del mes."));
        }

        // Rule 3: working days remain but the required quota beats your best day ever.
        int diasRestantes = metaService.diasLaborablesRestantes(diasCsv);
        if (metaMensual.signum() > 0 && diasRestantes > 0 && cuotaDiaria.signum() > 0) {
            BigDecimal mejorDia = mejorDiaHistorico(usuarioId);
            if (mejorDia != null && cuotaDiaria.compareTo(mejorDia) > 0) {
                alertas.add(new AlertaDTO("ALERTA", "META_EN_RIESGO",
                        "La ganancia diaria que necesitas supera tu mejor día histórico. Meta en riesgo."));
            }
        }

        return alertas;
    }

    private BigDecimal egresosHoy(Long usuarioId) {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime finDia = inicioDia.plusDays(1);
        return transaccionRepository.sumarPorTipoYRango(usuarioId, TipoTransaccion.EGRESO, inicioDia, finDia);
    }

    // Best single-day income across all history, or null when there is no income yet.
    private BigDecimal mejorDiaHistorico(Long usuarioId) {
        return transaccionRepository.sumasDiariasIngreso(usuarioId).stream()
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
