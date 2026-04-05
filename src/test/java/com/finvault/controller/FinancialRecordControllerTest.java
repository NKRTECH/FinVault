package com.finvault.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvault.dto.request.FinancialRecordRequest;
import com.finvault.dto.response.FinancialRecordResponse;
import com.finvault.enums.RecordType;
import com.finvault.exception.GlobalExceptionHandler;
import com.finvault.exception.ResourceNotFoundException;
import com.finvault.security.CustomUserDetailsService;
import com.finvault.security.JwtAuthenticationFilter;
import com.finvault.security.JwtTokenProvider;
import com.finvault.service.FinancialRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FinancialRecordController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FinancialRecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FinancialRecordService recordService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private FinancialRecordResponse testResponse;

    @BeforeEach
    void setUp() {
        testResponse = FinancialRecordResponse.builder()
                .id(1L)
                .amount(new BigDecimal("250.00"))
                .type("EXPENSE")
                .category("Food")
                .description("Groceries")
                .date(LocalDate.of(2026, 3, 15))
                .createdBy("admin")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Clear any existing authentication so each test sets its own security context explicitly
        SecurityContextHolder.clearContext();
    }

    private void setAuthentication(String username, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ── POST / (create) ──────────────────────────────────

    @Test
    void createRecord_shouldReturn201() throws Exception {
        setAuthentication("admin", "ROLE_ADMIN");
        when(recordService.createRecord(any(FinancialRecordRequest.class), eq("admin")))
                .thenReturn(testResponse);

        FinancialRecordRequest request = new FinancialRecordRequest(
                new BigDecimal("250.00"), RecordType.EXPENSE, "Food",
                "Groceries", LocalDate.of(2026, 3, 15));

        mockMvc.perform(post("/api/v1/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.category").value("Food"))
                .andExpect(jsonPath("$.data.createdBy").value("admin"));
    }

    @Test
    void createRecord_shouldReturn400ForInvalidPayload() throws Exception {
        // Missing required fields: amount, type, category, date
        String invalidJson = "{}";

        mockMvc.perform(post("/api/v1/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRecord_shouldReturn400ForNegativeAmount() throws Exception {
        FinancialRecordRequest request = new FinancialRecordRequest(
                new BigDecimal("-50.00"), RecordType.EXPENSE, "Food",
                "Invalid", LocalDate.of(2026, 3, 15));

        mockMvc.perform(post("/api/v1/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── GET / (list) ─────────────────────────────────────

    @Test
    void getAllRecords_shouldReturnPaginatedList() throws Exception {
        Page<FinancialRecordResponse> page = new PageImpl<>(List.of(testResponse));
        when(recordService.getAllRecords(any(Pageable.class),
                any(), any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].category").value("Food"));
    }

    @Test
    void getAllRecords_shouldAcceptFilterParams() throws Exception {
        Page<FinancialRecordResponse> page = new PageImpl<>(List.of(testResponse));
        when(recordService.getAllRecords(any(Pageable.class),
                any(), any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/records")
                        .param("type", "EXPENSE")
                        .param("category", "Food")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-12-31")
                        .param("minAmount", "10")
                        .param("maxAmount", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── GET /{id} (single) ───────────────────────────────

    @Test
    void getRecordById_shouldReturnRecord() throws Exception {
        when(recordService.getRecordById(1L)).thenReturn(testResponse);

        mockMvc.perform(get("/api/v1/records/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.type").value("EXPENSE"));
    }

    @Test
    void getRecordById_shouldReturn404WhenNotFound() throws Exception {
        when(recordService.getRecordById(999L))
                .thenThrow(new ResourceNotFoundException("FinancialRecord", "id", "999"));

        mockMvc.perform(get("/api/v1/records/999"))
                .andExpect(status().isNotFound());
    }

    // ── PUT /{id} (update) ───────────────────────────────

    @Test
    void updateRecord_shouldReturn200() throws Exception {
        when(recordService.updateRecord(eq(1L), any(FinancialRecordRequest.class)))
                .thenReturn(testResponse);

        FinancialRecordRequest request = new FinancialRecordRequest(
                new BigDecimal("300.00"), RecordType.EXPENSE, "Dining",
                "Restaurant dinner", LocalDate.of(2026, 3, 20));

        mockMvc.perform(put("/api/v1/records/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── DELETE /{id} (soft-delete) ───────────────────────

    @Test
    void deleteRecord_shouldReturn200() throws Exception {
        doNothing().when(recordService).deleteRecord(1L);

        mockMvc.perform(delete("/api/v1/records/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Record deleted successfully"));
    }

    @Test
    void deleteRecord_shouldReturn404WhenNotFound() throws Exception {
        when(recordService.getRecordById(999L))
                .thenThrow(new ResourceNotFoundException("FinancialRecord", "id", "999"));
        // deleteRecord also throws ResourceNotFoundException
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("FinancialRecord", "id", "999"))
                .when(recordService).deleteRecord(999L);

        mockMvc.perform(delete("/api/v1/records/999"))
                .andExpect(status().isNotFound());
    }
}
