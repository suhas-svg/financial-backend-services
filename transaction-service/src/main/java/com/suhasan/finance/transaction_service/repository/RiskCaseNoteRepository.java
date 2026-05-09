package com.suhasan.finance.transaction_service.repository;

import com.suhasan.finance.transaction_service.entity.RiskCaseNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RiskCaseNoteRepository extends JpaRepository<RiskCaseNote, String> {
}
