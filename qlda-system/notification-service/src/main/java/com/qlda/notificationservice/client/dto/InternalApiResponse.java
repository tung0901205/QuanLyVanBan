package com.qlda.notificationservice.client.dto;

/**
 * Envelope chung mà auth-service, document-service và workflow-service
 * sử dụng cho các API nội bộ.
 */
public record InternalApiResponse<T>(
    boolean success,
    String message,
    T data,
    String errorCode
) {
}
