package com.finanzas.api.controller;

import com.finanzas.api.model.dto.*;
import com.finanzas.api.model.entity.Usuario;
import com.finanzas.api.repository.UsuarioRepository;
import com.finanzas.api.security.JwtService;
import com.finanzas.api.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender; // Inyectamos el servicio de correos
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public UsuarioController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JavaMailSender mailSender, AuthenticationManager authenticationManager, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    // 1. REGISTRO CORREGIDO
    @PostMapping("/registro")
    public ResponseEntity<ApiResponseDTO<String>> registrarUsuario(@Valid @RequestBody UsuarioRegistroDTO dto) {
        if (usuarioRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new com.finanzas.api.exception.specific.EmailDuplicadoException();
        }

        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setEmail(dto.getEmail());
        nuevoUsuario.setNombre(dto.getNombre()); // Guardamos el nombre
        nuevoUsuario.setPasswordHash(passwordEncoder.encode(dto.getPassword()));

        usuarioRepository.save(nuevoUsuario);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "USER_REGISTERED", "¡Usuario registrado con éxito!", null, "/api/v1/usuarios/registro"));
    }

    // 2. LOGIN
    @PostMapping("/login")
    public ResponseEntity<ApiResponseDTO<LoginResponseDTO>> iniciarSesion(@Valid @RequestBody LoginDTO dto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword())
        );

        UsuarioPrincipal userPrincipal = (UsuarioPrincipal) authentication.getPrincipal();
        Usuario usuario = userPrincipal.getUsuario();
        String jwtToken = jwtService.generateToken(userPrincipal);

        LoginResponseDTO responseDTO = LoginResponseDTO.builder()
                .token(jwtToken)
                .usuarioId(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .tipoNegocio(usuario.getTipoNegocio())
                .build();

        return ResponseEntity.ok(ApiResponseDTO.success(200, "LOGIN_SUCCESS", "Login exitoso", responseDTO, "/api/v1/usuarios/login"));
    }

    // 3. GENERAR Y ENVIAR OTP
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponseDTO<String>> forgotPassword(@Valid @RequestBody ForgotPasswordDTO dto) {
        Optional<Usuario> userOpt = usuarioRepository.findByEmail(dto.getEmail());
        if (userOpt.isEmpty()) {
            // Por seguridad, se suele responder OK incluso si no existe, para no revelar correos registrados.
            return ResponseEntity.ok(ApiResponseDTO.success(200, "OTP_SENT", "Si el correo existe, se enviará un código.", null, "/api/v1/usuarios/forgot-password"));
        }

        Usuario usuario = userOpt.get();

        // Generar código de 4 dígitos (0000 - 9999)
        String otp = String.format("%04d", new Random().nextInt(10000));

        usuario.setCodigoOtp(otp);
        usuario.setExpiracionOtp(LocalDateTime.now().plusMinutes(10)); // Expira en 10 mins
        usuarioRepository.save(usuario);

        // Enviar el correo
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(usuario.getEmail());
        message.setSubject("Código de Recuperación");
        message.setText("Tu código de verificación de 4 dígitos es: " + otp + "\n\nEste código expirará en 10 minutos.");
        mailSender.send(message);

        return ResponseEntity.ok(ApiResponseDTO.success(200, "OTP_SENT", "Código enviado exitosamente", null, "/api/v1/usuarios/forgot-password"));
    }

    // 4. VERIFICAR OTP
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponseDTO<String>> verifyOtp(@Valid @RequestBody VerifyOtpDTO dto) {
        Usuario usuario = usuarioRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new com.finanzas.api.exception.specific.UsuarioNoEncontradoException());

        if (usuario.getCodigoOtp() == null || !usuario.getCodigoOtp().equals(dto.getOtp())) {
            throw new com.finanzas.api.exception.specific.OtpInvalidoException();
        }

        if (usuario.getExpiracionOtp().isBefore(LocalDateTime.now())) {
            throw new com.finanzas.api.exception.specific.OtpExpiradoException();
        }

        return ResponseEntity.ok(ApiResponseDTO.success(200, "OTP_VERIFIED", "Código verificado correctamente", null, "/api/v1/usuarios/verify-otp"));
    }

    // 5. ESTABLECER NUEVA CONTRASEÑA
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponseDTO<String>> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        Usuario usuario = usuarioRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new com.finanzas.api.exception.specific.UsuarioNoEncontradoException());

        // Doble validación de seguridad
        if (usuario.getCodigoOtp() == null || !usuario.getCodigoOtp().equals(dto.getOtp())) {
            throw new com.finanzas.api.exception.specific.OtpInvalidoException();
        }

        if (usuario.getExpiracionOtp().isBefore(LocalDateTime.now())) {
            throw new com.finanzas.api.exception.specific.OtpExpiradoException();
        }

        // Encriptar y guardar nueva contraseña
        usuario.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));

        // Limpiar el OTP para que no se pueda reusar
        usuario.setCodigoOtp(null);
        usuario.setExpiracionOtp(null);

        usuarioRepository.save(usuario);

        return ResponseEntity.ok(ApiResponseDTO.success(200, "PASSWORD_RESET_SUCCESS", "Contraseña actualizada exitosamente", null, "/api/v1/usuarios/reset-password"));
    }

    // 6. ACTUALIZAR TIPO DE NEGOCIO
    @PutMapping("/me/negocio")
    public ResponseEntity<ApiResponseDTO<String>> actualizarNegocio(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @Valid @RequestBody NegocioUpdateDTO dto) {
        Usuario usuario = userPrincipal.getUsuario();
        usuario.setTipoNegocio(dto.getTipoNegocio());
        usuarioRepository.save(usuario);

        return ResponseEntity.ok(ApiResponseDTO.success(200, "BUSINESS_UPDATED", "Negocio actualizado a: " + dto.getTipoNegocio(), null, "/api/v1/usuarios/me/negocio"));
    }
}