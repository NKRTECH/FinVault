package com.finvault.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentActivityResponse {

    private Long id;
    private BigDecimal amount;
    private String type;
    private String category;
    private LocalDate date;
    private String createdBy;
    private LocalDateTime createdAt;
}
