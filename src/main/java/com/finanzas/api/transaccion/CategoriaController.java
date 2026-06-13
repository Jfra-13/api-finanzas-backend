package com.finanzas.api.transaccion;

import com.finanzas.api.security.UsuarioPrincipal;
import com.finanzas.api.shared.dto.ApiResponseDTO;
import com.finanzas.api.transaccion.dto.CategoriaCreateDTO;
import com.finanzas.api.transaccion.dto.CategoriaResponseDTO;
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
}
