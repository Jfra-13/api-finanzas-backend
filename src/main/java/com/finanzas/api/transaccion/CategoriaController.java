package com.finanzas.api.transaccion;

import com.finanzas.api.security.UsuarioPrincipal;
import com.finanzas.api.shared.dto.ApiResponseDTO;
import com.finanzas.api.transaccion.dto.CategoriaCreateDTO;
import com.finanzas.api.transaccion.dto.CategoriaResponseDTO;
import com.finanzas.api.transaccion.dto.CategoriaUpdateDTO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/finanzas/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @PostMapping
    public ResponseEntity<ApiResponseDTO<CategoriaResponseDTO>> crear(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @Valid @RequestBody CategoriaCreateDTO dto) {
        CategoriaResponseDTO categoria = categoriaService.crear(userPrincipal.getUsuario(), dto);
        return ResponseEntity.ok(ApiResponseDTO.success(
                200, "CATEGORY_CREATED", "Categoría creada exitosamente", categoria, "/api/v1/finanzas/categorias"));
    }

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<CategoriaResponseDTO>>> listar(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal) {
        List<CategoriaResponseDTO> categorias = categoriaService.listar(userPrincipal.getUsuario().getId());
        return ResponseEntity.ok(ApiResponseDTO.success(
                200, "CATEGORIES_OK", "Categorías obtenidas", categorias, "/api/v1/finanzas/categorias"));
    }

    @Operation(summary = "Renombrar categoría propia",
            description = "Solo el nombre es editable; el tipo es inmutable para no corromper analíticas históricas. "
                    + "Las categorías base del sistema no se pueden editar (403 ACCESO_DENEGADO).")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<CategoriaResponseDTO>> actualizar(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @PathVariable Long id,
            @Valid @RequestBody CategoriaUpdateDTO dto) {
        CategoriaResponseDTO categoria = categoriaService.actualizar(userPrincipal.getUsuario().getId(), id, dto);
        return ResponseEntity.ok(ApiResponseDTO.success(
                200, "CATEGORY_UPDATED", "Categoría actualizada", categoria, "/api/v1/finanzas/categorias/" + id));
    }

    @Operation(summary = "Eliminar categoría propia",
            description = "Las transacciones asociadas NO se borran: quedan sin categoría. "
                    + "Los presupuestos de la categoría se eliminan. "
                    + "Las categorías base del sistema no se pueden eliminar (403 ACCESO_DENEGADO).")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<Void>> eliminar(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @PathVariable Long id) {
        categoriaService.eliminar(userPrincipal.getUsuario().getId(), id);
        return ResponseEntity.ok(ApiResponseDTO.success(
                200, "CATEGORY_DELETED", "Categoría eliminada", null, "/api/v1/finanzas/categorias/" + id));
    }
}
