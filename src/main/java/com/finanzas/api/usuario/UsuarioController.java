package com.finanzas.api.usuario;

import com.finanzas.api.shared.dto.ApiResponseDTO;
import com.finanzas.api.usuario.dto.LoginDTO;
import com.finanzas.api.usuario.dto.LoginResponseDTO;
import com.finanzas.api.usuario.dto.UsuarioRegistroDTO;
import com.finanzas.api.usuario.dto.RefreshRequestDTO;
import com.finanzas.api.usuario.dto.ForgotPasswordDTO;
import com.finanzas.api.usuario.dto.VerifyOtpDTO;
import com.finanzas.api.usuario.dto.ResetPasswordDTO;
import com.finanzas.api.usuario.dto.EliminarCuentaDTO;
import com.finanzas.api.usuario.dto.NegocioUpdateDTO;
import com.finanzas.api.usuario.dto.PerfilResponseDTO;
import com.finanzas.api.usuario.dto.PerfilUpdateDTO;
import com.finanzas.api.security.UsuarioPrincipal;
import io.swagger.v3.oas.annotations.Operation;
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

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponseDTO<LoginResponseDTO>> refrescarToken(@Valid @RequestBody RefreshRequestDTO dto) {
        LoginResponseDTO responseDTO = usuarioService.refrescarToken(dto);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "TOKEN_REFRESHED", "Token renovado", responseDTO, "/api/v1/usuarios/refresh"));
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

    @Operation(summary = "Perfil del usuario autenticado",
            description = "Única fuente para la cabecera de Perfil y el detalle de Cuenta. "
                    + "telefono, fotoUrl y plan son nullable; fotoUrl y plan aún no tienen backend y llegan null.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponseDTO<PerfilResponseDTO>> obtenerPerfil(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal) {
        PerfilResponseDTO perfil = usuarioService.obtenerPerfil(userPrincipal.getUsuario());
        return ResponseEntity.ok(ApiResponseDTO.success(200, "PROFILE_OK", "Perfil obtenido", perfil, "/api/v1/usuarios/me"));
    }

    @Operation(summary = "Actualizar perfil (parcial)",
            description = "Solo cambian los campos presentes en el body (nombre, telefono). "
                    + "El email no es editable; tipoNegocio tiene su propio endpoint.")
    @PutMapping("/me")
    public ResponseEntity<ApiResponseDTO<PerfilResponseDTO>> actualizarPerfil(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @Valid @RequestBody PerfilUpdateDTO dto) {
        PerfilResponseDTO perfil = usuarioService.actualizarPerfil(userPrincipal.getUsuario(), dto);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "PROFILE_UPDATED", "Perfil actualizado", perfil, "/api/v1/usuarios/me"));
    }

    @Operation(summary = "Cerrar sesión (revoca el refresh token)",
            description = "Revoca el refresh token en el servidor. Idempotente: un token desconocido o ya "
                    + "revocado también responde 200 LOGGED_OUT. El access token sigue válido hasta su "
                    + "expiración (15 min); el cliente debe descartarlo localmente.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponseDTO<String>> logout(@Valid @RequestBody RefreshRequestDTO dto) {
        usuarioService.logout(dto);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "LOGGED_OUT", "Sesión cerrada", null, "/api/v1/usuarios/logout"));
    }

    @Operation(summary = "Eliminar cuenta (soft-delete con 30 días de gracia)",
            description = "CONTRATO: requiere la contraseña en el body para confirmar identidad; incorrecta "
                    + "responde 401 CREDENCIALES_INVALIDAS. La cuenta se marca como eliminada y se revocan "
                    + "todos los refresh tokens (el access token vigente expira solo, máx. 15 min). "
                    + "Iniciar sesión dentro de los 30 días de gracia REACTIVA la cuenta con todos sus datos; "
                    + "pasado ese plazo el login responde 404 USUARIO_NO_ENCONTRADO y los datos se purgan "
                    + "definitivamente.")
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponseDTO<String>> eliminarCuenta(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @Valid @RequestBody EliminarCuentaDTO dto) {
        usuarioService.eliminarCuenta(userPrincipal.getUsuario(), dto);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "ACCOUNT_DELETED", "Cuenta eliminada", null, "/api/v1/usuarios/me"));
    }

    @PutMapping("/me/negocio")
    public ResponseEntity<ApiResponseDTO<String>> actualizarNegocio(
            @AuthenticationPrincipal UsuarioPrincipal userPrincipal,
            @Valid @RequestBody NegocioUpdateDTO dto) {
        usuarioService.actualizarNegocio(userPrincipal.getUsuario(), dto);
        return ResponseEntity.ok(ApiResponseDTO.success(200, "BUSINESS_UPDATED", "Negocio actualizado a: " + dto.getTipoNegocio(), null, "/api/v1/usuarios/me/negocio"));
    }
}
