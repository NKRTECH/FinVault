package com.finvault.service;

import com.finvault.dto.response.AiCategorizationResponse;
import com.finvault.dto.response.AiInsightsResponse;
import com.finvault.dto.response.FinancialRecordResponse;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private GeminiService geminiService;

    @InjectMocks
    private AiService aiService;

    // ── categorize ───────────────────────────────────────

    @Test
    void categorize_shouldReturnSuggestedCategory() {
        when(geminiService.generateContent(anyString())).thenReturn("Transport");

        AiCategorizationResponse result = aiService.categorize("Uber ride to airport");

        assertThat(result.getDescription()).isEqualTo("Uber ride to airport");
        assertThat(result.getSuggestedCategory()).isEqualTo("Transport");
        verify(geminiService).generateContent(contains("Uber ride to airport"));
    }

    @Test
    void categorize_shouldReturnFallbackOnGeminiFailure() {
        when(geminiService.generateContent(anyString()))
                .thenReturn("AI service is temporarily unavailable. Please try again later.");

        AiCategorizationResponse result = aiService.categorize("Random purchase");

        assertThat(result.getSuggestedCategory())
                .isEqualTo("AI service is temporarily unavailable. Please try again later.");
    }

    @Test
    void categorize_shouldIncludeDescriptionInPrompt() {
        when(geminiService.generateContent(anyString())).thenReturn("Food");

        aiService.categorize("Pizza delivery from Dominos");

        verify(geminiService).generateContent(contains("Pizza delivery from Dominos"));
    }

    // ── getFinancialInsights ─────────────────────────────

    @Test
    void getFinancialInsights_shouldReturnInsights() {
        List<FinancialRecordResponse> records = List.of(
                buildRecord("EXPENSE", "Food", "250.00"),
                buildRecord("INCOME", "Salary", "5000.00")
        );
        when(geminiService.generateContent(anyString()))
                .thenReturn("1. Your food expenses are 5% of income.\n2. Consider budgeting.");

        AiInsightsResponse result = aiService.getFinancialInsights(records);

        assertThat(result.getInsights()).contains("food expenses");
        assertThat(result.getRecordCount()).isEqualTo(2);
    }

    @Test
    void getFinancialInsights_shouldReturnMessageWhenNoRecords() {
        AiInsightsResponse result = aiService.getFinancialInsights(List.of());

        assertThat(result.getInsights()).contains("No financial records available");
        assertThat(result.getRecordCount()).isZero();
    }

    @Test
    void getFinancialInsights_shouldReturnFallbackOnGeminiFailure() {
        List<FinancialRecordResponse> records = List.of(
                buildRecord("EXPENSE", "Food", "100.00")
        );
        when(geminiService.generateContent(anyString()))
                .thenReturn("AI service is temporarily unavailable. Please try again later.");

        AiInsightsResponse result = aiService.getFinancialInsights(records);

        assertThat(result.getInsights())
                .isEqualTo("AI service is temporarily unavailable. Please try again later.");
        assertThat(result.getRecordCount()).isEqualTo(1);
    }

    private FinancialRecordResponse buildRecord(String type, String category, String amount) {
        return FinancialRecordResponse.builder()
                .id(1L)
                .type(type)
                .category(category)
                .amount(new BigDecimal(amount))
                .date(LocalDate.of(2026, 3, 15))
                .createdBy("admin")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
