package com.io.appioweb.adapters.web.relatorios;

import com.io.appioweb.application.auth.port.out.CompanyRepositoryPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AtendimentoReportsService {

    private final NamedParameterJdbcTemplate jdbc;
    private final CompanyRepositoryPort companies;

    public AtendimentoReportsService(NamedParameterJdbcTemplate jdbc, CompanyRepositoryPort companies) {
        this.jdbc = jdbc;
        this.companies = companies;
    }

    public AtendimentoOverviewHttpResponse loadOverview(UUID companyId, AtendimentoReportFilter filter) {
        FilterWindow window = FilterWindow.from(filter);
        MapSqlParameterSource params = baseParams(companyId, window);

        AtendimentoOverviewHttpResponse.SummaryCards cards = new AtendimentoOverviewHttpResponse.SummaryCards(
                countByCondition(params, "s.arrived_at < :startAt and (s.completed_at is null or s.completed_at >= :startAt)"),
                countByCondition(params, "s.arrived_at >= :startAt and s.arrived_at < :endExclusive"),
                countByCondition(params, "s.completed_at >= :startAt and s.completed_at < :endExclusive"),
                countByCondition(params, "s.arrived_at < :endExclusive and (s.completed_at is null or s.completed_at >= :endExclusive)")
        );

        List<AtendimentoOverviewHttpResponse.CapacityPoint> capacitySeries = jdbc.query("""
                with days as (
                    select generate_series(:startDate::date, :endDate::date, interval '1 day')::date as day
                )
                select
                    to_char(day, 'YYYY-MM-DD') as day_key,
                    (
                        select count(*)
                        from atendimento_sessions s
                        where s.company_id = :companyId
                          and timezone(:timeZone, s.arrived_at)::date = day
                          %s
                    ) as new_count,
                    (
                        select count(*)
                        from atendimento_sessions s
                        where s.company_id = :companyId
                          and s.completed_at is not null
                          and timezone(:timeZone, s.completed_at)::date = day
                          %s
                    ) as completed_count,
                    (
                        select count(*)
                        from atendimento_sessions s
                        where s.company_id = :companyId
                          and s.arrived_at < ((day + interval '1 day')::timestamp at time zone :timeZone)
                          and (s.completed_at is null or s.completed_at >= ((day + interval '1 day')::timestamp at time zone :timeZone))
                          %s
                    ) as pending_count
                from days
                order by day
                """.formatted(optionalSessionFilters("s"), optionalSessionFilters("s"), optionalSessionFilters("s")),
                params,
                (rs, rowNum) -> new AtendimentoOverviewHttpResponse.CapacityPoint(
                        rs.getString("day_key"),
                        rs.getLong("new_count"),
                        rs.getLong("completed_count"),
                        rs.getLong("pending_count")
                ));

        AtendimentoOverviewHttpResponse.WaitMetric waitTime = new AtendimentoOverviewHttpResponse.WaitMetric(
                "seconds",
                averageSeconds(params, """
                        select avg(extract(epoch from (s.started_at - s.arrived_at)))
                        from atendimento_sessions s
                        where s.company_id = :companyId
                          and s.arrived_at >= :startAt
                          and s.arrived_at < :endExclusive
                          and s.started_at is not null
                          %s
                        """.formatted(optionalSessionFilters("s"))),
                jdbc.query("""
                        with days as (
                            select generate_series(:startDate::date, :endDate::date, interval '1 day')::date as day
                        ),
                        eligible as (
                            select
                                timezone(:timeZone, s.arrived_at)::date as day,
                                avg(extract(epoch from (s.started_at - s.arrived_at))) as average_seconds,
                                count(*) as started_count
                            from atendimento_sessions s
                            where s.company_id = :companyId
                              and s.arrived_at >= :startAt
                              and s.arrived_at < :endExclusive
                              and s.started_at is not null
                              %s
                            group by 1
                        )
                        select
                            to_char(d.day, 'YYYY-MM-DD') as day_key,
                            coalesce(e.average_seconds, 0) as average_seconds,
                            coalesce(e.started_count, 0) as started_count
                        from days d
                        left join eligible e
                          on e.day = d.day
                        order by d.day
                        """.formatted(optionalSessionFilters("s")),
                        params,
                        (rs, rowNum) -> new AtendimentoOverviewHttpResponse.WaitPoint(
                                rs.getString("day_key"),
                                getNullableDouble(rs, "average_seconds"),
                                rs.getLong("started_count")
                        ))
        );

        AtendimentoOverviewHttpResponse.DurationMetric duration = new AtendimentoOverviewHttpResponse.DurationMetric(
                "seconds",
                averageSeconds(params, """
                        select avg(extract(epoch from (s.completed_at - s.started_at)))
                        from atendimento_sessions s
                        where s.company_id = :companyId
                          and s.arrived_at >= :startAt
                          and s.arrived_at < :endExclusive
                          and s.started_at is not null
                          and s.completed_at is not null
                          %s
                        """.formatted(optionalSessionFilters("s"))),
                jdbc.query("""
                        with days as (
                            select generate_series(:startDate::date, :endDate::date, interval '1 day')::date as day
                        ),
                        eligible as (
                            select
                                timezone(:timeZone, s.arrived_at)::date as day,
                                avg(extract(epoch from (s.completed_at - s.started_at))) as average_seconds,
                                count(*) as completed_count
                            from atendimento_sessions s
                            where s.company_id = :companyId
                              and s.arrived_at >= :startAt
                              and s.arrived_at < :endExclusive
                              and s.started_at is not null
                              and s.completed_at is not null
                              %s
                            group by 1
                        )
                        select
                            to_char(d.day, 'YYYY-MM-DD') as day_key,
                            coalesce(e.average_seconds, 0) as average_seconds,
                            coalesce(e.completed_count, 0) as completed_count
                        from days d
                        left join eligible e
                          on e.day = d.day
                        order by d.day
                        """.formatted(optionalSessionFilters("s")),
                        params,
                        (rs, rowNum) -> new AtendimentoOverviewHttpResponse.DurationPoint(
                                rs.getString("day_key"),
                                getNullableDouble(rs, "average_seconds"),
                                rs.getLong("completed_count")
                        ))
        );

        List<AtendimentoOverviewHttpResponse.RankingPoint> channelRanking = jdbc.query("""
                select
                    coalesce(nullif(trim(s.channel_id), ''), 'default') as id,
                    coalesce(nullif(trim(s.channel_name), ''), 'Integração') as label,
                    count(*) as total
                from atendimento_sessions s
                where s.company_id = :companyId
                  and s.arrived_at >= :startAt
                  and s.arrived_at < :endExclusive
                  %s
                group by 1, 2
                order by total desc, label asc
                """.formatted(optionalSessionFilters("s")),
                params,
                (rs, rowNum) -> new AtendimentoOverviewHttpResponse.RankingPoint(
                        rs.getString("id"),
                        rs.getString("label"),
                        null,
                        rs.getLong("total")
                ));

        List<AtendimentoOverviewHttpResponse.RankingPoint> tagRanking = jdbc.query("""
                select
                    l.label_id,
                    l.label_title,
                    l.label_color,
                    count(distinct s.id) as total
                from atendimento_sessions s
                join atendimento_session_labels l
                  on l.session_id = s.id
                 and l.company_id = s.company_id
                where s.company_id = :companyId
                  and s.arrived_at >= :startAt
                  and s.arrived_at < :endExclusive
                  %s
                group by l.label_id, l.label_title, l.label_color
                order by total desc, l.label_title asc
                limit 10
                """.formatted(optionalSessionFilters("s")),
                params,
                (rs, rowNum) -> new AtendimentoOverviewHttpResponse.RankingPoint(
                        rs.getString("label_id"),
                        rs.getString("label_title"),
                        rs.getString("label_color"),
                        rs.getLong("total")
                ));

        List<AtendimentoOverviewHttpResponse.HeatmapPoint> heatmapPoints = jdbc.query("""
                with days as (
                    select generate_series(:startDate::date, :endDate::date, interval '1 day')::date as day
                ),
                hours as (
                    select generate_series(0, 23) as hour
                ),
                counts as (
                    select
                        timezone(:timeZone, s.arrived_at)::date as day,
                        extract(hour from timezone(:timeZone, s.arrived_at))::int as hour,
                        count(*) as total
                    from atendimento_sessions s
                    where s.company_id = :companyId
                      and s.arrived_at >= :startAt
                      and s.arrived_at < :endExclusive
                      %s
                    group by 1, 2
                )
                select
                    to_char(d.day, 'YYYY-MM-DD') as day_key,
                    h.hour,
                    coalesce(c.total, 0) as total
                from days d
                cross join hours h
                left join counts c
                  on c.day = d.day
                 and c.hour = h.hour
                order by d.day, h.hour
                """.formatted(optionalSessionFilters("s")),
                params,
                (rs, rowNum) -> new AtendimentoOverviewHttpResponse.HeatmapPoint(
                        rs.getString("day_key"),
                        rs.getInt("hour"),
                        rs.getLong("total")
                ));

        AtendimentoOverviewHttpResponse.PeakSlot peakSlot = jdbc.query("""
                select
                    to_char(timezone(:timeZone, s.arrived_at)::date, 'YYYY-MM-DD') as day_key,
                    extract(hour from timezone(:timeZone, s.arrived_at))::int as hour,
                    count(*) as total
                from atendimento_sessions s
                where s.company_id = :companyId
                  and s.arrived_at >= :startAt
                  and s.arrived_at < :endExclusive
                  %s
                group by 1, 2
                order by total desc, day_key asc, hour asc
                limit 1
                """.formatted(optionalSessionFilters("s")),
                params,
                rs -> rs.next()
                        ? new AtendimentoOverviewHttpResponse.PeakSlot(rs.getString("day_key"), rs.getInt("hour"), rs.getLong("total"))
                        : null);

        return new AtendimentoOverviewHttpResponse(
                window.timeZone(),
                window.startDate().toString(),
                window.endDate().toString(),
                loadChannels(companyId),
                cards,
                capacitySeries,
                waitTime,
                duration,
                channelRanking,
                tagRanking,
                new AtendimentoOverviewHttpResponse.VolumeHeatmap(heatmapPoints, peakSlot)
        );
    }

    public AtendimentoUserReportHttpResponse loadUserReport(UUID companyId, AtendimentoReportFilter filter) {
        FilterWindow window = FilterWindow.from(filter);
        MapSqlParameterSource params = baseParams(companyId, window);

        List<AtendimentoUserReportHttpResponse.UserMetricRow> rows = jdbc.query("""
                select
                    u.id as user_id,
                    u.full_name as user_name,
                    count(*) as completed_count,
                    count(*) filter (where s.classification_result = 'OBJECTIVE_ACHIEVED') as achieved_count,
                    count(*) filter (where s.classification_result = 'OBJECTIVE_LOST') as lost_count,
                    avg(extract(epoch from (s.first_response_at - s.started_at)))
                        filter (where s.first_response_at is not null and s.started_at is not null) as avg_first_response_seconds,
                    avg(extract(epoch from (s.completed_at - s.started_at)))
                        filter (where s.completed_at is not null and s.started_at is not null) as avg_attendance_seconds
                from atendimento_sessions s
                join users u
                  on u.id = s.responsible_user_id
                 and u.company_id = s.company_id
                where s.company_id = :companyId
                  and s.completed_at >= :startAt
                  and s.completed_at < :endExclusive
                  %s
                group by u.id, u.full_name
                having count(*) > 0
                order by completed_count desc, u.full_name asc
                """.formatted(optionalSessionFilters("s")),
                params,
                (rs, rowNum) -> {
                    long completed = rs.getLong("completed_count");
                    long achieved = rs.getLong("achieved_count");
                    double percentage = completed == 0 ? 0d : (achieved * 100d) / completed;
                    return new AtendimentoUserReportHttpResponse.UserMetricRow(
                            rs.getString("user_id"),
                            rs.getString("user_name"),
                            completed,
                            achieved,
                            percentage,
                            rs.getLong("lost_count"),
                            getNullableDouble(rs, "avg_first_response_seconds"),
                            getNullableDouble(rs, "avg_attendance_seconds")
                    );
                });

        return new AtendimentoUserReportHttpResponse(
                window.timeZone(),
                window.startDate().toString(),
                window.endDate().toString(),
                rows
        );
    }

    public AtendimentoResultsReportHttpResponse loadResults(UUID companyId, AtendimentoReportFilter filter) {
        FilterWindow window = FilterWindow.from(filter);
        MapSqlParameterSource params = baseParams(companyId, window);
        params.addValue("nowAt", Timestamp.from(Instant.now()), Types.TIMESTAMP);

        Long totalCompleted = jdbc.queryForObject("""
                select count(*)
                from atendimento_sessions s
                where s.company_id = :companyId
                  and s.completed_at >= :startAt
                  and s.completed_at < :endExclusive
                  %s
                """.formatted(optionalSessionFilters("s")),
                params,
                Long.class);

        Long unclassifiedTotal = jdbc.queryForObject("""
                select count(*)
                from atendimento_sessions s
                where s.company_id = :companyId
                  and s.completed_at >= :startAt
                  and s.completed_at < :endExclusive
                  and s.classification_result is null
                  %s
                """.formatted(optionalSessionFilters("s")),
                params,
                Long.class);

        List<ClassificationAggregate> grouped = jdbc.query("""
                with filtered as (
                    select
                        s.classification_result as key,
                        coalesce(
                            nullif(trim(s.classification_label), ''),
                            case s.classification_result
                                when 'OBJECTIVE_ACHIEVED' then 'Objetivo atingido'
                                when 'OBJECTIVE_LOST' then 'Objetivo perdido'
                                when 'QUESTION' then 'Dúvida'
                                when 'OTHER' then 'Outro'
                            end
                        ) as classification_title
                    from atendimento_sessions s
                    where s.company_id = :companyId
                      and s.completed_at >= :startAt
                      and s.completed_at < :endExclusive
                      and s.classification_result is not null
                      %s
                ),
                totals as (
                    select key, count(*) as total
                    from filtered
                    group by key
                ),
                titles as (
                    select
                        key,
                        classification_title,
                        count(*) as title_total,
                        row_number() over (
                            partition by key
                            order by count(*) desc, classification_title asc
                        ) as rn
                    from filtered
                    group by key, classification_title
                )
                select
                    totals.key,
                    totals.total,
                    titles.classification_title
                from totals
                left join titles
                  on titles.key = totals.key
                 and titles.rn = 1
                order by totals.key
                """.formatted(optionalSessionFilters("s")),
                params,
                (rs, rowNum) -> new ClassificationAggregate(
                        rs.getString("key"),
                        rs.getLong("total"),
                        rs.getString("classification_title")
                ));

        Map<String, ClassificationAggregate> aggregatesByKey = new LinkedHashMap<>();
        for (ClassificationAggregate aggregate : grouped) {
            aggregatesByKey.put(aggregate.key(), aggregate);
        }
        long unclassifiedCount = unclassifiedTotal == null ? 0L : unclassifiedTotal;

        List<AtendimentoResultsReportHttpResponse.ClassificationItem> classifications = List.of(
                classificationItem("OBJECTIVE_ACHIEVED", aggregatesByKey, totalCompleted),
                classificationItem("OBJECTIVE_LOST", aggregatesByKey, totalCompleted),
                classificationItem("QUESTION", aggregatesByKey, totalCompleted),
                classificationItem("OTHER", aggregatesByKey, totalCompleted)
        );

        List<AtendimentoResultsReportHttpResponse.UnclassifiedAttendanceItem> unclassifiedAttendances = jdbc.query("""
                select
                    s.id as session_id,
                    coalesce(nullif(trim(s.channel_name), ''), 'Integração') as channel_name,
                    coalesce(nullif(trim(c.display_name), ''), c.phone) as contact_name,
                    coalesce(nullif(trim(s.responsible_user_name), ''), 'Sem responsavel') as responsible_user_name,
                    s.started_at,
                    case
                        when s.started_at is null then null
                        else extract(epoch from (coalesce(s.completed_at, :nowAt) - s.started_at))
                    end as duration_seconds
                from atendimento_sessions s
                join atendimento_conversations c
                  on c.id = s.conversation_id
                 and c.company_id = s.company_id
                where s.company_id = :companyId
                  and s.completed_at >= :startAt
                  and s.completed_at < :endExclusive
                  and s.classification_result is null
                  %s
                order by s.completed_at desc, s.started_at desc nulls last
                limit 100
                """.formatted(optionalSessionFilters("s")),
                params,
                (rs, rowNum) -> new AtendimentoResultsReportHttpResponse.UnclassifiedAttendanceItem(
                        rs.getString("session_id"),
                        rs.getString("channel_name"),
                        rs.getString("contact_name"),
                        rs.getString("responsible_user_name"),
                        rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toInstant().toString(),
                        getNullableDouble(rs, "duration_seconds")
                ));

        return new AtendimentoResultsReportHttpResponse(
                window.timeZone(),
                window.startDate().toString(),
                window.endDate().toString(),
                totalCompleted == null ? 0L : totalCompleted,
                unclassifiedCount,
                classifications,
                unclassifiedAttendances
        );
    }

    private AtendimentoResultsReportHttpResponse.ClassificationItem classificationItem(
            String key,
            Map<String, ClassificationAggregate> aggregatesByKey,
            Long totalCompleted
    ) {
        ClassificationAggregate aggregate = aggregatesByKey.get(key);
        long total = aggregate == null ? 0L : aggregate.total();
        double percentage = totalCompleted == null || totalCompleted == 0 ? 0d : (total * 100d) / totalCompleted;
        String categoryLabel = categoryLabelForResultKey(key);
        String classificationTitle = trimToNull(aggregate == null ? null : aggregate.classificationTitle());
        return new AtendimentoResultsReportHttpResponse.ClassificationItem(
                key,
                categoryLabel,
                classificationTitle == null ? categoryLabel : classificationTitle,
                total,
                percentage
        );
    }

    private String categoryLabelForResultKey(String key) {
        return switch (key) {
            case "OBJECTIVE_ACHIEVED" -> "Objetivo atingido";
            case "OBJECTIVE_LOST" -> "Objetivo perdido";
            case "QUESTION" -> "Dúvida";
            default -> "Outro";
        };
    }

    private List<AtendimentoOverviewHttpResponse.ChannelOption> loadChannels(UUID companyId) {
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", companyId);
        List<AtendimentoOverviewHttpResponse.ChannelOption> rows = jdbc.query("""
                select distinct
                    coalesce(nullif(trim(channel_id), ''), 'default') as id,
                    coalesce(nullif(trim(channel_name), ''), 'Integração') as label
                from atendimento_sessions
                where company_id = :companyId
                order by label asc
                """,
                params,
                (rs, rowNum) -> new AtendimentoOverviewHttpResponse.ChannelOption(rs.getString("id"), rs.getString("label")));
        if (!rows.isEmpty()) {
            return rows;
        }

        String defaultLabel = companies.findById(companyId)
                .map(company -> {
                    String number = trimToNull(company.whatsappNumber());
                    return number == null ? "Integração" : number;
                })
                .orElse("Integração");
        return List.of(new AtendimentoOverviewHttpResponse.ChannelOption("default", defaultLabel));
    }

    private List<AtendimentoOverviewHttpResponse.TrendPoint> loadTrendSeries(MapSqlParameterSource params, String sql) {
        return jdbc.query(sql, params, (rs, rowNum) -> new AtendimentoOverviewHttpResponse.TrendPoint(
                rs.getString("day_key"),
                getNullableDouble(rs, "value_seconds")
        ));
    }

    private Double averageSeconds(MapSqlParameterSource params, String sql) {
        return jdbc.query(sql, params, rs -> rs.next() ? getNullableDouble(rs, 1) : null);
    }

    private long countByCondition(MapSqlParameterSource params, String conditionSql) {
        Long result = jdbc.queryForObject("""
                select count(*)
                from atendimento_sessions s
                where s.company_id = :companyId
                  and %s
                  %s
                """.formatted(conditionSql, optionalSessionFilters("s")),
                params,
                Long.class);
        return result == null ? 0L : result;
    }

    private MapSqlParameterSource baseParams(UUID companyId, FilterWindow window) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("companyId", companyId, Types.OTHER);
        params.addValue("startAt", Timestamp.from(window.startAt()), Types.TIMESTAMP);
        params.addValue("endExclusive", Timestamp.from(window.endExclusive()), Types.TIMESTAMP);
        params.addValue("startDate", Date.valueOf(window.startDate()), Types.DATE);
        params.addValue("endDate", Date.valueOf(window.endDate()), Types.DATE);
        params.addValue("timeZone", window.timeZone(), Types.VARCHAR);
        params.addValue("userId", window.userId(), Types.OTHER);
        params.addValue("teamId", window.teamId(), Types.OTHER);
        params.addValue("channelId", window.channelId(), Types.VARCHAR);
        return params;
    }

    private String optionalSessionFilters(String alias) {
        return """
                and (cast(:userId as uuid) is null or %1$s.responsible_user_id = cast(:userId as uuid))
                and (cast(:teamId as uuid) is null or %1$s.responsible_team_id = cast(:teamId as uuid))
                and (cast(:channelId as varchar) is null or %1$s.channel_id = cast(:channelId as varchar))
                """.formatted(alias);
    }

    private static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Double getNullableDouble(ResultSet rs, int columnIndex) throws SQLException {
        double value = rs.getDouble(columnIndex);
        return rs.wasNull() ? null : value;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record AtendimentoReportFilter(
            LocalDate startDate,
            LocalDate endDate,
            UUID userId,
            UUID teamId,
            String channelId,
            String timeZone
    ) {
    }

    public record FilterWindow(
            LocalDate startDate,
            LocalDate endDate,
            UUID userId,
            UUID teamId,
            String channelId,
            String timeZone,
            Instant startAt,
            Instant endExclusive
    ) {
        static FilterWindow from(AtendimentoReportFilter filter) {
            LocalDate endDate = filter.endDate() == null ? LocalDate.now(ZoneOffset.UTC) : filter.endDate();
            LocalDate startDate = filter.startDate() == null ? endDate.minusDays(6) : filter.startDate();
            if (startDate.isAfter(endDate)) {
                LocalDate swap = startDate;
                startDate = endDate;
                endDate = swap;
            }

            String timeZone = trimToNull(filter.timeZone());
            if (timeZone == null) {
                timeZone = "America/Sao_Paulo";
            }
            ZoneId zone = ZoneId.of(timeZone);
            Instant startAt = startDate.atStartOfDay(zone).toInstant();
            Instant endExclusive = endDate.plusDays(1).atStartOfDay(zone).toInstant();
            return new FilterWindow(startDate, endDate, filter.userId(), filter.teamId(), trimToNull(filter.channelId()), timeZone, startAt, endExclusive);
        }
    }

    private record ClassificationAggregate(String key, long total, String classificationTitle) {
    }
}
