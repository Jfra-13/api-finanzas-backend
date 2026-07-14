package com.finanzas.api.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Enables @Async support (used by EmailService for OTP delivery) and @Scheduled
// support (used by CuentaPurgaJob for expired soft-deleted accounts).
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
