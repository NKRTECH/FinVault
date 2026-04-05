package com.finvault.repository;

import com.finvault.entity.FinancialRecord;
import com.finvault.enums.RecordType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface FinancialRecordRepository extends JpaRepository<FinancialRecord, Long>, JpaSpecificationExecutor<FinancialRecord> {

    @EntityGraph(attributePaths = "createdBy")
    Page<FinancialRecord> findAll(Specification<FinancialRecord> spec, Pageable pageable);

    // ── Dashboard aggregate queries ──────────────────────

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM FinancialRecord r " +
            "WHERE r.type = :type AND r.deleted = false " +
            "AND (:startDate IS NULL OR r.date >= :startDate) " +
            "AND (:endDate IS NULL OR r.date <= :endDate)")
    BigDecimal sumAmountByType(@Param("type") RecordType type,
                               @Param("startDate") LocalDate startDate,
                               @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(r) FROM FinancialRecord r " +
            "WHERE r.type = :type AND r.deleted = false " +
            "AND (:startDate IS NULL OR r.date >= :startDate) " +
            "AND (:endDate IS NULL OR r.date <= :endDate)")
    long countByTypeAndNotDeleted(@Param("type") RecordType type,
                                  @Param("startDate") LocalDate startDate,
                                  @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(r) FROM FinancialRecord r " +
            "WHERE r.deleted = false " +
            "AND (:startDate IS NULL OR r.date >= :startDate) " +
            "AND (:endDate IS NULL OR r.date <= :endDate)")
    long countAllActive(@Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

    @Query("SELECT r.category, r.type, SUM(r.amount), COUNT(r) " +
            "FROM FinancialRecord r WHERE r.deleted = false " +
            "AND (:startDate IS NULL OR r.date >= :startDate) " +
            "AND (:endDate IS NULL OR r.date <= :endDate) " +
            "GROUP BY r.category, r.type ORDER BY SUM(r.amount) DESC")
    List<Object[]> getCategoryBreakdown(@Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    @Query("SELECT YEAR(r.date), MONTH(r.date), r.type, SUM(r.amount) " +
            "FROM FinancialRecord r WHERE r.deleted = false " +
            "AND (:startDate IS NULL OR r.date >= :startDate) " +
            "AND (:endDate IS NULL OR r.date <= :endDate) " +
            "GROUP BY YEAR(r.date), MONTH(r.date), r.type " +
            "ORDER BY YEAR(r.date), MONTH(r.date)")
    List<Object[]> getMonthlyTrend(@Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate);

    @EntityGraph(attributePaths = "createdBy")
    List<FinancialRecord> findTop10ByDeletedFalseOrderByCreatedAtDesc();
}
