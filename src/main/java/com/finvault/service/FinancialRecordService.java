package com.finvault.service;

import com.finvault.dto.request.FinancialRecordRequest;
import com.finvault.dto.response.FinancialRecordResponse;
import com.finvault.entity.FinancialRecord;
import com.finvault.entity.User;
import com.finvault.enums.RecordType;
import com.finvault.exception.ResourceNotFoundException;
import com.finvault.repository.FinancialRecordRepository;
import com.finvault.repository.FinancialRecordSpecification;
import com.finvault.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialRecordService {

    private final FinancialRecordRepository recordRepository;
    private final UserRepository userRepository;

    @Transactional
    public FinancialRecordResponse createRecord(FinancialRecordRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        FinancialRecord record = FinancialRecord.builder()
                .amount(request.getAmount())
                .type(request.getType())
                .category(request.getCategory())
                .description(request.getDescription())
                .date(request.getDate())
                .createdBy(user)
                .build();

        FinancialRecord saved = recordRepository.save(record);
        log.info("Financial record created: id={}, type={}, category={}, createdBy={}",
                saved.getId(), saved.getType(), saved.getCategory(), username);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<FinancialRecordResponse> getAllRecords(Pageable pageable,
                                                       RecordType type,
                                                       String category,
                                                       LocalDate startDate,
                                                       LocalDate endDate,
                                                       BigDecimal minAmount,
                                                       BigDecimal maxAmount) {
        Specification<FinancialRecord> spec = Specification.where(FinancialRecordSpecification.notDeleted());

        if (type != null) {
            spec = spec.and(FinancialRecordSpecification.hasType(type));
        }
        if (category != null && !category.isBlank()) {
            spec = spec.and(FinancialRecordSpecification.hasCategory(category));
        }
        if (startDate != null) {
            spec = spec.and(FinancialRecordSpecification.dateOnOrAfter(startDate));
        }
        if (endDate != null) {
            spec = spec.and(FinancialRecordSpecification.dateOnOrBefore(endDate));
        }
        if (minAmount != null) {
            spec = spec.and(FinancialRecordSpecification.amountGreaterThanOrEqual(minAmount));
        }
        if (maxAmount != null) {
            spec = spec.and(FinancialRecordSpecification.amountLessThanOrEqual(maxAmount));
        }

        return recordRepository.findAll(spec, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public FinancialRecordResponse getRecordById(Long id) {
        FinancialRecord record = findActiveRecordOrThrow(id);
        return mapToResponse(record);
    }

    @Transactional
    public FinancialRecordResponse updateRecord(Long id, FinancialRecordRequest request) {
        FinancialRecord record = findActiveRecordOrThrow(id);

        record.setAmount(request.getAmount());
        record.setType(request.getType());
        record.setCategory(request.getCategory());
        record.setDescription(request.getDescription());
        record.setDate(request.getDate());

        FinancialRecord updated = recordRepository.save(record);
        log.info("Financial record updated: id={}", id);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteRecord(Long id) {
        FinancialRecord record = findActiveRecordOrThrow(id);
        record.setDeleted(true);
        recordRepository.save(record);
        log.info("Financial record soft-deleted: id={}", id);
    }

    private FinancialRecord findActiveRecordOrThrow(Long id) {
        FinancialRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FinancialRecord", "id", id.toString()));
        if (record.isDeleted()) {
            throw new ResourceNotFoundException("FinancialRecord", "id", id.toString());
        }
        return record;
    }

    private FinancialRecordResponse mapToResponse(FinancialRecord record) {
        return FinancialRecordResponse.builder()
                .id(record.getId())
                .amount(record.getAmount())
                .type(record.getType().name())
                .category(record.getCategory())
                .description(record.getDescription())
                .date(record.getDate())
                .createdBy(record.getCreatedBy().getUsername())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}
