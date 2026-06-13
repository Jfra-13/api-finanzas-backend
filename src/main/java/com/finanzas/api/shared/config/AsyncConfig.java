package com.finanzas.api.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// Enables @Async support (used by EmailService for OTP delivery).
@Configuration
@EnableAsync
public class AsyncConfig {
}
