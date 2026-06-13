package com.finanzas.api.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finanzas.api.meta.MetaRepository;
import com.finanzas.api.meta.model.Meta;
import com.finanzas.api.security.JwtService;
import com.finanzas.api.security.UsuarioPrincipal;
import com.finanzas.api.transaccion.CategoriaRepository;
import com.finanzas.api.transaccion.TransaccionRepository;
import com.finanzas.api.transaccion.model.Categoria;
import com.finanzas.api.transaccion.model.TipoTransaccion;
import com.finanzas.api.transaccion.model.Transaccion;
import com.finanzas.api.usuario.UsuarioRepository;
import com.finanzas.api.usuario.model.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicInteger;

// Full-context integration base: real Spring context, in-memory H2 (create-drop),
// real security filter chain and real JWTs. Each test runs in a transaction that
// is rolled back afterwards, so cases stay isolated without manual cleanup.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class IntegrationTestSupport {

    protected static final String AUTH = "Authorization";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UsuarioRepository usuarioRepository;
    @Autowired protected TransaccionRepository transaccionRepository;
    @Autowired protected CategoriaRepository categoriaRepository;
    @Autowired protected MetaRepository metaRepository;
    @Autowired protected JwtService jwtService;

    private static final AtomicInteger SEQ = new AtomicInteger();

    protected Usuario crearUsuario() {
        return crearUsuario("user" + SEQ.incrementAndGet() + "@test.com");
    }

    protected Usuario crearUsuario(String email) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setPasswordHash("irrelevant-hash");
        usuario.setNombre("Test User");
        usuario.setTipoNegocio("TAXI");
        return usuarioRepository.save(usuario);
    }

    // Real, correctly-signed JWT for the given user (no password/login flow needed).
    protected String tokenDe(Usuario usuario) {
        return "Bearer " + jwtService.generateToken(new UsuarioPrincipal(usuario));
    }

    protected Transaccion crearTransaccion(Usuario usuario, TipoTransaccion tipo, String monto,
                                           LocalDateTime fecha, Categoria categoria) {
        Transaccion transaccion = new Transaccion();
        transaccion.setUsuario(usuario);
        transaccion.setTipo(tipo);
        transaccion.setMonto(new BigDecimal(monto));
        transaccion.setFecha(fecha);
        transaccion.setCategoria(categoria);
        return transaccionRepository.save(transaccion);
    }

    protected Categoria crearCategoria(String nombre, TipoTransaccion tipo, Usuario usuario) {
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        categoria.setTipo(tipo);
        categoria.setUsuario(usuario);
        return categoriaRepository.save(categoria);
    }

    protected Meta crearMeta(Usuario usuario, String monto, String periodo, String diasCsv) {
        Meta meta = new Meta();
        meta.setUsuario(usuario);
        meta.setMontoObjetivo(new BigDecimal(monto));
        meta.setPeriodo(periodo);
        meta.setDiasLaborables(diasCsv);
        meta.setActiva(true);
        return metaRepository.save(meta);
    }

    protected String periodoActual() {
        return YearMonth.now().toString();
    }
}
