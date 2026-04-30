package com.finanzas.api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private List<FieldErrorVM> errors;
    private String path;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FieldErrorVM {
        private String field;
        private Object rejectedValue;
        private String message;
    }
}
