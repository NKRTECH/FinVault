package com.finvault.repository;

import com.finvault.entity.FinancialRecord;
import com.finvault.entity.User;
import com.finvault.enums.RecordType;
import com.finvault.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DashboardQueryTest {

    @Autowired
    private FinancialRecordRepository recordRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("dash_user")
                .email("dash@test.com")
                .password("hash")
                .fullName("Dashboard Tester")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(testUser);

        recordRepository.saveAll(List.of(
                buildRecord("3000.00", RecordType.INCOME, "Salary", LocalDate.of(2026, 1, 15), false),
                buildRecord("150.00", RecordType.EXPENSE, "Food", LocalDate.of(2026, 1, 20), false),
                buildRecord("3000.00", RecordType.INCOME, "Salary", LocalDate.of(2026, 2, 15), false),
                buildRecord("200.00", RecordType.EXPENSE, "Food", LocalDate.of(2026, 2, 18), false),
                buildRecord("85.00", RecordType.EXPENSE, "Transport", LocalDate.of(2026, 2, 25), false),
                buildRecord("500.00", RecordType.EXPENSE, "Shopping", LocalDate.of(2026, 3, 5), true) // soft-deleted
        ));
    }

    private FinancialRecord buildRecord(String amount, RecordType type, String category,
                                         LocalDate date, boolean deleted) {
        return FinancialRecord.builder()
                .amount(new BigDecimal(amount))
                .type(type)
                .category(category)
                .date(date)
                .createdBy(testUser)
                .deleted(deleted)
                .build();
    }

    // ── sumAmountByType ──────────────────────────────────

    @Test
    void sumAmountByType_shouldSumIncomeExcludingDeleted() {
        BigDecimal total = recordRepository.sumAmountByType(RecordType.INCOME, null, null);
        assertThat(total).isEqualByComparingTo("6000.00"); // 3000 + 3000
    }

    @Test
    void sumAmountByType_shouldSumExpenseExcludingDeleted() {
        BigDecimal total = recordRepository.sumAmountByType(RecordType.EXPENSE, null, null);
        assertThat(total).isEqualByComparingTo("435.00"); // 150 + 200 + 85 (not 500 deleted)
    }

    @Test
    void sumAmountByType_shouldReturnZeroWhenNoMatch() {
        BigDecimal total = recordRepository.sumAmountByType(RecordType.INCOME,
                LocalDate.of(2099, 1, 1), LocalDate.of(2099, 12, 31));
        assertThat(total).isEqualByComparingTo("0");
    }

    @Test
    void sumAmountByType_shouldFilterByDateRange() {
        BigDecimal total = recordRepository.sumAmountByType(RecordType.EXPENSE,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        assertThat(total).isEqualByComparingTo("285.00"); // 200 + 85
    }

    // ── countByTypeAndNotDeleted ─────────────────────────

    @Test
    void countByTypeAndNotDeleted_shouldCountCorrectly() {
        long count = recordRepository.countByTypeAndNotDeleted(RecordType.EXPENSE, null, null);
        assertThat(count).isEqualTo(3); // 150, 200, 85 (not 500 deleted)
    }

    // ── countAllActive ───────────────────────────────────

    @Test
    void countAllActive_shouldExcludeDeleted() {
        long count = recordRepository.countAllActive(null, null);
        assertThat(count).isEqualTo(5); // 6 total minus 1 deleted
    }

    // ── getCategoryBreakdown ─────────────────────────────

    @Test
    void getCategoryBreakdown_shouldGroupByCategoryAndType() {
        List<Object[]> rows = recordRepository.getCategoryBreakdown(null, null);

        assertThat(rows).isNotEmpty();
        // Should have: Salary/INCOME, Food/EXPENSE, Transport/EXPENSE
        // Shopping/EXPENSE is deleted so excluded
        assertThat(rows).hasSize(3);
    }

    @Test
    void getCategoryBreakdown_shouldRespectDateRange() {
        List<Object[]> rows = recordRepository.getCategoryBreakdown(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        // Feb only: Salary/INCOME, Food/EXPENSE, Transport/EXPENSE
        assertThat(rows).hasSize(3);
    }

    // ── getMonthlyTrend ──────────────────────────────────

    @Test
    void getMonthlyTrend_shouldGroupByYearMonthAndType() {
        List<Object[]> rows = recordRepository.getMonthlyTrend(null, null);

        assertThat(rows).isNotEmpty();
        // Jan: INCOME, EXPENSE; Feb: INCOME, EXPENSE → 4 rows
        assertThat(rows).hasSize(4);

        // Verify first row is 2026-01 INCOME
        int year = ((Number) rows.get(0)[0]).intValue();
        int month = ((Number) rows.get(0)[1]).intValue();
        assertThat(year).isEqualTo(2026);
        assertThat(month).isEqualTo(1);
    }

    // ── findTop10ByDeletedFalseOrderByCreatedAtDesc ──────

    @Test
    void findTop10_shouldExcludeDeletedAndLimitTo10() {
        List<FinancialRecord> records = recordRepository.findTop10ByDeletedFalseOrderByCreatedAtDesc();

        assertThat(records).hasSize(5); // 5 active out of 6 total
        assertThat(records).noneMatch(FinancialRecord::isDeleted);
    }
}
