package com.finanzas.api.usuario;

import com.finanzas.api.shared.exception.specific.*;
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

    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          EmailService emailService,
                          AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          RefreshTokenService refreshTokenService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public void registrarUsuario(UsuarioRegistroDTO dto) {
        if (usuarioRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new EmailDuplicadoException();
        }

        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setEmail(dto.getEmail());
        nuevoUsuario.setNombre(dto.getNombre());
        nuevoUsuario.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        nuevoUsuario.setTipoNegocio(dto.getTipoNegocio());

        usuarioRepository.save(nuevoUsuario);
    }

    public LoginResponseDTO login(LoginDTO dto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword())
        );

        UsuarioPrincipal userPrincipal = (UsuarioPrincipal) authentication.getPrincipal();
        Usuario usuario = userPrincipal.getUsuario();
        String jwtToken = jwtService.generateToken(userPrincipal);
        String refreshToken = refreshTokenService.emitir(usuario);

        return construirRespuesta(usuario, jwtToken, refreshToken);
    }

    // Exchanges a valid refresh token for a fresh access token and a rotated
    // refresh token. The old refresh token is invalidated in the process.
    public LoginResponseDTO refrescarToken(RefreshRequestDTO dto) {
        RefreshTokenService.Rotacion rotacion = refreshTokenService.rotar(dto.getRefreshToken());
        Usuario usuario = rotacion.usuario();
        String jwtToken = jwtService.generateToken(new UsuarioPrincipal(usuario));
        return construirRespuesta(usuario, jwtToken, rotacion.rawToken());
    }

    private LoginResponseDTO construirRespuesta(Usuario usuario, String accessToken, String refreshToken) {
        return LoginResponseDTO.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .usuarioId(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .tipoNegocio(usuario.getTipoNegocio())
                .build();
    }

    // Logout is public like refresh: possession of the refresh token IS the
    // credential, so it also works when the access token already expired.
    public void logout(RefreshRequestDTO dto) {
        refreshTokenService.revocar(dto.getRefreshToken());
    }

    @Transactional
    public void generarOtpRecuperacion(ForgotPasswordDTO dto) {
        usuarioRepository.findByEmail(dto.getEmail()).ifPresent(usuario -> {
            String otp = String.format("%04d", SECURE_RANDOM.nextInt(10000));
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
    }

    @Transactional
    public void resetPassword(ResetPasswordDTO dto) {
        Usuario usuario = buscarParaOtp(dto.getEmail());
        validarOtp(usuario, dto.getOtp());

        usuario.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        usuario.setCodigoOtp(null);
        usuario.setExpiracionOtp(null);
        usuario.setIntentosOtp(0);
        usuarioRepository.save(usuario);
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

        if (usuario.getIntentosOtp() >= MAX_OTP_ATTEMPTS) {
            throw new OtpBloqueadoException();
        }

        if (usuario.getExpiracionOtp().isBefore(LocalDateTime.now())) {
            throw new OtpExpiradoException();
        }

        if (!usuario.getCodigoOtp().equals(otp)) {
            usuario.setIntentosOtp(usuario.getIntentosOtp() + 1);
            usuarioRepository.save(usuario);
            throw new OtpInvalidoException();
        }
    }
}
