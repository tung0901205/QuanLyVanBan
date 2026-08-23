package com.qlda.notificationservice.client.dto;

import java.util.List;

public record DocumentStatisticsClientResponse(
    Long totalDocuments,
    Long incomingDocuments,
    Long outgoingDocuments,
    List<StatisticClientItem> items
) {
}
