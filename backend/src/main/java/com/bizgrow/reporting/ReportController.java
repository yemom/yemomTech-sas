package com.YemomBizGrow.reporting;

import com.YemomBizGrow.common.security.CurrentUser;
import com.YemomBizGrow.reporting.dto.ComprehensiveReportResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/comprehensive")
    public ResponseEntity<ComprehensiveReportResponse> getComprehensiveReport(
            @PathVariable UUID organizationId
    ) {
        UUID actorUserId = CurrentUser.require().id();
        ComprehensiveReportResponse response = reportService.generateComprehensiveReport(actorUserId, organizationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportReportCsv(
            @PathVariable UUID organizationId
    ) {
        UUID actorUserId = CurrentUser.require().id();
        String csv = reportService.exportReportCsv(actorUserId, organizationId);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bizgrow-report-" + organizationId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }
}
