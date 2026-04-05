package com.finvault.service;

import com.finvault.dto.response.*;
import com.finvault.entity.FinancialRecord;
import com.finvault.entity.User;
import com.finvault.enums.RecordType;
import com.finvault.enums.UserStatus;
import com.finvault.repository.FinancialRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private FinancialRecordRepository recordRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("admin")
                .email("admin@finvault.com")
                .password("encoded")
                .fullName("Admin User")
                .status(UserStatus.ACTIVE)
                .build();
    }

    // ── getSummary ───────────────────────────────────────

    @Test
    void getSummary_shouldReturnCorrectTotals() {
        when(recordRepository.sumAmountByType(eq(RecordType.INCOME), any(), any()))
                .thenReturn(new BigDecimal("5000.00"));
        when(recordRepository.sumAmountByType(eq(RecordType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("3200.00"));
        when(recordRepository.countByTypeAndNotDeleted(eq(RecordType.INCOME), any(), any()))
                .thenReturn(5L);
        when(recordRepository.countByTypeAndNotDeleted(eq(RecordType.EXPENSE), any(), any()))
                .thenReturn(8L);
        when(recordRepository.countAllActive(any(), any()))
                .thenReturn(13L);

        DashboardSummaryResponse result = dashboardService.getSummary(null, null);

        assertThat(result.getTotalIncome()).isEqualByComparingTo("5000.00");
        assertThat(result.getTotalExpense()).isEqualByComparingTo("3200.00");
        assertThat(result.getNetBalance()).isEqualByComparingTo("1800.00");
        assertThat(result.getRecordCount()).isEqualTo(13);
        assertThat(result.getIncomeCount()).isEqualTo(5);
        assertThat(result.getExpenseCount()).isEqualTo(8);
    }

    @Test
    void getSummary_shouldReturnZerosWhenNoData() {
        when(recordRepository.sumAmountByType(any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(recordRepository.countByTypeAndNotDeleted(any(), any(), any()))
                .thenReturn(0L);
        when(recordRepository.countAllActive(any(), any()))
                .thenReturn(0L);

        DashboardSummaryResponse result = dashboardService.getSummary(null, null);

        assertThat(result.getTotalIncome()).isEqualByComparingTo("0");
        assertThat(result.getTotalExpense()).isEqualByComparingTo("0");
        assertThat(result.getNetBalance()).isEqualByComparingTo("0");
        assertThat(result.getRecordCount()).isZero();
    }

    @Test
    void getSummary_shouldPassDateRangeToRepository() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);

        when(recordRepository.sumAmountByType(eq(RecordType.INCOME), eq(start), eq(end)))
                .thenReturn(new BigDecimal("2000.00"));
        when(recordRepository.sumAmountByType(eq(RecordType.EXPENSE), eq(start), eq(end)))
                .thenReturn(new BigDecimal("1500.00"));
        when(recordRepository.countByTypeAndNotDeleted(eq(RecordType.INCOME), eq(start), eq(end)))
                .thenReturn(3L);
        when(recordRepository.countByTypeAndNotDeleted(eq(RecordType.EXPENSE), eq(start), eq(end)))
                .thenReturn(4L);
        when(recordRepository.countAllActive(eq(start), eq(end)))
                .thenReturn(7L);

        DashboardSummaryResponse result = dashboardService.getSummary(start, end);

        assertThat(result.getNetBalance()).isEqualByComparingTo("500.00");
        assertThat(result.getRecordCount()).isEqualTo(7);
    }

    // ── getCategoryBreakdown ─────────────────────────────

    @Test
    void getCategoryBreakdown_shouldMapAndComputePercentages() {
        List<Object[]> rows = List.of(
                new Object[]{"Food", RecordType.EXPENSE, new BigDecimal("600.00"), 3L},
                new Object[]{"Transport", RecordType.EXPENSE, new BigDecimal("400.00"), 2L},
                new Object[]{"Salary", RecordType.INCOME, new BigDecimal("5000.00"), 1L}
        );
        when(recordRepository.getCategoryBreakdown(any(), any())).thenReturn(rows);

        List<CategoryBreakdownResponse> result = dashboardService.getCategoryBreakdown(null, null);

        assertThat(result).hasSize(3);

        // Food is 600/1000 = 60% of EXPENSE
        CategoryBreakdownResponse food = result.get(0);
        assertThat(food.getCategory()).isEqualTo("Food");
        assertThat(food.getPercentage()).isEqualTo(60.0);

        // Transport is 400/1000 = 40% of EXPENSE
        CategoryBreakdownResponse transport = result.get(1);
        assertThat(transport.getPercentage()).isEqualTo(40.0);

        // Salary is 5000/5000 = 100% of INCOME
        CategoryBreakdownResponse salary = result.get(2);
        assertThat(salary.getType()).isEqualTo("INCOME");
        assertThat(salary.getPercentage()).isEqualTo(100.0);
    }

    @Test
    void getCategoryBreakdown_shouldReturnEmptyListWhenNoData() {
        when(recordRepository.getCategoryBreakdown(any(), any())).thenReturn(List.of());

        List<CategoryBreakdownResponse> result = dashboardService.getCategoryBreakdown(null, null);

        assertThat(result).isEmpty();
    }

    // ── getMonthlyTrend ──────────────────────────────────

    @Test
    void getMonthlyTrend_shouldComputeRunningBalance() {
        List<Object[]> rows = List.of(
                new Object[]{2026, 1, RecordType.INCOME, new BigDecimal("3000.00")},
                new Object[]{2026, 1, RecordType.EXPENSE, new BigDecimal("1500.00")},
                new Object[]{2026, 2, RecordType.INCOME, new BigDecimal("3000.00")},
                new Object[]{2026, 2, RecordType.EXPENSE, new BigDecimal("2000.00")},
                new Object[]{2026, 3, RecordType.EXPENSE, new BigDecimal("500.00")}
        );
        when(recordRepository.getMonthlyTrend(any(), any())).thenReturn(rows);

        List<MonthlyTrendResponse> result = dashboardService.getMonthlyTrend(null, null);

        assertThat(result).hasSize(3);

        // Jan: income=3000, expense=1500, net=1500, running=1500
        assertThat(result.get(0).getMonth()).isEqualTo("2026-01");
        assertThat(result.get(0).getNetAmount()).isEqualByComparingTo("1500.00");
        assertThat(result.get(0).getRunningBalance()).isEqualByComparingTo("1500.00");

        // Feb: income=3000, expense=2000, net=1000, running=2500
        assertThat(result.get(1).getMonth()).isEqualTo("2026-02");
        assertThat(result.get(1).getNetAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.get(1).getRunningBalance()).isEqualByComparingTo("2500.00");

        // Mar: income=0, expense=500, net=-500, running=2000
        assertThat(result.get(2).getMonth()).isEqualTo("2026-03");
        assertThat(result.get(2).getTotalIncome()).isEqualByComparingTo("0");
        assertThat(result.get(2).getNetAmount()).isEqualByComparingTo("-500.00");
        assertThat(result.get(2).getRunningBalance()).isEqualByComparingTo("2000.00");
    }

    @Test
    void getMonthlyTrend_shouldReturnEmptyListWhenNoData() {
        when(recordRepository.getMonthlyTrend(any(), any())).thenReturn(List.of());

        List<MonthlyTrendResponse> result = dashboardService.getMonthlyTrend(null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void getMonthlyTrend_shouldHandleSingleMonth() {
        List<Object[]> rows = new java.util.ArrayList<>();
        rows.add(new Object[]{2026, 5, RecordType.INCOME, new BigDecimal("4000.00")});
        when(recordRepository.getMonthlyTrend(any(), any())).thenReturn(rows);

        List<MonthlyTrendResponse> result = dashboardService.getMonthlyTrend(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMonth()).isEqualTo("2026-05");
        assertThat(result.get(0).getTotalIncome()).isEqualByComparingTo("4000.00");
        assertThat(result.get(0).getTotalExpense()).isEqualByComparingTo("0");
        assertThat(result.get(0).getRunningBalance()).isEqualByComparingTo("4000.00");
    }

    // ── getRecentActivity ────────────────────────────────

    @Test
    void getRecentActivity_shouldReturnMappedList() {
        FinancialRecord record = FinancialRecord.builder()
                .id(1L)
                .amount(new BigDecimal("250.00"))
                .type(RecordType.EXPENSE)
                .category("Food")
                .date(LocalDate.of(2026, 3, 15))
                .createdBy(testUser)
                .createdAt(LocalDateTime.now())
                .build();
        when(recordRepository.findTop10ByDeletedFalseOrderByCreatedAtDesc())
                .thenReturn(List.of(record));

        List<RecentActivityResponse> result = dashboardService.getRecentActivity();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo("Food");
        assertThat(result.get(0).getCreatedBy()).isEqualTo("admin");
        assertThat(result.get(0).getType()).isEqualTo("EXPENSE");
    }

    @Test
    void getRecentActivity_shouldReturnEmptyListWhenNoRecords() {
        when(recordRepository.findTop10ByDeletedFalseOrderByCreatedAtDesc())
                .thenReturn(List.of());

        List<RecentActivityResponse> result = dashboardService.getRecentActivity();

        assertThat(result).isEmpty();
    }
}
