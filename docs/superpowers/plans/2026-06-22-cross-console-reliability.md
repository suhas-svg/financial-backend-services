# Cross-Console Reliability Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the confirmed timeout, risk-threshold, metric-registration, and browser E2E reliability defects found during the live customer/admin console audit.

**Architecture:** Keep the existing synchronous service architecture. Increase the bounded account-service timeout, make risk evaluation participate in the transaction that persists the evaluated event, leave transaction-duration ownership with the monitoring aspect, and align Playwright with the separated console navigation and measured Docker latency.

**Tech Stack:** Java 17, Spring Boot, Spring transactions, JUnit 5, Mockito, Micrometer, Docker Compose, React, TypeScript, Playwright.

---

## File structure

- `transaction-service/src/test/java/com/suhasan/finance/transaction_service/config/ApplicationRuntimeDefaultsTest.java`: verifies committed runtime timeout defaults.
- `transaction-service/src/main/resources/application.properties`: defines environment-overridable 30-second client and time-limiter defaults.
- `transaction-service/src/main/java/com/suhasan/finance/transaction_service/client/AccountServiceClient.java`: aligns the direct client fallback default.
- `transaction-service/src/main/java/com/suhasan/finance/transaction_service/client/ResilientAccountServiceClient.java`: aligns the resilient client fallback default.
- `transaction-service/src/main/java/com/suhasan/finance/transaction_service/config/ResilienceConfig.java`: aligns the time-limiter fallback default.
- `docker-compose.codex.yml`: passes the live timeout explicitly to transaction-service.
- `transaction-service/src/test/java/com/suhasan/finance/transaction_service/service/RiskEvaluationServiceTest.java`: locks threshold and transaction propagation behavior.
- `transaction-service/src/main/java/com/suhasan/finance/transaction_service/service/RiskEvaluationService.java`: joins the caller transaction.
- `transaction-service/src/test/java/com/suhasan/finance/transaction_service/service/MetricsServiceTest.java`: prevents reintroduction of an untagged processing timer.
- `transaction-service/src/main/java/com/suhasan/finance/transaction_service/service/MetricsService.java`: removes duplicate ownership of transaction processing duration.
- `frontend/playwright.config.ts`: gives Docker-backed assertions a measured 30-second window.
- `frontend/tests/e2e/admin.spec.ts`: enters the separated Operations Console before admin assertions.

---

### Task 1: Establish the 30-second account-service timeout

**Files:**
- Create: `transaction-service/src/test/java/com/suhasan/finance/transaction_service/config/ApplicationRuntimeDefaultsTest.java`
- Modify: `transaction-service/src/main/resources/application.properties:41,48`
- Modify: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/client/AccountServiceClient.java:29`
- Modify: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/client/ResilientAccountServiceClient.java:52`
- Modify: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/config/ResilienceConfig.java:40`
- Modify: `docker-compose.codex.yml` transaction-service environment

- [ ] **Step 1: Write the failing defaults test**

```java
package com.suhasan.finance.transaction_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationRuntimeDefaultsTest {

    @Test
    void accountServiceTimeoutDefaultsToThirtySeconds() throws Exception {
        Properties properties = PropertiesLoaderUtils.loadAllProperties("application.properties");

        assertThat(properties.getProperty("account-service.timeout"))
                .isEqualTo("${ACCOUNT_SERVICE_TIMEOUT:30000}");
        assertThat(properties.getProperty("account-service.resilience.time-limiter.timeout"))
                .isEqualTo("${ACCOUNT_SERVICE_TIMEOUT:30000}");
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
./mvnw.cmd -q -Dtest=ApplicationRuntimeDefaultsTest test
```

Expected: FAIL because both committed defaults are currently `5000`.

- [ ] **Step 3: Apply the minimal timeout changes**

Change both properties to:

```properties
account-service.timeout=${ACCOUNT_SERVICE_TIMEOUT:30000}
account-service.resilience.time-limiter.timeout=${ACCOUNT_SERVICE_TIMEOUT:30000}
```

Change the three Java `@Value` fallbacks from `:5000` to `:30000`.

Add under the transaction-service Compose environment:

```yaml
ACCOUNT_SERVICE_TIMEOUT: ${ACCOUNT_SERVICE_TIMEOUT:-30000}
```

- [ ] **Step 4: Verify GREEN and rendered Compose configuration**

Run:

```powershell
./mvnw.cmd -q -Dtest=ApplicationRuntimeDefaultsTest,ResilienceConfigTest test
docker compose -f docker-compose.codex.yml config | Select-String ACCOUNT_SERVICE_TIMEOUT
```

Expected: tests pass and Compose renders `ACCOUNT_SERVICE_TIMEOUT: "30000"`.

- [ ] **Step 5: Commit the timeout repair**

```powershell
git add transaction-service/src/test/java/com/suhasan/finance/transaction_service/config/ApplicationRuntimeDefaultsTest.java transaction-service/src/main/resources/application.properties transaction-service/src/main/java/com/suhasan/finance/transaction_service/client/AccountServiceClient.java transaction-service/src/main/java/com/suhasan/finance/transaction_service/client/ResilientAccountServiceClient.java transaction-service/src/main/java/com/suhasan/finance/transaction_service/config/ResilienceConfig.java docker-compose.codex.yml
git commit -m "fix: raise account service timeout"
```

### Task 2: Count the current transaction at risk thresholds

**Files:**
- Modify: `transaction-service/src/test/java/com/suhasan/finance/transaction_service/service/RiskEvaluationServiceTest.java`
- Modify: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/service/RiskEvaluationService.java:34,43,75`

- [ ] **Step 1: Add the failing propagation test**

Add imports for `Propagation`, `Transactional`, and `Method`, then add:

```java
@Test
void riskEvaluationJoinsTheTransactionThatPersistsTheEvent() throws Exception {
    for (String methodName : new String[] {
            "evaluateCompletedTransaction",
            "evaluateFailedTransaction",
            "evaluateReversalTransaction"
    }) {
        Method method = RiskEvaluationService.class.getMethod(methodName, Transaction.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}
```

The existing `rapidCompletedTransfersCreateMediumSeverityAlert` test already locks the exact threshold at five observed transfers.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
./mvnw.cmd -q -Dtest=RiskEvaluationServiceTest#riskEvaluationJoinsTheTransactionThatPersistsTheEvent test
```

Expected: FAIL because all three methods use `REQUIRES_NEW`.

- [ ] **Step 3: Join the caller transaction**

Replace each annotation with the default propagation:

```java
@Transactional
```

Remove the unused `Propagation` import from production code.

- [ ] **Step 4: Verify GREEN and all risk unit behavior**

Run:

```powershell
./mvnw.cmd -q -Dtest=RiskEvaluationServiceTest test
```

Expected: all risk evaluation tests pass, including the exact threshold and deduplication cases.

- [ ] **Step 5: Commit the risk boundary repair**

```powershell
git add transaction-service/src/main/java/com/suhasan/finance/transaction_service/service/RiskEvaluationService.java transaction-service/src/test/java/com/suhasan/finance/transaction_service/service/RiskEvaluationServiceTest.java
git commit -m "fix: evaluate risk in transaction context"
```

### Task 3: Remove the inconsistent processing-duration meter

**Files:**
- Modify: `transaction-service/src/test/java/com/suhasan/finance/transaction_service/service/MetricsServiceTest.java`
- Modify: `transaction-service/src/main/java/com/suhasan/finance/transaction_service/service/MetricsService.java:38,75-77,158`

- [ ] **Step 1: Add the failing meter-ownership test**

```java
@Test
void metricsServiceDoesNotRegisterTheAspectOwnedProcessingTimer() {
    metricsService.recordTransactionInitiated(TransactionType.TRANSFER);
    metricsService.recordTransactionCompleted(
            TransactionType.TRANSFER,
            TransactionStatus.COMPLETED,
            BigDecimal.TEN,
            25L);

    assertTrue(meterRegistry.find("transaction.processing.duration").meters().isEmpty());
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
./mvnw.cmd -q -Dtest=MetricsServiceTest#metricsServiceDoesNotRegisterTheAspectOwnedProcessingTimer test
```

Expected: FAIL because `MetricsService` registers an untagged timer during construction.

- [ ] **Step 3: Leave timer ownership with MonitoringAspect**

Remove:

```java
private final Timer transactionProcessingTimer;
```

Remove its constructor registration and the call that records `processingTimeMs` against it. Keep the method parameter and all counters/gauges unchanged because callers and aggregate metrics still use them.

- [ ] **Step 4: Verify GREEN and monitoring tests**

Run:

```powershell
./mvnw.cmd -q -Dtest=MetricsServiceTest test
```

Expected: all focused metrics tests pass. Monitoring integration is covered again by the complete transaction-service suite and the live log check in Task 6.

- [ ] **Step 5: Commit the metric repair**

```powershell
git add transaction-service/src/main/java/com/suhasan/finance/transaction_service/service/MetricsService.java transaction-service/src/test/java/com/suhasan/finance/transaction_service/service/MetricsServiceTest.java
git commit -m "fix: use one processing timer schema"
```

### Task 4: Align Playwright with the separated consoles and Docker latency

**Files:**
- Modify: `frontend/playwright.config.ts:5-8`
- Modify: `frontend/tests/e2e/admin.spec.ts:15-25`

- [ ] **Step 1: Preserve the observed RED evidence**

Run before changes:

```powershell
npm.cmd run e2e -- tests/e2e/admin.spec.ts tests/e2e/customer.spec.ts --reporter=line
```

Expected: admin fails while looking for obsolete combined-shell navigation; customer can exceed the ten-second registration/account assertion window.

- [ ] **Step 2: Increase only the assertion window**

Change:

```ts
expect: {
  timeout: 30_000
}
```

Keep the overall test timeout bounded at 45 seconds initially. Raise it to 120 seconds only if the full customer journey exceeds 45 seconds after individual requests complete successfully.

- [ ] **Step 3: Enter the Operations Console explicitly**

Immediately after admin sign-in, assert and click the separated-shell link:

```ts
const operationsConsole = page.getByRole("link", { name: "Operations Console" });
await expect(operationsConsole).toBeVisible();
await operationsConsole.click();
await expect(page.getByRole("heading", { name: "Operations overview" })).toBeVisible();
```

Then retain the existing admin navigation assertions.

- [ ] **Step 4: Verify both complete browser journeys**

Run:

```powershell
npm.cmd run e2e -- tests/e2e/admin.spec.ts tests/e2e/customer.spec.ts --reporter=line
```

Expected: all three tests pass: admin workflow, customer admin-route guard, and customer financial journey.

- [ ] **Step 5: Commit the E2E alignment**

```powershell
git add frontend/playwright.config.ts frontend/tests/e2e/admin.spec.ts
git commit -m "test: align e2e with separated consoles"
```

### Task 5: Run full static and automated verification

**Files:** No additional source changes expected.

- [ ] **Step 1: Run the frontend suite and production build**

```powershell
npm.cmd test
npm.cmd run build
```

Expected: 40 frontend tests pass and production build exits zero. Record the existing bundle warning separately.

- [ ] **Step 2: Run the account-service suite**

```powershell
./mvnw.cmd -q test
```

Expected: all account-service tests pass.

- [ ] **Step 3: Run the transaction-service suite**

```powershell
./mvnw.cmd -q test
```

Expected: all enabled transaction-service tests pass; record intentional skips separately.

- [ ] **Step 4: Check the patch and worktree**

```powershell
git diff --check
git status --short
git log --oneline -6
```

Expected: no whitespace errors; only intentional commits are present.

### Task 6: Rebuild and verify the live cross-console system

**Files:** No additional source changes expected unless verification exposes a new root cause.

- [ ] **Step 1: Rebuild affected transaction-service container**

```powershell
docker compose -f docker-compose.codex.yml up -d --build transaction-service
docker compose -f docker-compose.codex.yml ps
```

Expected: account-service, transaction-service, both PostgreSQL containers, and Redis are healthy; transaction-service has `ACCOUNT_SERVICE_TIMEOUT=30000`.

- [ ] **Step 2: Verify service and frontend health**

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:5173
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8080/actuator/health
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8081/actuator/health
```

Expected: HTTP 200 and `UP` service health.

- [ ] **Step 3: Repeat the threshold transaction scenario**

Create a fresh customer with two funded accounts, complete exactly five rapid transfers, and query `/api/risk/alerts` as admin.

Expected: the fifth completed transfer creates one open `RAPID_TRANSFERS` alert; no sixth transfer is required.

- [ ] **Step 4: Verify cross-console propagation**

Confirm that admin can see the customer's accounts and transactions, freeze/unfreeze remains functional, customer debit from a frozen account returns HTTP 400, and notification/dispute/audit/investigation views still load.

- [ ] **Step 5: Verify metric registration remains clean**

```powershell
docker logs financialgithubmain-transaction-service-1 --since 10m 2>&1 | Select-String -Pattern "same name|different tag|registration failed"
```

Expected: no processing-duration meter identity conflict.

- [ ] **Step 6: Run final browser route sweep**

Visit all six customer routes and all nine admin routes with their respective users.

Expected: expected headings render, API-backed content loads, and browser console error/warning logs are empty.

- [ ] **Step 7: Final verification gate**

```powershell
git status --short
git branch --show-current
git rev-parse --short HEAD
docker compose -f docker-compose.codex.yml ps
```

Expected: clean worktree on `codex/github-main-baseline`, all runtime containers healthy, and the final commit recorded for handoff.
