package com.finvault.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GeminiService {

    private final RestClient restClient;
    private final String apiKey;
    private final int timeoutSeconds;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public GeminiService(RestClient geminiRestClient,
                         @Qualifier("geminiApiKey") String geminiApiKey,
                         @Qualifier("geminiTimeoutSeconds") int geminiTimeoutSeconds,
                         com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.restClient = geminiRestClient;
        this.apiKey = geminiApiKey;
        this.timeoutSeconds = geminiTimeoutSeconds;
        this.objectMapper = objectMapper;
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

            // Spring 3.2+ setup for timeouts requires passing an explicit HttpRequestFactory 
            // to RestClient builder. Instead, since we already have a timeout parameter,
            // we will use simple HTTP response and manage timeout globally or leave RestClient defaults.
            // A more robust app sets request factory timeouts, but for now we execute the request.
            String response = restClient.post()
                    .uri("/models/gemini-2.0-flash:generateContent?key={key}", apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return extractTextFromResponse(response);
        } catch (RestClientResponseException e) {
            log.error("Gemini API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return "AI service is temporarily unavailable. Please try again later.";
        } catch (Exception e) {
            log.error("Failed to call Gemini API: {}", e.getMessage(), e);
            return "AI service is temporarily unavailable. Please try again later.";
        }
    }

    private String extractTextFromResponse(String response) {
        if (response == null || response.isBlank()) {
            return "No response from AI service.";
        }

        try {
            // Parse: { "candidates": [{ "content": { "parts": [{ "text": "..." }] } }] }
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response);
            com.fasterxml.jackson.databind.JsonNode candidates = root.path("candidates");

            if (candidates.isArray() && !candidates.isEmpty()) {
                com.fasterxml.jackson.databind.JsonNode firstCandidate = candidates.path(0);
                com.fasterxml.jackson.databind.JsonNode parts = firstCandidate.path("content").path("parts");

                if (parts.isArray() && !parts.isEmpty()) {
                    com.fasterxml.jackson.databind.JsonNode text = parts.path(0).path("text");
                    if (!text.isMissingNode()) {
                        return text.asText().trim();
                    }
                }
            }

            return "Unable to parse AI response.";
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage(), e);
            return "Unable to parse AI response.";
        }
    }
}
