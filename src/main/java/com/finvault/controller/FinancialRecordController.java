package com.finvault.controller;

import com.finvault.dto.request.FinancialRecordRequest;
import com.finvault.dto.response.ApiResponse;
import com.finvault.dto.response.FinancialRecordResponse;
import com.finvault.enums.RecordType;
import com.finvault.service.FinancialRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/records")
@RequiredArgsConstructor
public class FinancialRecordController {

    private final FinancialRecordService recordService;

    // ── Write endpoints (ADMIN only) ─────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FinancialRecordResponse>> createRecord(
            @Valid @RequestBody FinancialRecordRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        FinancialRecordResponse response = recordService.createRecord(request, username);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Record created successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FinancialRecordResponse>> updateRecord(
            @PathVariable Long id,
            @Valid @RequestBody FinancialRecordRequest request) {
        FinancialRecordResponse response = recordService.updateRecord(id, request);
        return ResponseEntity.ok(ApiResponse.success("Record updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteRecord(@PathVariable Long id) {
        recordService.deleteRecord(id);
        return ResponseEntity.ok(ApiResponse.success("Record deleted successfully", null));
    }

    // ── Read endpoints (VIEWER, ANALYST, ADMIN) ──────────

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'ANALYST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<FinancialRecordResponse>>> getAllRecords(
            Pageable pageable,
            @RequestParam(required = false) RecordType type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount) {
        Page<FinancialRecordResponse> records = recordService.getAllRecords(
                pageable, type, category, startDate, endDate, minAmount, maxAmount);
        return ResponseEntity.ok(ApiResponse.success("Records retrieved successfully", records));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VIEWER', 'ANALYST', 'ADMIN')")
    public ResponseEntity<ApiResponse<FinancialRecordResponse>> getRecordById(@PathVariable Long id) {
        FinancialRecordResponse response = recordService.getRecordById(id);
        return ResponseEntity.ok(ApiResponse.success("Record retrieved successfully", response));
    }
}
