package com.suhasan.finance.transaction_service.repository;

import com.suhasan.finance.transaction_service.entity.TransactionDisputeNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionDisputeNoteRepository extends JpaRepository<TransactionDisputeNote, String> {
}
