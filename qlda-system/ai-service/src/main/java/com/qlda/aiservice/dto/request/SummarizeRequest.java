package com.qlda.aiservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SummarizeRequest(
    @NotNull Long documentId,
    Long userId,
    String text,
    @NotBlank String summaryType,
    String language
) {
}
