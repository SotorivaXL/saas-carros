package com.io.appioweb.adapters.web.relatorios;

import java.util.List;

public record AtendimentoResultsReportHttpResponse(
        String timeZone,
        String startDate,
        String endDate,
        long totalCompleted,
        long unclassifiedCount,
        List<ClassificationItem> classifications,
        List<UnclassifiedAttendanceItem> unclassifiedAttendances
) {
    public record ClassificationItem(
            String key,
            String categoryLabel,
            String classificationTitle,
            long total,
            double percentage
    ) {
    }

    public record UnclassifiedAttendanceItem(
            String sessionId,
            String channel,
            String contactName,
            String responsibleUserName,
            String startedAt,
            Double durationSeconds
    ) {
    }
}
