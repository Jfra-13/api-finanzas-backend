package com.finanzas.api.transaccion;

import com.finanzas.api.meta.dto.DiaResumenDTO;
import com.finanzas.api.shared.dto.ApiResponseDTO;
import java.util.List;
import com.finanzas.api.transaccion.dto.TransaccionRegistroDTO;
import com.finanzas.api.transaccion.dto.TransaccionResponseDTO;
import com.finanzas.api.transaccion.dto.TransaccionUpdateDTO;
import com.finanzas.api.security.UsuarioPrincipal;
import com.finanzas.api.meta.dto.ProgresoMetasDTO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/finanzas")
public class TransaccionController {

    private static final String TX_PATH = "/api/v1/finanzas/transacciones";

    private final TransaccionService transaccionService;

    public TransaccionController(TransaccionService transaccionService) {
        this.transaccionService = transaccionService;
    }

    @Operation(summary = "Registrar transacción",
            description = "Campos opcionales: descripcion, fecha y categoriaId. "
                    + "CONTRATO: 'fecha' se acepta solo como ISO-8601 datetime (yyyy-MM-dd'T'HH:mm:ss); "
                    + "una fecha sin hora es rechazada. Si se omite, se registra con la fecha/hora actual del servidor.")
    @PostMapping("/transacciones")
    public ResponseEntity<ApiResponseDTO<String>> registrarTransaccion(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @Valid @RequestBody TransaccionRegistroDTO dto) {
        transaccionService.registrar(userPrincipal.getUsuario(), dto);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "TRANSACTION_CREATED", "¡Transacción guardada exitosamente!", null, TX_PATH));
    }

    // Paged history; newest first by default. Optional type/category/date-range filters.
    @Operation(summary = "Historial de transacciones",
            description = "CONTRATO: 'sinCategoria=true' filtra solo las transacciones sin categoría; "
                    + "'sinCategoria=false' u omitido no filtra (NO significa 'solo categorizadas'). "
                    + "Combinar 'sinCategoria=true' con 'categoriaId' responde 400 PARAMETRO_INVALIDO.")
    @GetMapping("/transacciones")
    public ResponseEntity<ApiResponseDTO<Page<TransaccionResponseDTO>>> listarTransacciones(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Boolean sinCategoria,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @PageableDefault(sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TransaccionResponseDTO> historial = transaccionService.listar(
                userPrincipal.getUsuario().getId(), tipo, categoriaId, sinCategoria, desde, hasta, pageable);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "TRANSACTIONS_OK", "Historial obtenido", historial, TX_PATH));
    }

    @PutMapping("/transacciones/{id}")
    public ResponseEntity<ApiResponseDTO<TransaccionResponseDTO>> actualizarTransaccion(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @PathVariable Long id,
            @Valid @RequestBody TransaccionUpdateDTO dto) {
        TransaccionResponseDTO actualizada = transaccionService.actualizar(userPrincipal.getUsuario().getId(), id, dto);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "TRANSACTION_UPDATED", "Transacción actualizada", actualizada, TX_PATH + "/" + id));
    }

    @DeleteMapping("/transacciones/{id}")
    public ResponseEntity<ApiResponseDTO<Void>> eliminarTransaccion(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @PathVariable Long id) {
        transaccionService.eliminar(userPrincipal.getUsuario().getId(), id);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "TRANSACTION_DELETED", "Transacción eliminada", null, TX_PATH + "/" + id));
    }

    // Daily quota. Without params it reads goal and working days from the DB.
    @Operation(summary = "Cuota diaria de ganancia",
            description = "CONTRATO: el signo es parte del contrato. Valor > 0 = lo que falta ganar hoy; "
                    + "valor <= 0 = meta del mes ya superada (el excedente se devuelve en negativo). "
                    + "Sin meta activa y sin parámetros ad-hoc responde 404 META_NO_ENCONTRADA.")
    @GetMapping("/cuota-diaria")
    public ResponseEntity<ApiResponseDTO<BigDecimal>> consultarCuota(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @RequestParam(required = false) BigDecimal meta,
            @RequestParam(required = false) Integer dias) {

        BigDecimal cuota = transaccionService.obtenerCuotaDiaria(userPrincipal.getUsuario().getId(), meta, dias);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "DAILY_QUOTA_OK", "Cuota diaria obtenida", cuota, "/api/v1/finanzas/cuota-diaria"));
    }

    @GetMapping("/hoy")
    public ResponseEntity<ApiResponseDTO<BigDecimal>> consultarIngresosHoy(@AuthenticationPrincipal UsuarioPrincipal userPrincipal) {
        BigDecimal gananciaHoy = transaccionService.obtenerIngresosHoy(userPrincipal.getUsuario().getId());
        return ResponseEntity.ok(ApiResponseDTO.success(200, "TODAY_INCOME_OK", "Ingresos de hoy obtenidos", gananciaHoy, "/api/v1/finanzas/hoy"));
    }

    @Operation(summary = "Resumen de la semana en curso",
            description = "CONTRATO: devuelve SIEMPRE exactamente 7 items en orden fijo lunes→domingo "
                    + "(posición 0 = lunes), con ceros en los días sin movimientos. "
                    + "El cliente indexa por posición; orden y cantidad son garantía de la API.")
    @GetMapping("/resumen-semanal")
    public ResponseEntity<ApiResponseDTO<List<DiaResumenDTO>>> consultarResumenSemanal(@AuthenticationPrincipal UsuarioPrincipal userPrincipal) {
        List<DiaResumenDTO> resumen = transaccionService.obtenerResumenSemanal(userPrincipal.getUsuario().getId());
        return ResponseEntity.ok(ApiResponseDTO.success(200, "WEEKLY_SUMMARY_OK", "Resumen semanal obtenido", resumen, "/api/v1/finanzas/resumen-semanal"));
    }

    // Progress indicators. Without params the goal comes from the DB.
    @Operation(summary = "Progreso de metas",
            description = "Indicadores de ingreso en BRUTO (solo ingresos); la metaDiaria sale del motor NETO. "
                    + "Sin meta activa y sin parámetros ad-hoc responde 404 META_NO_ENCONTRADA.")
    @GetMapping("/progreso-metas")
    public ResponseEntity<ApiResponseDTO<ProgresoMetasDTO>> consultarProgresoTotal(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @RequestParam(required = false) BigDecimal meta,
            @RequestParam(required = false) Integer dias) {

        ProgresoMetasDTO progreso = transaccionService.obtenerProgresoMetas(userPrincipal.getUsuario().getId(), meta, dias);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "GOALS_PROGRESS_OK", "Progreso de metas obtenido", progreso, "/api/v1/finanzas/progreso-metas"));
    }
}
