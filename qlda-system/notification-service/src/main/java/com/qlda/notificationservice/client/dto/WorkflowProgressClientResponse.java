package com.qlda.notificationservice.client.dto;

import java.util.List;

public record WorkflowProgressClientResponse(
    Long totalTasks,
    Long completedTasks,
    Long processingTasks,
    List<WorkflowProgressClientItem> items
) {
}
