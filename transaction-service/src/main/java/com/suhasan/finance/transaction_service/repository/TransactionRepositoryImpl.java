package com.suhasan.finance.transaction_service.repository;

import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
class TransactionRepositoryImpl implements TransactionRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Transaction> findTransactionsWithFilters(String accountId,
                                                         TransactionType type,
                                                         TransactionStatus status,
                                                         LocalDateTime startDate,
                                                         LocalDateTime endDate,
                                                         BigDecimal minAmount,
                                                         BigDecimal maxAmount,
                                                         String descriptionPattern,
                                                         String reference,
                                                         String createdBy,
                                                         Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

        CriteriaQuery<Transaction> queryDefinition = criteriaBuilder.createQuery(Transaction.class);
        Root<Transaction> transactionRoot = queryDefinition.from(Transaction.class);
        queryDefinition.select(transactionRoot)
                .where(buildPredicates(criteriaBuilder, transactionRoot, accountId, type, status, startDate, endDate,
                        minAmount, maxAmount, descriptionPattern, reference, createdBy).toArray(Predicate[]::new))
                .orderBy(criteriaBuilder.desc(transactionRoot.get("createdAt")));

        TypedQuery<Transaction> query = entityManager.createQuery(queryDefinition);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        CriteriaQuery<Long> countDefinition = criteriaBuilder.createQuery(Long.class);
        Root<Transaction> countRoot = countDefinition.from(Transaction.class);
        countDefinition.select(criteriaBuilder.count(countRoot))
                .where(buildPredicates(criteriaBuilder, countRoot, accountId, type, status, startDate, endDate,
                        minAmount, maxAmount, descriptionPattern, reference, createdBy).toArray(Predicate[]::new));

        List<Transaction> content = query.getResultList();
        Long total = entityManager.createQuery(countDefinition).getSingleResult();
        return new PageImpl<>(content, pageable, total);
    }

    private List<Predicate> buildPredicates(CriteriaBuilder criteriaBuilder,
                                            Root<Transaction> root,
                                            String accountId,
                                            TransactionType type,
                                            TransactionStatus status,
                                            LocalDateTime startDate,
                                            LocalDateTime endDate,
                                            BigDecimal minAmount,
                                            BigDecimal maxAmount,
                                            String descriptionPattern,
                                            String reference,
                                            String createdBy) {
        List<Predicate> predicates = new ArrayList<>();

        if (hasText(accountId)) {
            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.equal(root.get("fromAccountId"), accountId),
                    criteriaBuilder.equal(root.get("toAccountId"), accountId)));
        }
        if (type != null) {
            predicates.add(criteriaBuilder.equal(root.get("type"), type));
        }
        if (status != null) {
            predicates.add(criteriaBuilder.equal(root.get("status"), status));
        }
        if (startDate != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
        }
        if (endDate != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
        }
        if (minAmount != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("amount"), minAmount));
        }
        if (maxAmount != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("amount"), maxAmount));
        }
        if (hasText(descriptionPattern)) {
            Expression<String> description = criteriaBuilder.lower(criteriaBuilder.coalesce(root.get("description"), ""));
            predicates.add(criteriaBuilder.like(description, descriptionPattern));
        }
        if (hasText(reference)) {
            predicates.add(criteriaBuilder.equal(root.get("reference"), reference));
        }
        if (hasText(createdBy)) {
            predicates.add(criteriaBuilder.equal(root.get("createdBy"), createdBy));
        }

        return predicates;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
