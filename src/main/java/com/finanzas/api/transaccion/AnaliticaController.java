package com.finanzas.api.transaccion;

import com.finanzas.api.security.UsuarioPrincipal;
import com.finanzas.api.shared.dto.ApiResponseDTO;
import com.finanzas.api.transaccion.dto.AlertaDTO;
import com.finanzas.api.transaccion.dto.TendenciaMensualDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// Read-only analytics: the phone receives JSON ready to plot, it computes nothing.
@RestController
@RequestMapping("/api/v1/finanzas")
public class AnaliticaController {

    private final AnaliticaService analiticaService;

    public AnaliticaController(AnaliticaService analiticaService) {
        this.analiticaService = analiticaService;
    }

    @GetMapping("/resumen-categorias")
    public ResponseEntity<ApiResponseDTO<Map<String, BigDecimal>>> resumenCategorias(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal) {
        Map<String, BigDecimal> resumen = analiticaService.resumenCategorias(userPrincipal.getUsuario().getId());
        return ResponseEntity.ok(ApiResponseDTO.success(200, "CATEGORY_SUMMARY_OK", "Resumen de categorías obtenido", resumen, "/api/v1/finanzas/resumen-categorias"));
    }

    @GetMapping("/tendencia-mensual")
    public ResponseEntity<ApiResponseDTO<TendenciaMensualDTO>> tendenciaMensual(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @RequestParam(defaultValue = "6") int meses) {
        TendenciaMensualDTO tendencia = analiticaService.tendenciaMensual(userPrincipal.getUsuario().getId(), meses);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "MONTHLY_TREND_OK", "Tendencia mensual obtenida", tendencia, "/api/v1/finanzas/tendencia-mensual"));
    }

    @GetMapping("/salud-financiera")
    public ResponseEntity<ApiResponseDTO<List<AlertaDTO>>> saludFinanciera(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal) {
        List<AlertaDTO> alertas = analiticaService.saludFinanciera(userPrincipal.getUsuario().getId());
        return ResponseEntity.ok(ApiResponseDTO.success(200, "FINANCIAL_HEALTH_OK", "Salud financiera evaluada", alertas, "/api/v1/finanzas/salud-financiera"));
    }
}
