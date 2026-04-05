package com.finvault.controller;

import com.finvault.dto.response.ApiResponse;
import com.finvault.dto.response.CategoryBreakdownResponse;
import com.finvault.dto.response.DashboardSummaryResponse;
import com.finvault.dto.response.MonthlyTrendResponse;
import com.finvault.dto.response.RecentActivityResponse;
import com.finvault.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard Analytics", description = "Aggregate financial summaries, trends, and breakdowns")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    @Operation(summary = "Dashboard summary", description = "Total income, expenses, balance, and transaction count")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        DashboardSummaryResponse summary = dashboardService.getSummary(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Dashboard summary retrieved successfully", summary));
    }

    @GetMapping("/category-breakdown")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    @Operation(summary = "Category breakdown", description = "Spending breakdown by category with percentages")
    public ResponseEntity<ApiResponse<List<CategoryBreakdownResponse>>> getCategoryBreakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<CategoryBreakdownResponse> breakdown = dashboardService.getCategoryBreakdown(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Category breakdown retrieved successfully", breakdown));
    }

    @GetMapping("/monthly-trend")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    @Operation(summary = "Monthly trend", description = "Monthly income vs expense with running balance")
    public ResponseEntity<ApiResponse<List<MonthlyTrendResponse>>> getMonthlyTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<MonthlyTrendResponse> trend = dashboardService.getMonthlyTrend(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Monthly trend retrieved successfully", trend));
    }

    @GetMapping("/recent-activity")
    @PreAuthorize("hasAnyRole('VIEWER', 'ANALYST', 'ADMIN')")
    @Operation(summary = "Recent activity", description = "Last 10 financial transactions")
    public ResponseEntity<ApiResponse<List<RecentActivityResponse>>> getRecentActivity() {
        List<RecentActivityResponse> activity = dashboardService.getRecentActivity();
        return ResponseEntity.ok(ApiResponse.success("Recent activity retrieved successfully", activity));
    }
}
