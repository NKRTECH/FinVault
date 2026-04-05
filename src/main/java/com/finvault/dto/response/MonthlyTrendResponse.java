package com.finvault.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyTrendResponse {

    private String month;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netAmount;
    private BigDecimal runningBalance;
}
