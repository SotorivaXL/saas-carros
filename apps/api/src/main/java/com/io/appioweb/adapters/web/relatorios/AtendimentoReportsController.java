package com.io.appioweb.adapters.web.relatorios;

import com.io.appioweb.application.auth.port.out.CurrentUserPort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
public class AtendimentoReportsController {

    private final CurrentUserPort currentUser;
    private final AtendimentoReportsService reportsService;

    public AtendimentoReportsController(CurrentUserPort currentUser, AtendimentoReportsService reportsService) {
        this.currentUser = currentUser;
        this.reportsService = reportsService;
    }

    @GetMapping("/reports/atendimentos/overview")
    public AtendimentoOverviewHttpResponse overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) String channelId,
            @RequestParam(required = false) String timeZone
    ) {
        return reportsService.loadOverview(currentUser.companyId(), new AtendimentoReportsService.AtendimentoReportFilter(
                startDate,
                endDate,
                userId,
                teamId,
                channelId,
                timeZone
        ));
    }

    @GetMapping("/reports/atendimentos/users")
    public AtendimentoUserReportHttpResponse users(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) String channelId,
            @RequestParam(required = false) String timeZone
    ) {
        return reportsService.loadUserReport(currentUser.companyId(), new AtendimentoReportsService.AtendimentoReportFilter(
                startDate,
                endDate,
                userId,
                teamId,
                channelId,
                timeZone
        ));
    }

    @GetMapping("/reports/atendimentos/results")
    public AtendimentoResultsReportHttpResponse results(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) String channelId,
            @RequestParam(required = false) String timeZone
    ) {
        return reportsService.loadResults(currentUser.companyId(), new AtendimentoReportsService.AtendimentoReportFilter(
                startDate,
                endDate,
                userId,
                teamId,
                channelId,
                timeZone
        ));
    }
}
