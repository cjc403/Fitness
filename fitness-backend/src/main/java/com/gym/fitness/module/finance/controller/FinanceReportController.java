package com.gym.fitness.module.finance.controller;
import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.finance.dto.FinanceDtos.Report;
import com.gym.fitness.module.finance.service.FinanceReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
@RestController @RequestMapping("/api/v1/finance/report") @RequiredArgsConstructor
public class FinanceReportController {
    private final FinanceReportService reports;
    @GetMapping @PreAuthorize("hasAuthority('FINANCE_REPORT_READ')")
    public Result<Report> report(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){return Result.success(reports.report(from,to));}
    @GetMapping("/export") @PreAuthorize("hasAuthority('FINANCE_REPORT_READ')")
    public ResponseEntity<byte[]> export(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=finance-report.csv").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(reports.export(from,to));}
}
