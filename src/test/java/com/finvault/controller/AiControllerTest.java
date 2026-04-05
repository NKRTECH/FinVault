package com.finvault.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvault.dto.response.AiCategorizationResponse;
import com.finvault.dto.response.AiInsightsResponse;
import com.finvault.dto.response.FinancialRecordResponse;
import com.finvault.exception.GlobalExceptionHandler;
import com.finvault.security.CustomUserDetailsService;
import com.finvault.security.JwtAuthenticationFilter;
import com.finvault.security.JwtTokenProvider;
import com.finvault.service.AiService;
import com.finvault.service.FinancialRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiService aiService;

    @MockitoBean
    private FinancialRecordService recordService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    // ── POST /categorize ─────────────────────────────────

    @Test
    void categorize_shouldReturn200() throws Exception {
        AiCategorizationResponse response = AiCategorizationResponse.builder()
                .description("Uber ride to airport")
                .suggestedCategory("Transport")
                .build();
        when(aiService.categorize(anyString())).thenReturn(response);

        mockMvc.perform(post("/api/v1/ai/categorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"Uber ride to airport\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.suggestedCategory").value("Transport"))
                .andExpect(jsonPath("$.data.description").value("Uber ride to airport"));
    }

    @Test
    void categorize_shouldReturn400WhenDescriptionBlank() throws Exception {
        mockMvc.perform(post("/api/v1/ai/categorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void categorize_shouldReturn400WhenDescriptionMissing() throws Exception {
        mockMvc.perform(post("/api/v1/ai/categorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /insights ────────────────────────────────────

    @Test
    void getInsights_shouldReturn200() throws Exception {
        List<FinancialRecordResponse> records = List.of(
                FinancialRecordResponse.builder()
                        .id(1L).type("EXPENSE").category("Food")
                        .amount(new BigDecimal("250.00"))
                        .date(LocalDate.of(2026, 3, 15))
                        .createdBy("admin")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );
        when(recordService.getAllRecords(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(records, PageRequest.of(0, 50), 1));
        when(aiService.getFinancialInsights(any()))
                .thenReturn(AiInsightsResponse.builder()
                        .insights("1. Your food spending is high.")
                        .recordCount(1)
                        .build());

        mockMvc.perform(get("/api/v1/ai/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.insights").value("1. Your food spending is high."))
                .andExpect(jsonPath("$.data.recordCount").value(1));
    }

    @Test
    void getInsights_shouldReturn200WhenNoRecords() throws Exception {
        when(recordService.getAllRecords(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
        when(aiService.getFinancialInsights(any()))
                .thenReturn(AiInsightsResponse.builder()
                        .insights("No financial records available to analyze.")
                        .recordCount(0)
                        .build());

        mockMvc.perform(get("/api/v1/ai/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordCount").value(0));
    }
}
