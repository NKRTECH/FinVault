package com.finvault.controller;

import com.finvault.dto.request.AiCategorizationRequest;
import com.finvault.dto.response.AiCategorizationResponse;
import com.finvault.dto.response.AiInsightsResponse;
import com.finvault.dto.response.ApiResponse;
import com.finvault.dto.response.FinancialRecordResponse;
import com.finvault.service.AiService;
import com.finvault.service.FinancialRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Features", description = "Gemini-powered transaction categorization and financial insights")
@SecurityRequirement(name = "bearerAuth")
public class AiController {

    private final AiService aiService;
    private final FinancialRecordService recordService;

    @PostMapping("/categorize")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    @Operation(summary = "Categorize transaction", description = "AI-categorize a transaction description into a spending category")
    public ResponseEntity<ApiResponse<AiCategorizationResponse>> categorize(
            @Valid @RequestBody AiCategorizationRequest request) {
        AiCategorizationResponse response = aiService.categorize(request.getDescription());
        return ResponseEntity.ok(ApiResponse.success("Categorization completed", response));
    }

    @GetMapping("/insights")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    @Operation(summary = "Financial insights", description = "AI-generated analysis and recommendations based on recent transactions")
    public ResponseEntity<ApiResponse<AiInsightsResponse>> getInsights() {
        // Fetch the 50 most recent non-deleted records for analysis
        List<FinancialRecordResponse> records = recordService.getAllRecords(
                PageRequest.of(0, 50, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")), null, null, null, null, null, null
        ).getContent();

        AiInsightsResponse response = aiService.getFinancialInsights(records);
        return ResponseEntity.ok(ApiResponse.success("Financial insights generated", response));
    }
}
