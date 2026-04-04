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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class FinancialRecordRepositoryTest {

    @Autowired
    private FinancialRecordRepository recordRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private FinancialRecord testRecord;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("record_tester")
                .email("record@test.com")
                .password("hash")
                .fullName("Tester")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(testUser);

        testRecord = FinancialRecord.builder()
                .amount(new BigDecimal("150.50"))
                .type(RecordType.EXPENSE)
                .category("Food")
                .description("Groceries")
                .date(LocalDate.of(2026, 1, 15))
                .createdBy(testUser)
                .deleted(false)
                .build();
        recordRepository.save(testRecord);
    }

    @Test
    void shouldSaveAndFindRecord() {
        Optional<FinancialRecord> found = recordRepository.findById(testRecord.getId());
        
        assertThat(found).isPresent();
        assertThat(found.get().getAmount()).isEqualByComparingTo("150.50");
        assertThat(found.get().getCategory()).isEqualTo("Food");
        assertThat(found.get().getCreatedBy().getUsername()).isEqualTo("record_tester");
    }

    @Test
    void shouldUpdateRecord() {
        FinancialRecord record = recordRepository.findById(testRecord.getId()).orElseThrow();
        record.setAmount(new BigDecimal("200.00"));
        recordRepository.save(record);

        FinancialRecord updated = recordRepository.findById(testRecord.getId()).orElseThrow();
        assertThat(updated.getAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void shouldSoftDeleteRecord() {
        FinancialRecord record = recordRepository.findById(testRecord.getId()).orElseThrow();
        record.setDeleted(true);
        recordRepository.save(record);

        FinancialRecord updated = recordRepository.findById(testRecord.getId()).orElseThrow();
        assertThat(updated.getDeleted()).isTrue();
    }
}
