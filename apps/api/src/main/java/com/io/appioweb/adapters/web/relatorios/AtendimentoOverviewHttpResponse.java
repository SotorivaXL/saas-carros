package com.io.appioweb.adapters.web.relatorios;

import java.util.List;

public record AtendimentoOverviewHttpResponse(
        String timeZone,
        String startDate,
        String endDate,
        List<ChannelOption> channels,
        SummaryCards cards,
        List<CapacityPoint> capacitySeries,
        WaitMetric waitTime,
        DurationMetric duration,
        List<RankingPoint> channelRanking,
        List<RankingPoint> tagRanking,
        VolumeHeatmap volumeHeatmap
) {
    public record ChannelOption(String id, String label) {
    }

    public record SummaryCards(
            long pendingBeforePeriod,
            long newInPeriod,
            long completedInPeriod,
            long pendingAfterPeriod
    ) {
    }

    public record CapacityPoint(String date, long newCount, long completedCount, long pendingCount) {
    }

    public record TrendMetric(String unit, Double averageSeconds, List<TrendPoint> series) {
    }

    public record TrendPoint(String date, Double valueSeconds) {
    }

    public record WaitMetric(String unit, Double averageSeconds, List<WaitPoint> series) {
    }

    public record WaitPoint(String date, Double averageSeconds, long startedCount) {
    }

    public record DurationMetric(String unit, Double averageSeconds, List<DurationPoint> series) {
    }

    public record DurationPoint(String date, Double averageSeconds, long completedCount) {
    }

    public record RankingPoint(String id, String label, String color, long total) {
    }

    public record VolumeHeatmap(List<HeatmapPoint> points, PeakSlot peak) {
    }

    public record HeatmapPoint(String date, int hour, long total) {
    }

    public record PeakSlot(String date, int hour, long total) {
    }
}
