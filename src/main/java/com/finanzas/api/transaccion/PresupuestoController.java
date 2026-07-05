package com.finanzas.api.transaccion;

import com.finanzas.api.security.UsuarioPrincipal;
import com.finanzas.api.shared.dto.ApiResponseDTO;
import com.finanzas.api.transaccion.dto.PresupuestoRegistroDTO;
import com.finanzas.api.transaccion.dto.PresupuestoResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/finanzas/presupuestos")
public class PresupuestoController {

    private static final String PATH = "/api/v1/finanzas/presupuestos";

    private final PresupuestoService presupuestoService;

    public PresupuestoController(PresupuestoService presupuestoService) {
        this.presupuestoService = presupuestoService;
    }

    // Create or update the monthly budget of a category (upsert).
    @PostMapping
    public ResponseEntity<ApiResponseDTO<PresupuestoResponseDTO>> guardar(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @Valid @RequestBody PresupuestoRegistroDTO dto) {
        PresupuestoResponseDTO presupuesto = presupuestoService.guardar(userPrincipal.getUsuario(), dto);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "BUDGET_SET", "Presupuesto guardado", presupuesto, PATH));
    }

    // List the user's budgets with their current-month status.
    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<PresupuestoResponseDTO>>> listar(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal) {
        List<PresupuestoResponseDTO> presupuestos = presupuestoService.listar(userPrincipal.getUsuario().getId());
        return ResponseEntity.ok(ApiResponseDTO.success(200, "BUDGETS_OK", "Presupuestos obtenidos", presupuestos, PATH));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<Void>> eliminar(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @PathVariable Long id) {
        presupuestoService.eliminar(userPrincipal.getUsuario().getId(), id);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "BUDGET_DELETED", "Presupuesto eliminado", null, PATH + "/" + id));
    }
}
