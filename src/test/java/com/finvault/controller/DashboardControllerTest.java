package com.finvault.controller;

import com.finvault.dto.response.ApiResponse;
import com.finvault.dto.response.CategoryBreakdownResponse;
import com.finvault.dto.response.DashboardSummaryResponse;
import com.finvault.dto.response.MonthlyTrendResponse;
import com.finvault.dto.response.RecentActivityResponse;
import com.finvault.exception.GlobalExceptionHandler;
import com.finvault.security.CustomUserDetailsService;
import com.finvault.security.JwtAuthenticationFilter;
import com.finvault.security.JwtTokenProvider;
import com.finvault.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    // ── GET /summary ─────────────────────────────────────

    @Test
    void getSummary_shouldReturn200() throws Exception {
        DashboardSummaryResponse summary = DashboardSummaryResponse.builder()
                .totalIncome(new BigDecimal("5000.00"))
                .totalExpense(new BigDecimal("3200.00"))
                .netBalance(new BigDecimal("1800.00"))
                .recordCount(13)
                .incomeCount(5)
                .expenseCount(8)
                .build();
        when(dashboardService.getSummary(any(), any())).thenReturn(summary);

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalIncome").value(5000.00))
                .andExpect(jsonPath("$.data.totalExpense").value(3200.00))
                .andExpect(jsonPath("$.data.netBalance").value(1800.00))
                .andExpect(jsonPath("$.data.recordCount").value(13));
    }

    @Test
    void getSummary_shouldAcceptDateParams() throws Exception {
        DashboardSummaryResponse summary = DashboardSummaryResponse.builder()
                .totalIncome(BigDecimal.ZERO)
                .totalExpense(BigDecimal.ZERO)
                .netBalance(BigDecimal.ZERO)
                .recordCount(0)
                .incomeCount(0)
                .expenseCount(0)
                .build();
        when(dashboardService.getSummary(
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 6, 30))))
                .thenReturn(summary);

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── GET /category-breakdown ──────────────────────────

    @Test
    void getCategoryBreakdown_shouldReturn200() throws Exception {
        List<CategoryBreakdownResponse> breakdown = List.of(
                CategoryBreakdownResponse.builder()
                        .category("Food")
                        .type("EXPENSE")
                        .totalAmount(new BigDecimal("600.00"))
                        .count(3)
                        .percentage(60.0)
                        .build()
        );
        when(dashboardService.getCategoryBreakdown(any(), any())).thenReturn(breakdown);

        mockMvc.perform(get("/api/v1/dashboard/category-breakdown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].category").value("Food"))
                .andExpect(jsonPath("$.data[0].percentage").value(60.0));
    }

    @Test
    void getCategoryBreakdown_shouldReturnEmptyList() throws Exception {
        when(dashboardService.getCategoryBreakdown(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/dashboard/category-breakdown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ── GET /monthly-trend ───────────────────────────────

    @Test
    void getMonthlyTrend_shouldReturn200() throws Exception {
        List<MonthlyTrendResponse> trend = List.of(
                MonthlyTrendResponse.builder()
                        .month("2026-01")
                        .totalIncome(new BigDecimal("3000.00"))
                        .totalExpense(new BigDecimal("1500.00"))
                        .netAmount(new BigDecimal("1500.00"))
                        .runningBalance(new BigDecimal("1500.00"))
                        .build()
        );
        when(dashboardService.getMonthlyTrend(any(), any())).thenReturn(trend);

        mockMvc.perform(get("/api/v1/dashboard/monthly-trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].month").value("2026-01"))
                .andExpect(jsonPath("$.data[0].runningBalance").value(1500.00));
    }

    // ── GET /recent-activity ─────────────────────────────

    @Test
    void getRecentActivity_shouldReturn200() throws Exception {
        List<RecentActivityResponse> activity = List.of(
                RecentActivityResponse.builder()
                        .id(1L)
                        .amount(new BigDecimal("250.00"))
                        .type("EXPENSE")
                        .category("Food")
                        .date(LocalDate.of(2026, 3, 15))
                        .createdBy("admin")
                        .createdAt(LocalDateTime.of(2026, 3, 15, 10, 30))
                        .build()
        );
        when(dashboardService.getRecentActivity()).thenReturn(activity);

        mockMvc.perform(get("/api/v1/dashboard/recent-activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].category").value("Food"))
                .andExpect(jsonPath("$.data[0].createdBy").value("admin"));
    }

    @Test
    void getRecentActivity_shouldReturnEmptyList() throws Exception {
        when(dashboardService.getRecentActivity()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/dashboard/recent-activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
