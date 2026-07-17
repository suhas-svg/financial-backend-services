package com.suhasan.finance.transaction_service.integration;

import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.GuardrailConsentRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Aspect
@Component
@RequiredArgsConstructor
public class ConsentGovernanceEnforcementAspect {
    private final JdbcTemplate jdbc;
    private final Environment environment;

    @Value("${integration.consent.approved-version:2026-07-16.1}") private String approvedVersion;
    @Value("${integration.consent.jurisdictions:*}") private String eligibleJurisdictions;
    @Value("${integration.consent.default-jurisdiction:*}") private String defaultJurisdiction;

    @Before("execution(* com.suhasan.finance.transaction_service.outcome.service.OutcomeGuardrailService.consent(..))"
            + " && args(guardrailId,request,userId,idempotencyKey)")
    public void requireGovernedVersion(String guardrailId, GuardrailConsentRequest request,
                                       String userId, String idempotencyKey) {
        if (request == null || !approvedVersion.equals(request.termsVersion())) {
            throw new IllegalStateException("The configured consent version is not eligible for new consent");
        }
        Set<String> eligible = Arrays.stream(eligibleJurisdictions.split(","))
                .map(String::trim).filter(v -> !v.isBlank()).collect(Collectors.toSet());
        if (!eligible.contains("*") && !eligible.contains(defaultJurisdiction)) {
            throw new IllegalStateException("Balance Shield is not eligible in the configured jurisdiction");
        }
        var versions = jdbc.queryForList("""
                SELECT lifecycle_status,effective_from,retired_at FROM outcome_consent_versions
                WHERE version_id=?
                """, approvedVersion);
        if (versions.size() != 1) throw new IllegalStateException("Approved consent version evidence is missing");
        var version = versions.getFirst();
        String status = String.valueOf(version.get("lifecycle_status"));
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("production");
        if (!(status.equals("APPROVED") || (!production && status.equals("NON_PRODUCTION_APPROVED")))) {
            throw new IllegalStateException("Consent version is not approved for this runtime profile");
        }
        Object effective = version.get("effective_from");
        Object retired = version.get("retired_at");
        if (effective == null || retired != null) {
            throw new IllegalStateException("Consent version is not currently effective");
        }
    }

    @Before("execution(* com.suhasan.finance.transaction_service.outcome.service.OutcomeGuardrailService.execute(..))"
            + " && args(guardrailId,request,userId,idempotencyKey)")
    public void requireNotWithdrawn(String guardrailId, Object request, String userId, String idempotencyKey) {
        Integer withdrawn = jdbc.queryForObject("""
                SELECT COUNT(*) FROM outcome_consent_governance_events
                WHERE user_id=? AND event_type='WITHDRAWN' AND (policy_id IS NULL OR policy_id=?)
                """, Integer.class, userId, guardrailId);
        if (withdrawn != null && withdrawn > 0) {
            throw new IllegalStateException("Consent was withdrawn; a new governed consent is required");
        }
    }
}
