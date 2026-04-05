package com.finvault.repository;

import com.finvault.entity.FinancialRecord;
import com.finvault.entity.User;
import com.finvault.enums.RecordType;
import com.finvault.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class FinancialRecordSpecificationTest {

    @Autowired
    private FinancialRecordRepository recordRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("spec_user")
                .email("spec@test.com")
                .password("hash")
                .fullName("Spec Tester")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(testUser);

        // Seed diverse records
        recordRepository.saveAll(List.of(
                buildRecord("150.00", RecordType.EXPENSE, "Food", LocalDate.of(2026, 1, 10), false),
                buildRecord("3000.00", RecordType.INCOME, "Salary", LocalDate.of(2026, 1, 31), false),
                buildRecord("85.00", RecordType.EXPENSE, "Transport", LocalDate.of(2026, 2, 15), false),
                buildRecord("200.00", RecordType.EXPENSE, "Food", LocalDate.of(2026, 3, 5), false),
                buildRecord("500.00", RecordType.EXPENSE, "Shopping", LocalDate.of(2026, 4, 1), true) // soft-deleted
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

    @Test
    void notDeleted_shouldExcludeSoftDeletedRecords() {
        Specification<FinancialRecord> spec = FinancialRecordSpecification.notDeleted();
        List<FinancialRecord> results = recordRepository.findAll(spec);

        assertThat(results).hasSize(4); // 5 total minus 1 deleted
        assertThat(results).noneMatch(FinancialRecord::isDeleted);
    }

    @Test
    void hasType_shouldFilterByType() {
        Specification<FinancialRecord> spec = Specification
                .where(FinancialRecordSpecification.notDeleted())
                .and(FinancialRecordSpecification.hasType(RecordType.INCOME));

        List<FinancialRecord> results = recordRepository.findAll(spec);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCategory()).isEqualTo("Salary");
    }

    @Test
    void hasCategory_shouldFilterByCategory() {
        Specification<FinancialRecord> spec = Specification
                .where(FinancialRecordSpecification.notDeleted())
                .and(FinancialRecordSpecification.hasCategory("Food"));

        List<FinancialRecord> results = recordRepository.findAll(spec);

        assertThat(results).hasSize(2);
    }

    @Test
    void dateRange_shouldFilterByDateRange() {
        Specification<FinancialRecord> spec = Specification
                .where(FinancialRecordSpecification.notDeleted())
                .and(FinancialRecordSpecification.dateOnOrAfter(LocalDate.of(2026, 2, 1)))
                .and(FinancialRecordSpecification.dateOnOrBefore(LocalDate.of(2026, 3, 31)));

        List<FinancialRecord> results = recordRepository.findAll(spec);

        assertThat(results).hasSize(2); // Transport (Feb 15) + Food (Mar 5)
    }

    @Test
    void amountRange_shouldFilterByAmountRange() {
        Specification<FinancialRecord> spec = Specification
                .where(FinancialRecordSpecification.notDeleted())
                .and(FinancialRecordSpecification.amountGreaterThanOrEqual(new BigDecimal("100")))
                .and(FinancialRecordSpecification.amountLessThanOrEqual(new BigDecimal("300")));

        List<FinancialRecord> results = recordRepository.findAll(spec);

        assertThat(results).hasSize(2); // Food 150 + Food 200
    }

    @Test
    void combinedFilters_shouldComposeCorrectly() {
        Specification<FinancialRecord> spec = Specification
                .where(FinancialRecordSpecification.notDeleted())
                .and(FinancialRecordSpecification.hasType(RecordType.EXPENSE))
                .and(FinancialRecordSpecification.hasCategory("Food"))
                .and(FinancialRecordSpecification.amountGreaterThanOrEqual(new BigDecimal("100")))
                .and(FinancialRecordSpecification.dateOnOrAfter(LocalDate.of(2026, 3, 1)));

        List<FinancialRecord> results = recordRepository.findAll(spec);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getAmount()).isEqualByComparingTo("200.00");
    }
}
