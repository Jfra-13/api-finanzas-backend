package com.finanzas.api.controller;

import com.finanzas.api.model.dto.*;
import com.finanzas.api.security.UsuarioPrincipal;
import com.finanzas.api.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping("/registro")
    public ResponseEntity<ApiResponseDTO<String>> registrarUsuario(@Valid @RequestBody UsuarioRegistroDTO dto) {
        usuarioService.registrarUsuario(dto);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "USER_REGISTERED", "¡Usuario registrado con éxito!", null, "/api/v1/usuarios/registro"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponseDTO<LoginResponseDTO>> iniciarSesion(@Valid @RequestBody LoginDTO dto) {
        LoginResponseDTO responseDTO = usuarioService.login(dto);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "LOGIN_SUCCESS", "Login exitoso", responseDTO, "/api/v1/usuarios/login"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponseDTO<String>> forgotPassword(@Valid @RequestBody ForgotPasswordDTO dto) {
        usuarioService.generarOtpRecuperacion(dto);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "OTP_SENT", "Si el correo existe, se enviará un código.", null, "/api/v1/usuarios/forgot-password"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponseDTO<String>> verifyOtp(@Valid @RequestBody VerifyOtpDTO dto) {
        usuarioService.verificarOtp(dto);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "OTP_VERIFIED", "Código verificado correctamente", null, "/api/v1/usuarios/verify-otp"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponseDTO<String>> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        usuarioService.resetPassword(dto);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "PASSWORD_RESET_SUCCESS", "Contraseña actualizada exitosamente", null, "/api/v1/usuarios/reset-password"));
    }

    @PutMapping("/me/negocio")
    public ResponseEntity<ApiResponseDTO<String>> actualizarNegocio(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @Valid @RequestBody NegocioUpdateDTO dto) {
        usuarioService.actualizarNegocio(userPrincipal.getUsuario(), dto);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "BUSINESS_UPDATED", "Negocio actualizado a: " + dto.getTipoNegocio(), null, "/api/v1/usuarios/me/negocio"));
    }
}