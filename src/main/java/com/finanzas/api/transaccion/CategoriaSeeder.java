package com.finanzas.api.transaccion;

import com.finanzas.api.transaccion.model.Categoria;
import com.finanzas.api.transaccion.model.TipoTransaccion;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the base expense categories a taxi driver needs out of the box. Base
 * categories have a null usuario so every account shares them. Idempotent: each
 * category is inserted only if a base one with the same name does not exist yet.
 */
@Component
public class CategoriaSeeder implements CommandLineRunner {

    private static final List<String> CATEGORIAS_BASE_TAXI =
            List.of("Gasolina", "Peaje", "Alimentación", "Mantenimiento");

    private final CategoriaRepository categoriaRepository;

    public CategoriaSeeder(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @Override
    public void run(String... args) {
        for (String nombre : CATEGORIAS_BASE_TAXI) {
            if (!categoriaRepository.existsByNombreIgnoreCaseAndUsuarioIsNull(nombre)) {
                Categoria categoria = new Categoria();
                categoria.setNombre(nombre);
                categoria.setTipo(TipoTransaccion.EGRESO);
                categoria.setUsuario(null);
                categoriaRepository.save(categoria);
            }
        }
    }
}
