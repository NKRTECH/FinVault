package com.finvault.service;

import com.finvault.dto.request.FinancialRecordRequest;
import com.finvault.dto.response.FinancialRecordResponse;
import com.finvault.entity.FinancialRecord;
import com.finvault.entity.User;
import com.finvault.enums.RecordType;
import com.finvault.enums.UserStatus;
import com.finvault.exception.ResourceNotFoundException;
import com.finvault.repository.FinancialRecordRepository;
import com.finvault.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialRecordServiceTest {

    @Mock
    private FinancialRecordRepository recordRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FinancialRecordService recordService;

    private User testUser;
    private FinancialRecord testRecord;
    private FinancialRecordRequest testRequest;

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

        testRecord = FinancialRecord.builder()
                .id(1L)
                .amount(new BigDecimal("250.00"))
                .type(RecordType.EXPENSE)
                .category("Food")
                .description("Groceries")
                .date(LocalDate.of(2026, 3, 15))
                .createdBy(testUser)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testRequest = new FinancialRecordRequest(
                new BigDecimal("250.00"),
                RecordType.EXPENSE,
                "Food",
                "Groceries",
                LocalDate.of(2026, 3, 15)
        );
    }

    // ── createRecord ─────────────────────────────────────

    @Test
    void createRecord_shouldCreateAndReturnResponse() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(testUser));
        when(recordRepository.save(any(FinancialRecord.class))).thenReturn(testRecord);

        FinancialRecordResponse response = recordService.createRecord(testRequest, "admin");

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getAmount()).isEqualByComparingTo("250.00");
        assertThat(response.getType()).isEqualTo("EXPENSE");
        assertThat(response.getCategory()).isEqualTo("Food");
        assertThat(response.getCreatedBy()).isEqualTo("admin");
        verify(recordRepository).save(any(FinancialRecord.class));
    }

    @Test
    void createRecord_shouldThrowWhenUserNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recordService.createRecord(testRequest, "unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getAllRecords ─────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void getAllRecords_shouldReturnPaginatedResults_noFilters() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<FinancialRecord> page = new PageImpl<>(List.of(testRecord), pageable, 1);
        when(recordRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<FinancialRecordResponse> result = recordService.getAllRecords(
                pageable, null, null, null, null, null, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCategory()).isEqualTo("Food");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAllRecords_shouldApplyTypeFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<FinancialRecord> page = new PageImpl<>(List.of(testRecord), pageable, 1);
        when(recordRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<FinancialRecordResponse> result = recordService.getAllRecords(
                pageable, RecordType.EXPENSE, null, null, null, null, null);

        assertThat(result.getContent()).hasSize(1);
        verify(recordRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAllRecords_shouldApplyDateRange() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<FinancialRecord> page = new PageImpl<>(List.of(), pageable, 0);
        when(recordRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<FinancialRecordResponse> result = recordService.getAllRecords(
                pageable, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30),
                null, null);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAllRecords_shouldApplyAmountRange() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<FinancialRecord> page = new PageImpl<>(List.of(testRecord), pageable, 1);
        when(recordRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<FinancialRecordResponse> result = recordService.getAllRecords(
                pageable, null, null, null, null,
                new BigDecimal("100"), new BigDecimal("500"));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAllRecords_shouldApplyCombinedFilters() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<FinancialRecord> page = new PageImpl<>(List.of(testRecord), pageable, 1);
        when(recordRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<FinancialRecordResponse> result = recordService.getAllRecords(
                pageable, RecordType.EXPENSE, "Food",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("50"), new BigDecimal("1000"));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getType()).isEqualTo("EXPENSE");
    }

    // ── getRecordById ────────────────────────────────────

    @Test
    void getRecordById_shouldReturnRecord() {
        when(recordRepository.findById(1L)).thenReturn(Optional.of(testRecord));

        FinancialRecordResponse response = recordService.getRecordById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCategory()).isEqualTo("Food");
    }

    @Test
    void getRecordById_shouldThrowWhenNotFound() {
        when(recordRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recordService.getRecordById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getRecordById_shouldThrowWhenSoftDeleted() {
        FinancialRecord deletedRecord = FinancialRecord.builder()
                .id(2L).amount(new BigDecimal("100")).type(RecordType.INCOME)
                .category("Salary").date(LocalDate.now()).createdBy(testUser)
                .deleted(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(recordRepository.findById(2L)).thenReturn(Optional.of(deletedRecord));

        assertThatThrownBy(() -> recordService.getRecordById(2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── updateRecord ─────────────────────────────────────

    @Test
    void updateRecord_shouldUpdateAndReturn() {
        when(recordRepository.findById(1L)).thenReturn(Optional.of(testRecord));
        when(recordRepository.save(any(FinancialRecord.class))).thenReturn(testRecord);

        FinancialRecordRequest updateReq = new FinancialRecordRequest(
                new BigDecimal("300.00"), RecordType.EXPENSE, "Dining",
                "Restaurant dinner", LocalDate.of(2026, 3, 20));

        FinancialRecordResponse response = recordService.updateRecord(1L, updateReq);

        assertThat(response).isNotNull();
        verify(recordRepository).save(any(FinancialRecord.class));
    }

    @Test
    void updateRecord_shouldThrowWhenNotFound() {
        when(recordRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recordService.updateRecord(999L, testRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── deleteRecord ─────────────────────────────────────

    @Test
    void deleteRecord_shouldSoftDelete() {
        when(recordRepository.findById(1L)).thenReturn(Optional.of(testRecord));
        when(recordRepository.save(any(FinancialRecord.class))).thenReturn(testRecord);

        recordService.deleteRecord(1L);

        verify(recordRepository).save(argThat(rec -> rec.isDeleted()));
    }

    @Test
    void deleteRecord_shouldThrowWhenNotFound() {
        when(recordRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recordService.deleteRecord(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRecord_shouldThrowWhenAlreadyDeleted() {
        FinancialRecord alreadyDeleted = FinancialRecord.builder()
                .id(3L).amount(new BigDecimal("50")).type(RecordType.EXPENSE)
                .category("Other").date(LocalDate.now()).createdBy(testUser)
                .deleted(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(recordRepository.findById(3L)).thenReturn(Optional.of(alreadyDeleted));

        assertThatThrownBy(() -> recordService.deleteRecord(3L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
