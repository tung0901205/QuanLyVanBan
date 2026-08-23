package com.qlda.notificationservice.client.dto;

public record WorkflowStatisticsClientResponse(
    Long totalTasks,
    Long completedTasks,
    Long processingTasks,
    Long overdueTasks
) {
}
