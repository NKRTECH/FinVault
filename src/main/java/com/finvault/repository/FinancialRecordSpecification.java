package com.finvault.repository;

import com.finvault.entity.FinancialRecord;
import com.finvault.enums.RecordType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class FinancialRecordSpecification {

    private FinancialRecordSpecification() {
        // utility class — no instantiation
    }

    public static Specification<FinancialRecord> notDeleted() {
        return (root, query, cb) -> cb.equal(root.get("deleted"), false);
    }

    public static Specification<FinancialRecord> hasType(RecordType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<FinancialRecord> hasCategory(String category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<FinancialRecord> dateOnOrAfter(LocalDate startDate) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), startDate);
    }

    public static Specification<FinancialRecord> dateOnOrBefore(LocalDate endDate) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), endDate);
    }

    public static Specification<FinancialRecord> amountGreaterThanOrEqual(BigDecimal minAmount) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), minAmount);
    }

    public static Specification<FinancialRecord> amountLessThanOrEqual(BigDecimal maxAmount) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("amount"), maxAmount);
    }
}
