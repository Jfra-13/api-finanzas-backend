package com.finanzas.api.service;

import com.finanzas.api.exception.specific.*;
import com.finanzas.api.model.dto.*;
import com.finanzas.api.model.entity.Usuario;
import com.finanzas.api.repository.UsuarioRepository;
import com.finanzas.api.security.JwtService;
import com.finanzas.api.security.UsuarioPrincipal;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public UsuarioService(UsuarioRepository usuarioRepository, 
                          PasswordEncoder passwordEncoder, 
                          JavaMailSender mailSender, 
                          AuthenticationManager authenticationManager, 
                          JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
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

        usuarioRepository.save(nuevoUsuario);
    }

    public LoginResponseDTO login(LoginDTO dto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword())
        );

        UsuarioPrincipal userPrincipal = (UsuarioPrincipal) authentication.getPrincipal();
        Usuario usuario = userPrincipal.getUsuario();
        String jwtToken = jwtService.generateToken(userPrincipal);

        return LoginResponseDTO.builder()
                .token(jwtToken)
                .usuarioId(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .tipoNegocio(usuario.getTipoNegocio())
                .build();
    }

    @Transactional
    public void generarOtpRecuperacion(ForgotPasswordDTO dto) {
        usuarioRepository.findByEmail(dto.getEmail()).ifPresent(usuario -> {
            String otp = String.format("%04d", new Random().nextInt(10000));
            usuario.setCodigoOtp(otp);
            usuario.setExpiracionOtp(LocalDateTime.now().plusMinutes(10));
            usuarioRepository.save(usuario);
            enviarEmailOtp(usuario.getEmail(), otp);
        });
    }

    public void verificarOtp(VerifyOtpDTO dto) {
        Usuario usuario = buscarPorEmail(dto.getEmail());
        validarOtp(usuario, dto.getOtp());
    }

    @Transactional
    public void resetPassword(ResetPasswordDTO dto) {
        Usuario usuario = buscarPorEmail(dto.getEmail());
        validarOtp(usuario, dto.getOtp());

        usuario.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        usuario.setCodigoOtp(null);
        usuario.setExpiracionOtp(null);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void actualizarNegocio(Usuario usuario, NegocioUpdateDTO dto) {
        usuario.setTipoNegocio(dto.getTipoNegocio());
        usuarioRepository.save(usuario);
    }

    private Usuario buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(UsuarioNoEncontradoException::new);
    }

    private void validarOtp(Usuario usuario, String otp) {
        if (usuario.getCodigoOtp() == null || !usuario.getCodigoOtp().equals(otp)) {
            throw new OtpInvalidoException();
        }

        if (usuario.getExpiracionOtp().isBefore(LocalDateTime.now())) {
            throw new OtpExpiradoException();
        }
    }

    private void enviarEmailOtp(String email, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Código de Recuperación");
        message.setText("Tu código de verificación de 4 dígitos es: " + otp + "\n\nEste código expirará en 10 minutos.");
        mailSender.send(message);
    }
}
