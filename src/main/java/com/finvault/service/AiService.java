package com.finvault.service;

import com.finvault.dto.response.AiCategorizationResponse;
import com.finvault.dto.response.AiInsightsResponse;
import com.finvault.dto.response.FinancialRecordResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final GeminiService geminiService;

    public AiCategorizationResponse categorize(String description) {
        String prompt = "Classify the following financial transaction into exactly one category. " +
                "Return only the category name, nothing else. No explanation, no punctuation, just the category. " +
                "Examples of categories: Food, Transport, Shopping, Salary, Entertainment, Utilities, Healthcare, Education, Travel, Other. " +
                "Transaction: " + description;

        String category = geminiService.generateContent(prompt);
        log.info("AI categorization: '{}' -> '{}'", description, category);

        return AiCategorizationResponse.builder()
                .description(description)
                .suggestedCategory(category)
                .build();
    }

    public AiInsightsResponse getFinancialInsights(List<FinancialRecordResponse> records) {
        if (records.isEmpty()) {
            return AiInsightsResponse.builder()
                    .insights("No financial records available to analyze. Start adding records to receive AI-powered insights.")
                    .recordCount(0)
                    .build();
        }

        String recordsSummary = records.stream()
                .map(r -> String.format("%s | %s | %s | %s", r.getType(), r.getCategory(), r.getAmount(), r.getDate()))
                .collect(Collectors.joining("\n"));

        String prompt = "You are a financial advisor AI. Analyze the following financial records and provide 3-5 actionable insights " +
                "about spending patterns, savings opportunities, and budget recommendations.\n\n" +
                "Records (Type | Category | Amount | Date):\n" + recordsSummary + "\n\n" +
                "Provide clear, concise, and actionable advice. Format each insight as a numbered point.";

        String insights = geminiService.generateContent(prompt);
        log.info("AI insights generated for {} records", records.size());

        return AiInsightsResponse.builder()
                .insights(insights)
                .recordCount(records.size())
                .build();
    }
}
