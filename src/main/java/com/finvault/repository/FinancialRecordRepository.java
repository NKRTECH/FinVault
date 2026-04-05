package com.finvault.repository;

import com.finvault.entity.FinancialRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface FinancialRecordRepository extends JpaRepository<FinancialRecord, Long>, JpaSpecificationExecutor<FinancialRecord> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "createdBy")
    org.springframework.data.domain.Page<FinancialRecord> findAll(org.springframework.data.jpa.domain.Specification<FinancialRecord> spec, org.springframework.data.domain.Pageable pageable);
}
