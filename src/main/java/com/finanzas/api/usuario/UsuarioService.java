package com.finanzas.api.usuario;

import com.finanzas.api.shared.exception.specific.*;
import com.finanzas.api.usuario.dto.EliminarCuentaDTO;
import com.finanzas.api.usuario.dto.ForgotPasswordDTO;
import com.finanzas.api.usuario.dto.LoginDTO;
import com.finanzas.api.usuario.dto.LoginResponseDTO;
import com.finanzas.api.usuario.dto.NegocioUpdateDTO;
import com.finanzas.api.usuario.dto.PerfilResponseDTO;
import com.finanzas.api.usuario.dto.PerfilUpdateDTO;
import com.finanzas.api.usuario.dto.RefreshRequestDTO;
import com.finanzas.api.usuario.dto.ResetPasswordDTO;
import com.finanzas.api.usuario.dto.UsuarioRegistroDTO;
import com.finanzas.api.usuario.dto.VerifyOtpDTO;
import com.finanzas.api.usuario.model.Usuario;
import com.finanzas.api.security.JwtService;
import com.finanzas.api.security.UsuarioPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class UsuarioService {

    // Grace window for soft-deleted accounts: logging in within it reactivates
    // the account; after it, the purge job removes the user permanently.
    public static final int DIAS_GRACIA_ELIMINACION = 30;

    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginIntentos loginIntentos;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          EmailService emailService,
                          AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          RefreshTokenService refreshTokenService,
                          LoginIntentos loginIntentos) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginIntentos = loginIntentos;
    }

    // An existing email gets the same response as a new one (no account
    // enumeration); its owner is told by mail instead. The hash runs on both
    // paths so response time does not give the email away either.
    @Transactional
    public void registrarUsuario(UsuarioRegistroDTO dto) {
        String passwordHash = passwordEncoder.encode(dto.getPassword());
        if (usuarioRepository.findByEmail(dto.getEmail()).isPresent()) {
            emailService.avisarRegistroConEmailExistente(dto.getEmail());
            return;
        }

        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setEmail(dto.getEmail());
        nuevoUsuario.setNombre(dto.getNombre());
        nuevoUsuario.setPasswordHash(passwordHash);
        nuevoUsuario.setTipoNegocio(dto.getTipoNegocio());

        usuarioRepository.save(nuevoUsuario);
    }

    public LoginResponseDTO login(LoginDTO dto) {
        loginIntentos.reservar(dto.getEmail());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword())
        );
        loginIntentos.limpiar(dto.getEmail());

        UsuarioPrincipal userPrincipal = (UsuarioPrincipal) authentication.getPrincipal();
        Usuario usuario = userPrincipal.getUsuario();
        boolean reactivada = reactivarSiEnGracia(usuario);
        String jwtToken = jwtService.generateToken(userPrincipal);
        String refreshToken = refreshTokenService.emitir(usuario);

        return construirRespuesta(usuario, jwtToken, refreshToken, reactivada);
    }

    // Exchanges a valid refresh token for a fresh access token and a rotated
    // refresh token. The old refresh token is invalidated in the process.
    public LoginResponseDTO refrescarToken(RefreshRequestDTO dto) {
        RefreshTokenService.Rotacion rotacion = refreshTokenService.rotar(dto.getRefreshToken());
        Usuario usuario = rotacion.usuario();
        String jwtToken = jwtService.generateToken(new UsuarioPrincipal(usuario));
        return construirRespuesta(usuario, jwtToken, rotacion.rawToken(), false);
    }

    private LoginResponseDTO construirRespuesta(Usuario usuario, String accessToken, String refreshToken, boolean cuentaReactivada) {
        return LoginResponseDTO.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .usuarioId(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .tipoNegocio(usuario.getTipoNegocio())
                .cuentaReactivada(cuentaReactivada)
                .build();
    }

    // Logout is public like refresh: possession of the refresh token IS the
    // credential, so it also works when the access token already expired.
    public void logout(RefreshRequestDTO dto) {
        refreshTokenService.revocar(dto.getRefreshToken());
    }

    // Soft delete with re-authentication: the JWT proves the session, the password
    // proves the owner. Marks the account and kills every live session; data stays
    // untouched during the grace period so logging back in restores everything.
    @Transactional
    public void eliminarCuenta(Usuario usuario, EliminarCuentaDTO dto) {
        if (!passwordEncoder.matches(dto.getPassword(), usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }
        usuario.setEliminadoEn(LocalDateTime.now());
        usuarioRepository.save(usuario);
        refreshTokenService.revocarTodosDe(usuario.getId());
    }

    // A soft-deleted account inside the grace window is reactivated by the login
    // itself (no extra endpoint); past the window it behaves as if it no longer
    // existed — the purge job will remove it. Returns whether a reactivation
    // actually happened so the response can tell the client about it.
    private boolean reactivarSiEnGracia(Usuario usuario) {
        if (usuario.getEliminadoEn() == null) {
            return false;
        }
        if (usuario.getEliminadoEn().plusDays(DIAS_GRACIA_ELIMINACION).isBefore(LocalDateTime.now())) {
            throw new UsuarioNoEncontradoException();
        }
        usuario.setEliminadoEn(null);
        usuarioRepository.save(usuario);
        return true;
    }

    // One live OTP per account: while a code is still valid, a new request is
    // silently ignored. Re-issuing would reset the attempt counter and turn the
    // 5-attempt lock into an unlimited brute force (and spam the inbox).
    @Transactional
    public void generarOtpRecuperacion(ForgotPasswordDTO dto) {
        usuarioRepository.findByEmail(dto.getEmail()).ifPresent(usuario -> {
            if (usuario.getExpiracionOtp() != null && usuario.getExpiracionOtp().isAfter(LocalDateTime.now())) {
                return;
            }
            String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
            usuario.setCodigoOtp(otp);
            usuario.setExpiracionOtp(LocalDateTime.now().plusMinutes(10));
            usuario.setIntentosOtp(0);
            usuarioRepository.save(usuario);
            emailService.enviarOtp(usuario.getEmail(), otp);
        });
    }

    public void verificarOtp(VerifyOtpDTO dto) {
        Usuario usuario = buscarParaOtp(dto.getEmail());
        validarOtp(usuario, dto.getOtp());
        // A correct code gives the attempt back so verify + reset don't burn the lock.
        usuarioRepository.liberarIntentoOtp(usuario.getId());
    }

    // A wrong code must still commit the failed-attempt counter; rolling it back
    // would disable the lockout for this endpoint.
    @Transactional(noRollbackFor = OtpInvalidoException.class)
    public void resetPassword(ResetPasswordDTO dto) {
        Usuario usuario = buscarParaOtp(dto.getEmail());
        validarOtp(usuario, dto.getOtp());

        usuario.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        usuario.setCodigoOtp(null);
        usuario.setExpiracionOtp(null);
        usuario.setIntentosOtp(0);
        usuarioRepository.save(usuario);
        // New password ends every session opened with the old one.
        refreshTokenService.revocarTodosDe(usuario.getId());
    }

    @Transactional
    public void actualizarNegocio(Usuario usuario, NegocioUpdateDTO dto) {
        usuario.setTipoNegocio(dto.getTipoNegocio());
        usuarioRepository.save(usuario);
    }

    public PerfilResponseDTO obtenerPerfil(Usuario usuario) {
        return construirPerfil(usuario);
    }

    // Partial update: only non-null fields change.
    @Transactional
    public PerfilResponseDTO actualizarPerfil(Usuario usuario, PerfilUpdateDTO dto) {
        if (dto.getNombre() != null) {
            usuario.setNombre(dto.getNombre());
        }
        if (dto.getTelefono() != null) {
            usuario.setTelefono(dto.getTelefono());
        }
        return construirPerfil(usuarioRepository.save(usuario));
    }

    private PerfilResponseDTO construirPerfil(Usuario usuario) {
        return PerfilResponseDTO.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .telefono(usuario.getTelefono())
                .tipoNegocio(usuario.getTipoNegocio())
                // fotoUrl and plan: contract-stable placeholders, no storage yet.
                .build();
    }

    // OTP flows must not leak whether an email exists: an unknown account is
    // reported exactly like an invalid code (same OtpInvalidoException / 422),
    // matching the silent behaviour of forgot-password.
    private Usuario buscarParaOtp(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(OtpInvalidoException::new);
    }

    private void validarOtp(Usuario usuario, String otp) {
        if (usuario.getCodigoOtp() == null || usuario.getExpiracionOtp() == null) {
            throw new OtpInvalidoException();
        }

        if (usuario.getExpiracionOtp().isBefore(LocalDateTime.now())) {
            throw new OtpExpiradoException();
        }

        // Every guess reserves an attempt before the comparison, so a parallel
        // burst cannot get more than MAX_OTP_ATTEMPTS guesses past the lock.
        if (usuarioRepository.reservarIntentoOtp(usuario.getId(), MAX_OTP_ATTEMPTS) == 0) {
            throw new OtpBloqueadoException();
        }

        if (!usuario.getCodigoOtp().equals(otp)) {
            throw new OtpInvalidoException();
        }
    }
}
