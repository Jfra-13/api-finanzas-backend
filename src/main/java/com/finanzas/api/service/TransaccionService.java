package com.finanzas.api.service;

import com.finanzas.api.model.dto.TransaccionRegistroDTO;
import com.finanzas.api.model.entity.Transaccion;
import com.finanzas.api.model.entity.Usuario;
import com.finanzas.api.repository.TransaccionRepository;
import com.finanzas.api.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import com.finanzas.api.model.dto.DiaResumenDTO;

import java.math.BigDecimal;

@Service
public class TransaccionService {

    private final TransaccionRepository transaccionRepository;
    private final UsuarioRepository usuarioRepository;
    private final MetaService metaService; // Traemos tu algoritmo del Día 1

    public TransaccionService(TransaccionRepository transaccionRepository, UsuarioRepository usuarioRepository, MetaService metaService) {
        this.transaccionRepository = transaccionRepository;
        this.usuarioRepository = usuarioRepository;
        this.metaService = metaService;
    }

    public Transaccion registrar(TransaccionRegistroDTO dto) {
        Usuario usuario = usuarioRepository.findById(dto.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Transaccion nuevaTransaccion = new Transaccion();
        nuevaTransaccion.setMonto(dto.getMonto());
        nuevaTransaccion.setTipo(dto.getTipo().toUpperCase());
        nuevaTransaccion.setUsuario(usuario);

        return transaccionRepository.save(nuevaTransaccion);
    }

    public BigDecimal obtenerCuotaDiaria(Long usuarioId, BigDecimal metaMensual, int diasRestantes) {
        BigDecimal utilidadActual = transaccionRepository.sumarIngresosPorUsuario(usuarioId)
                .orElse(BigDecimal.ZERO);

        // ¡EL PARCHE DE QA!
        // Si ya ganaste más que la meta, enviamos la diferencia total en negativo
        if (utilidadActual.compareTo(metaMensual) > 0) {
            // Ejemplo: Meta 3000 - Utilidad 3500 = -500.
            // Android recibirá -500, lo volverá positivo (abs) y dirá "Superada por S/ 500.00"
            return metaMensual.subtract(utilidadActual);
        }

        // Si todavía falta dinero, usamos tu algoritmo original del Día 1
        return metaService.calcularCuotaDiaria(metaMensual, utilidadActual, diasRestantes);
    }

    public BigDecimal obtenerIngresosHoy(Long usuarioId) {
        java.time.LocalDateTime inicioDia = java.time.LocalDate.now().atStartOfDay();
        java.time.LocalDateTime finDia = inicioDia.plusDays(1);

        return transaccionRepository.sumarIngresosDeHoy(usuarioId, inicioDia, finDia);
    }

    public List<DiaResumenDTO> obtenerResumenSemanal(Long usuarioId) {
        // 1. Calculamos las fechas: Desde el Lunes a las 00:00 hasta el Domingo a las 23:59
        LocalDate hoy = LocalDate.now();
        LocalDateTime inicioSemana = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime finSemana = hoy.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusDays(1).atStartOfDay();

        // 2. Traemos todas las transacciones de esa semana
        List<Transaccion> transacciones = transaccionRepository.obtenerTransaccionesPorRango(usuarioId, inicioSemana, finSemana);

        // 3. Preparamos nuestras 7 "cajas" de días vacías
        List<DiaResumenDTO> resumen = new ArrayList<>();
        String[] nombresDias = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};

        for (String nombreDia : nombresDias) {
            resumen.add(new DiaResumenDTO(nombreDia, BigDecimal.ZERO, BigDecimal.ZERO));
        }

        // 4. Clasificamos cada transacción en la caja correcta
        for (Transaccion t : transacciones) {
            // getDayOfWeek().getValue() devuelve 1 para Lunes, 7 para Domingo.
            // Restamos 1 para que encaje en el índice de nuestra lista (0 a 6).
            int indexDia = t.getFecha().getDayOfWeek().getValue() - 1;
            DiaResumenDTO diaDto = resumen.get(indexDia);

            if ("INGRESO".equalsIgnoreCase(t.getTipo())) {
                diaDto.setIngresos(diaDto.getIngresos().add(t.getMonto()));
            } else if ("EGRESO".equalsIgnoreCase(t.getTipo())) {
                diaDto.setEgresos(diaDto.getEgresos().add(t.getMonto()));
            }
        }

        return resumen;
    }

}
