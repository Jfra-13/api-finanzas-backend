package com.finanzas.api.transaccion;

import com.finanzas.api.meta.MetaService;
import com.finanzas.api.meta.model.Meta;
import com.finanzas.api.shared.exception.specific.RangoFechasInvalidoException;
import com.finanzas.api.transaccion.dto.AlertaDTO;
import com.finanzas.api.transaccion.dto.TendenciaMensualDTO;
import com.finanzas.api.transaccion.model.TipoTransaccion;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
