package com.finanzas.api.transaccion;

import com.finanzas.api.transaccion.dto.CategoriaCreateDTO;
import com.finanzas.api.transaccion.dto.CategoriaResponseDTO;
import com.finanzas.api.transaccion.model.Categoria;
import com.finanzas.api.transaccion.model.TipoTransaccion;
import com.finanzas.api.usuario.model.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    public CategoriaService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @Transactional
    public CategoriaResponseDTO crear(Usuario usuario, CategoriaCreateDTO dto) {
        Categoria categoria = new Categoria();
        categoria.setNombre(dto.getNombre());
        categoria.setTipo(TipoTransaccion.valueOf(dto.getTipo().toUpperCase()));
        categoria.setUsuario(usuario);

        return toResponse(categoriaRepository.save(categoria));
    }

    public List<CategoriaResponseDTO> listar(Long usuarioId) {
        return categoriaRepository.findByUsuarioIsNullOrUsuarioId(usuarioId).stream()
                .map(this::toResponse)
                .toList();
    }

    private CategoriaResponseDTO toResponse(Categoria categoria) {
        CategoriaResponseDTO dto = new CategoriaResponseDTO();
        dto.setId(categoria.getId());
        dto.setNombre(categoria.getNombre());
        dto.setTipo(categoria.getTipo().name());
        return dto;
    }
}
