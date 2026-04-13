package com.finanzas.api.service;

import com.finanzas.api.model.dto.TransaccionRegistroDTO;
import com.finanzas.api.model.entity.Transaccion;
import com.finanzas.api.model.entity.Usuario;
import com.finanzas.api.repository.TransaccionRepository;
import com.finanzas.api.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

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

    // NUEVA FUNCIÓN: Calcula la cuota en tiempo real (¡Ahora con ganancia extra!)
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
}