package com.finanzas.api.meta;

import com.finanzas.api.meta.dto.MetaHistorialDTO;
import com.finanzas.api.meta.dto.MetaRegistroDTO;
import com.finanzas.api.meta.dto.MetaResponseDTO;
import com.finanzas.api.meta.model.Meta;
import com.finanzas.api.security.UsuarioPrincipal;
import com.finanzas.api.shared.dto.ApiResponseDTO;
import com.finanzas.api.shared.exception.specific.MetaNoEncontradaException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/finanzas/metas")
public class MetaController {

    private static final String BASE_PATH = "/api/v1/finanzas/metas";

    private final MetaService metaService;

    public MetaController(MetaService metaService) {
        this.metaService = metaService;
    }

    // Set or update the current month's goal together with its working days.
    @PostMapping
    public ResponseEntity<ApiResponseDTO<MetaResponseDTO>> fijarMeta(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @Valid @RequestBody MetaRegistroDTO dto) {

        Meta meta = metaService.fijarMeta(userPrincipal.getUsuario(), dto);
        return ResponseEntity.ok(ApiResponseDTO.success(
                200, "GOAL_SET", "Meta guardada exitosamente", toResponse(meta), BASE_PATH));
    }

    @GetMapping("/actual")
    public ResponseEntity<ApiResponseDTO<MetaResponseDTO>> obtenerActual(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal) {

        Meta meta = metaService.obtenerMetaActual(userPrincipal.getUsuario().getId())
                .orElseThrow(MetaNoEncontradaException::new);
        return ResponseEntity.ok(ApiResponseDTO.success(
                200, "GOAL_OK", "Meta vigente obtenida", toResponse(meta), BASE_PATH + "/actual"));
    }

    @Operation(summary = "Historial de metas mensuales",
            description = "CONTRATO: un item por mes CON meta registrada dentro de la ventana, ascendente por periodo. "
                    + "'meses' = ventana hacia atrás incluyendo el mes en curso (default 6, mínimo 1). "
                    + "'utilidadReal' es utilidad NETA del mes (ingresos - egresos), calculada al momento; "
                    + "en el mes en curso compara lo acumulado hasta hoy. 'cumplida' = utilidadReal >= metaMensual. "
                    + "Sin metas en la ventana devuelve lista vacía, no 404.")
    @GetMapping("/historial")
    public ResponseEntity<ApiResponseDTO<List<MetaHistorialDTO>>> historial(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @RequestParam(required = false) Integer meses) {

        List<MetaHistorialDTO> historial = metaService.historial(userPrincipal.getUsuario().getId(), meses);
        return ResponseEntity.ok(ApiResponseDTO.success(
                200, "GOALS_HISTORY_OK", "Historial de metas obtenido", historial, BASE_PATH + "/historial"));
    }

    private MetaResponseDTO toResponse(Meta meta) {
        return new MetaResponseDTO(
                meta.getId(),
                meta.getMontoObjetivo(),
                meta.getPeriodo(),
                metaService.diasFromCsv(meta.getDiasLaborables()),
                meta.isActiva());
    }
}
