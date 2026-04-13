package com.finanzas.api.controller;

import com.finanzas.api.model.dto.*;
import com.finanzas.api.model.entity.Usuario;
import com.finanzas.api.repository.UsuarioRepository;
import org.springframework.http.ResponseEntity;
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

    public UsuarioController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JavaMailSender mailSender) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
    }

    // 1. REGISTRO CORREGIDO
    @PostMapping("/registro")
    public ResponseEntity<String> registrarUsuario(@RequestBody UsuarioRegistroDTO dto) {
        if (usuarioRepository.findByEmail(dto.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("El email ya está registrado");
        }

        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setEmail(dto.getEmail());
        nuevoUsuario.setNombre(dto.getNombre()); // Guardamos el nombre
        nuevoUsuario.setPasswordHash(passwordEncoder.encode(dto.getPassword()));

        usuarioRepository.save(nuevoUsuario);
        return ResponseEntity.ok("¡Usuario registrado con exito!");
    }

    // 2. LOGIN
    @PostMapping("/login")
    public ResponseEntity<?> iniciarSesion(@RequestBody LoginDTO dto) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(dto.getEmail());

        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Usuario no encontrado");
        }

        Usuario usuario = usuarioOpt.get();

        if (passwordEncoder.matches(dto.getPassword(), usuario.getPasswordHash())) {
            return ResponseEntity.ok(usuario.getId());
        } else {
            return ResponseEntity.badRequest().body("Contraseña incorrecta");
        }
    }

    // 3. GENERAR Y ENVIAR OTP
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordDTO dto) {
        Optional<Usuario> userOpt = usuarioRepository.findByEmail(dto.getEmail());
        if (userOpt.isEmpty()) {
            // Por seguridad, se suele responder OK incluso si no existe, para no revelar correos registrados.
            return ResponseEntity.ok("Si el correo existe, se enviará un código.");
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

        return ResponseEntity.ok("Código enviado exitosamente");
    }

    // 4. VERIFICAR OTP
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody VerifyOtpDTO dto) {
        Optional<Usuario> userOpt = usuarioRepository.findByEmail(dto.getEmail());
        if (userOpt.isEmpty()) return ResponseEntity.badRequest().body("Usuario no encontrado");

        Usuario usuario = userOpt.get();

        if (usuario.getCodigoOtp() == null || !usuario.getCodigoOtp().equals(dto.getOtp())) {
            return ResponseEntity.badRequest().body("Código incorrecto");
        }

        if (usuario.getExpiracionOtp().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body("El código ha expirado");
        }

        return ResponseEntity.ok("Código verificado correctamente");
    }

    // 5. ESTABLECER NUEVA CONTRASEÑA
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordDTO dto) {
        Optional<Usuario> userOpt = usuarioRepository.findByEmail(dto.getEmail());
        if (userOpt.isEmpty()) return ResponseEntity.badRequest().body("Usuario no encontrado");

        Usuario usuario = userOpt.get();

        // Doble validación de seguridad
        if (usuario.getCodigoOtp() == null || !usuario.getCodigoOtp().equals(dto.getOtp()) || usuario.getExpiracionOtp().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body("Código inválido o expirado");
        }

        // Encriptar y guardar nueva contraseña
        usuario.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));

        // Limpiar el OTP para que no se pueda reusar
        usuario.setCodigoOtp(null);
        usuario.setExpiracionOtp(null);

        usuarioRepository.save(usuario);

        return ResponseEntity.ok("Contraseña actualizada exitosamente");
    }

    // 6. ACTUALIZAR TIPO DE NEGOCIO
    @PutMapping("/{id}/negocio")
    public ResponseEntity<?> actualizarNegocio(@PathVariable Long id, @RequestBody NegocioUpdateDTO dto) {
        Optional<Usuario> userOpt = usuarioRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.badRequest().body("Usuario no encontrado");

        Usuario usuario = userOpt.get();
        usuario.setTipoNegocio(dto.getTipoNegocio());
        usuarioRepository.save(usuario);

        return ResponseEntity.ok("Negocio actualizado a: " + dto.getTipoNegocio());
    }
}