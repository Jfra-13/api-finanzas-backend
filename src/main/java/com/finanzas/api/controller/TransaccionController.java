package com.finanzas.api.controller;

import com.finanzas.api.model.dto.TransaccionRegistroDTO;
import com.finanzas.api.service.TransaccionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/finanzas")
public class TransaccionController {

    private final TransaccionService transaccionService;

    public TransaccionController(TransaccionService transaccionService) {
        this.transaccionService = transaccionService;
    }

    @PostMapping("/transacciones")
    public ResponseEntity<String> registrarTransaccion(@RequestBody TransaccionRegistroDTO dto) {
        try {
            transaccionService.registrar(dto);
            return ResponseEntity.ok("¡Transacción guardada exitosamente!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
    // Endpoint para que el celular consulte cuánto falta ganar hoy
    @GetMapping("/cuota-diaria/{usuarioId}")
    public ResponseEntity<BigDecimal> consultarCuota(
            @PathVariable Long usuarioId,
            @RequestParam BigDecimal meta,
            @RequestParam int dias) {

        try {
            BigDecimal cuota = transaccionService.obtenerCuotaDiaria(usuarioId, meta, dias);
            return ResponseEntity.ok(cuota);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(BigDecimal.ZERO);
        }
    }
}