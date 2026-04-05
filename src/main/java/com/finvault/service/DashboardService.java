package com.finvault.service;

import com.finvault.dto.response.CategoryBreakdownResponse;
import com.finvault.dto.response.DashboardSummaryResponse;
import com.finvault.dto.response.MonthlyTrendResponse;
import com.finvault.dto.response.RecentActivityResponse;
import com.finvault.entity.FinancialRecord;
import com.finvault.enums.RecordType;
import com.finvault.repository.FinancialRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final FinancialRecordRepository recordRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(LocalDate startDate, LocalDate endDate) {
        BigDecimal totalIncome = recordRepository.sumAmountByType(RecordType.INCOME, startDate, endDate);
        BigDecimal totalExpense = recordRepository.sumAmountByType(RecordType.EXPENSE, startDate, endDate);
        long incomeCount = recordRepository.countByTypeAndNotDeleted(RecordType.INCOME, startDate, endDate);
        long expenseCount = recordRepository.countByTypeAndNotDeleted(RecordType.EXPENSE, startDate, endDate);
        long recordCount = recordRepository.countAllActive(startDate, endDate);

        return DashboardSummaryResponse.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netBalance(totalIncome.subtract(totalExpense))
                .recordCount(recordCount)
                .incomeCount(incomeCount)
                .expenseCount(expenseCount)
                .build();
    }

    @Transactional(readOnly = true)
    public List<CategoryBreakdownResponse> getCategoryBreakdown(LocalDate startDate, LocalDate endDate) {
        List<Object[]> rows = recordRepository.getCategoryBreakdown(startDate, endDate);

        if (rows.isEmpty()) {
            return List.of();
        }

        // Compute type totals for percentage calculation
        Map<RecordType, BigDecimal> typeTotals = new EnumMap<>(RecordType.class);
        for (Object[] row : rows) {
            RecordType type = (RecordType) row[1];
            BigDecimal amount = (BigDecimal) row[2];
            typeTotals.merge(type, amount, BigDecimal::add);
        }

        return rows.stream().map(row -> {
            String category = (String) row[0];
            RecordType type = (RecordType) row[1];
            BigDecimal totalAmount = (BigDecimal) row[2];
            long count = (Long) row[3];

            BigDecimal typeTotal = typeTotals.getOrDefault(type, BigDecimal.ONE);
            double percentage = typeTotal.compareTo(BigDecimal.ZERO) == 0
                    ? 0.0
                    : totalAmount.multiply(BigDecimal.valueOf(100))
                            .divide(typeTotal, 2, RoundingMode.HALF_UP)
                            .doubleValue();

            return CategoryBreakdownResponse.builder()
                    .category(category)
                    .type(type.name())
                    .totalAmount(totalAmount)
                    .count(count)
                    .percentage(percentage)
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MonthlyTrendResponse> getMonthlyTrend(LocalDate startDate, LocalDate endDate) {
        List<Object[]> rows = recordRepository.getMonthlyTrend(startDate, endDate);

        if (rows.isEmpty()) {
            return List.of();
        }

        // Group rows by year-month, each month can have up to 2 rows (INCOME, EXPENSE)
        Map<String, BigDecimal[]> monthData = new LinkedHashMap<>();
        for (Object[] row : rows) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            RecordType type = (RecordType) row[2];
            BigDecimal sum = (BigDecimal) row[3];

            String key = String.format("%d-%02d", year, month);
            monthData.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

            if (type == RecordType.INCOME) {
                monthData.get(key)[0] = sum;
            } else {
                monthData.get(key)[1] = sum;
            }
        }

        // Build response list with running balance
        List<MonthlyTrendResponse> result = new ArrayList<>();
        BigDecimal runningBalance = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal[]> entry : monthData.entrySet()) {
            BigDecimal income = entry.getValue()[0];
            BigDecimal expense = entry.getValue()[1];
            BigDecimal net = income.subtract(expense);
            runningBalance = runningBalance.add(net);

            result.add(MonthlyTrendResponse.builder()
                    .month(entry.getKey())
                    .totalIncome(income)
                    .totalExpense(expense)
                    .netAmount(net)
                    .runningBalance(runningBalance)
                    .build());
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<RecentActivityResponse> getRecentActivity() {
        List<FinancialRecord> records = recordRepository.findTop10ByDeletedFalseOrderByCreatedAtDesc();

        return records.stream().map(record -> RecentActivityResponse.builder()
                .id(record.getId())
                .amount(record.getAmount())
                .type(record.getType().name())
                .category(record.getCategory())
                .date(record.getDate())
                .createdBy(record.getCreatedBy().getUsername())
                .createdAt(record.getCreatedAt())
                .build()
        ).collect(Collectors.toList());
    }
}
