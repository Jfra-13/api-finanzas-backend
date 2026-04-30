package com.finanzas.api.controller;
import com.finanzas.api.model.dto.DiaResumenDTO;
import com.finanzas.api.model.dto.ApiResponseDTO;
import java.util.List;
import com.finanzas.api.model.dto.TransaccionRegistroDTO;
import com.finanzas.api.service.TransaccionService;
import com.finanzas.api.security.UsuarioPrincipal;
import jakarta.validation.Valid;
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
    public ResponseEntity<ApiResponseDTO<String>> registrarTransaccion(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @Valid @RequestBody TransaccionRegistroDTO dto) {
        transaccionService.registrar(userPrincipal.getUsuario(), dto);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "TRANSACTION_CREATED", "¡Transacción guardada exitosamente!", null, "/api/v1/finanzas/transacciones"));
    }

    // Endpoint para que el celular consulte cuánto falta ganar hoy
    @GetMapping("/cuota-diaria")
    public ResponseEntity<ApiResponseDTO<BigDecimal>> consultarCuota(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @RequestParam BigDecimal meta,
            @RequestParam int dias) {

        BigDecimal cuota = transaccionService.obtenerCuotaDiaria(userPrincipal.getUsuario().getId(), meta, dias);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "DAILY_QUOTA_OK", "Cuota diaria obtenida", cuota, "/api/v1/finanzas/cuota-diaria"));
    }


    @GetMapping("/hoy")
    public ResponseEntity<ApiResponseDTO<BigDecimal>> consultarIngresosHoy(@AuthenticationPrincipal UsuarioPrincipal userPrincipal) {
        BigDecimal gananciaHoy = transaccionService.obtenerIngresosHoy(userPrincipal.getUsuario().getId());
        return ResponseEntity.ok(ApiResponseDTO.success(200, "TODAY_INCOME_OK", "Ingresos de hoy obtenidos", gananciaHoy, "/api/v1/finanzas/hoy"));
    }

    @GetMapping("/resumen-semanal")
    public ResponseEntity<ApiResponseDTO<List<DiaResumenDTO>>> consultarResumenSemanal(@AuthenticationPrincipal UsuarioPrincipal userPrincipal) {
        List<DiaResumenDTO> resumen = transaccionService.obtenerResumenSemanal(userPrincipal.getUsuario().getId());
        return ResponseEntity.ok(ApiResponseDTO.success(200, "WEEKLY_SUMMARY_OK", "Resumen semanal obtenido", resumen, "/api/v1/finanzas/resumen-semanal"));
    }

    @GetMapping("/progreso-metas")
    public ResponseEntity<ApiResponseDTO<ProgresoMetasDTO>> consultarProgresoTotal(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @RequestParam BigDecimal meta,
            @RequestParam int dias) {

        ProgresoMetasDTO progreso = transaccionService.obtenerProgresoMetas(userPrincipal.getUsuario().getId(), meta, dias);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "GOALS_PROGRESS_OK", "Progreso de metas obtenido", progreso, "/api/v1/finanzas/progreso-metas"));
    }

}
