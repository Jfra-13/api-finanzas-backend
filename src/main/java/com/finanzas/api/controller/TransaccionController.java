package com.finanzas.api.controller;
import com.finanzas.api.model.dto.DiaResumenDTO;
import java.util.List;
import com.finanzas.api.model.dto.TransaccionRegistroDTO;
import com.finanzas.api.service.TransaccionService;
import com.finanzas.api.security.UsuarioPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.finanzas.api.model.dto.ProgresoMetasDTO;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/finanzas")
public class TransaccionController {

    private final TransaccionService transaccionService;

    public TransaccionController(TransaccionService transaccionService) {
        this.transaccionService = transaccionService;
    }

    @PostMapping("/transacciones")
    public ResponseEntity<String> registrarTransaccion(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @RequestBody TransaccionRegistroDTO dto) {
        try {
            dto.setUsuarioId(userPrincipal.getUsuario().getId());
            transaccionService.registrar(dto);
            return ResponseEntity.ok("¡Transacción guardada exitosamente!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // Endpoint para que el celular consulte cuánto falta ganar hoy
    @GetMapping("/cuota-diaria")
    public ResponseEntity<BigDecimal> consultarCuota(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @RequestParam BigDecimal meta,
            @RequestParam int dias) {

        try {
            BigDecimal cuota = transaccionService.obtenerCuotaDiaria(userPrincipal.getUsuario().getId(), meta, dias);
            return ResponseEntity.ok(cuota);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(BigDecimal.ZERO);
        }
    }


    @GetMapping("/hoy")
    public ResponseEntity<BigDecimal> consultarIngresosHoy(@AuthenticationPrincipal UsuarioPrincipal userPrincipal) {
        try {
            BigDecimal gananciaHoy = transaccionService.obtenerIngresosHoy(userPrincipal.getUsuario().getId());
            return ResponseEntity.ok(gananciaHoy);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(BigDecimal.ZERO);
        }
    }

    @GetMapping("/resumen-semanal")
    public ResponseEntity<List<DiaResumenDTO>> consultarResumenSemanal(@AuthenticationPrincipal UsuarioPrincipal userPrincipal) {
        try {
            List<DiaResumenDTO> resumen = transaccionService.obtenerResumenSemanal(userPrincipal.getUsuario().getId());
            return ResponseEntity.ok(resumen);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/progreso-metas")
    public ResponseEntity<ProgresoMetasDTO> consultarProgresoTotal(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @RequestParam BigDecimal meta,
            @RequestParam int dias) {

        try {
            ProgresoMetasDTO progreso = transaccionService.obtenerProgresoMetas(userPrincipal.getUsuario().getId(), meta, dias);
            return ResponseEntity.ok(progreso);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

}
