package com.finvault.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GeminiService {

    private final WebClient webClient;
    private final String apiKey;
    private final int timeoutSeconds;

    public GeminiService(WebClient geminiWebClient,
                         @Qualifier("geminiApiKey") String geminiApiKey,
                         @Qualifier("geminiTimeoutSeconds") int geminiTimeoutSeconds) {
        this.webClient = geminiWebClient;
        this.apiKey = geminiApiKey;
        this.timeoutSeconds = geminiTimeoutSeconds;
    }

    public String generateContent(String prompt) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", prompt)
                            ))
                    )
            );

            String response = webClient.post()
                    .uri("/models/gemini-2.0-flash:generateContent?key={key}", apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            return extractTextFromResponse(response);
        } catch (WebClientResponseException e) {
            log.error("Gemini API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return "AI service is temporarily unavailable. Please try again later.";
        } catch (Exception e) {
            log.error("Failed to call Gemini API: {}", e.getMessage());
            return "AI service is temporarily unavailable. Please try again later.";
        }
    }

    private String extractTextFromResponse(String response) {
        if (response == null || response.isBlank()) {
            return "No response from AI service.";
        }

        try {
            // Parse: { "candidates": [{ "content": { "parts": [{ "text": "..." }] } }] }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response);
            com.fasterxml.jackson.databind.JsonNode candidates = root.path("candidates");

            if (candidates.isArray() && !candidates.isEmpty()) {
                com.fasterxml.jackson.databind.JsonNode text = candidates.get(0)
                        .path("content")
                        .path("parts")
                        .get(0)
                        .path("text");
                if (!text.isMissingNode()) {
                    return text.asText().trim();
                }
            }

            return "Unable to parse AI response.";
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
            return "Unable to parse AI response.";
        }
    }
}
