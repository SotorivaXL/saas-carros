package com.io.appioweb.adapters.web.relatorios;

import java.util.List;

public record AtendimentoUserReportHttpResponse(
        String timeZone,
        String startDate,
        String endDate,
        List<UserMetricRow> rows
) {
    public record UserMetricRow(
            String userId,
            String userName,
            long completedCount,
            long achievedCount,
            double achievedPercentage,
            long lostCount,
            Double averageFirstResponseSeconds,
            Double averageAttendanceSeconds
    ) {
    }
}
