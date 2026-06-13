package com.finanzas.api.usuario;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

// OTP email is sent off the request thread (@Async) so a slow Gmail SMTP never
// blocks the password-recovery response. Lives in its own bean because @Async
// only applies through the Spring proxy, not on self-invoked private methods.
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void enviarOtp(String email, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Código de Recuperación");
        message.setText("Tu código de verificación de 4 dígitos es: " + otp + "\n\nEste código expirará en 10 minutos.");
        mailSender.send(message);
    }
}
