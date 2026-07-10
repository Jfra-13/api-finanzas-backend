package com.finanzas.api.transaccion;

import com.finanzas.api.shared.exception.specific.AccesoDenegadoException;
import com.finanzas.api.shared.exception.specific.CategoriaNoEncontradaException;
import com.finanzas.api.transaccion.dto.CategoriaCreateDTO;
import com.finanzas.api.transaccion.dto.CategoriaResponseDTO;
import com.finanzas.api.transaccion.dto.CategoriaUpdateDTO;
import com.finanzas.api.transaccion.model.Categoria;
import com.finanzas.api.transaccion.model.TipoTransaccion;
import com.finanzas.api.usuario.model.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final TransaccionRepository transaccionRepository;
    private final PresupuestoRepository presupuestoRepository;

    public CategoriaService(CategoriaRepository categoriaRepository,
                            TransaccionRepository transaccionRepository,
                            PresupuestoRepository presupuestoRepository) {
        this.categoriaRepository = categoriaRepository;
        this.transaccionRepository = transaccionRepository;
        this.presupuestoRepository = presupuestoRepository;
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

    @Transactional
    public CategoriaResponseDTO actualizar(Long usuarioId, Long id, CategoriaUpdateDTO dto) {
        Categoria categoria = obtenerEditable(usuarioId, id);
        categoria.setNombre(dto.getNombre());
        return toResponse(categoriaRepository.save(categoria));
    }

    // Deleting a category keeps its movements (reassigned to "Sin categoría") and
    // drops its budgets, which are meaningless without the category.
    @Transactional
    public void eliminar(Long usuarioId, Long id) {
        Categoria categoria = obtenerEditable(usuarioId, id);
        transaccionRepository.desasociarCategoria(categoria.getId());
        presupuestoRepository.deleteByCategoriaId(categoria.getId());
        categoriaRepository.delete(categoria);
    }

    // Only categories owned by the user are editable. Base categories (usuario null)
    // are shared, so touching them is a 403; another user's category is reported as
    // 404 to avoid leaking that it exists.
    private Categoria obtenerEditable(Long usuarioId, Long id) {
        Categoria categoria = categoriaRepository.findVisible(id, usuarioId)
                .orElseThrow(CategoriaNoEncontradaException::new);
        if (categoria.getUsuario() == null) {
            throw new AccesoDenegadoException();
        }
        return categoria;
    }

    private CategoriaResponseDTO toResponse(Categoria categoria) {
        CategoriaResponseDTO dto = new CategoriaResponseDTO();
        dto.setId(categoria.getId());
        dto.setNombre(categoria.getNombre());
        dto.setTipo(categoria.getTipo().name());
        return dto;
    }
}
