package com.finanzas.api.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyOtpDTO {
    @Email(message = "{email.invalid}")
    @NotBlank(message = "{email.notblank}")
    private String email;

    @NotBlank(message = "{otp.notblank}")
    @Pattern(regexp = "^\\d{4}$", message = "{otp.pattern}")
    private String otp;
}