package com.finanzas.api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponseDTO<T> {
    private LocalDateTime timestamp;
    private int status;
    private String code;
    private String message;
    private T data;
    private String path;

    public static <T> ApiResponseDTO<T> success(int status, String code, String message, T data, String path) {
        return ApiResponseDTO.<T>builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .code(code)
                .message(message)
                .data(data)
                .path(path)
                .build();
    }
}
