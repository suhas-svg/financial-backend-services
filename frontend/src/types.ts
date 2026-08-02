export type Role = "ROLE_USER" | "ROLE_ADMIN" | "ROLE_INTERNAL_SERVICE" | string;

export type Page<T> = {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type AccountType = "CHECKING" | "SAVINGS" | "CREDIT";
export type AccountStatus = "ACTIVE" | "FROZEN" | "CLOSED";

export type Account = {
  id: number;
  ownerId: string;
  balance: number;
  ledgerBalance?: number;
  availableBalance?: number;
  createdAt: string;
  accountType: AccountType;
  currency: "USD" | "EUR" | "GBP" | "INR";
  status?: AccountStatus;
  statusReason?: string;
  statusUpdatedAt?: string;
  statusUpdatedBy?: string;
  interestRate?: number;
  creditLimit?: number;
  dueDate?: string;
};

export type BeneficiaryStatus = "ACTIVE" | "DISABLED";

export type Beneficiary = {
  beneficiaryId: string;
  userId: string;
  displayName: string;
  destinationAccountId: string;
  currency: "USD" | "EUR" | "GBP" | "INR";
  nickname?: string;
  notes?: string;
  status: BeneficiaryStatus;
  createdAt: string;
  updatedAt: string;
  disabledAt?: string;
  version?: number;
};

export type LedgerAccountProjection = {
  externalAccountId: string;
  currency: string;
  postedBalance: number;
  pendingBalance: number;
  availableBalance: number;
  projectionVersion: number;
  updatedAt?: string;
};

export type CustomerJournalPosting = {
  externalAccountId: string;
  direction: "DEBIT" | "CREDIT" | string;
  amount: number;
  currency: string;
  memo?: string;
};

export type CustomerJournal = {
  journalId: string;
  journalReference?: string;
  journalType: string;
  state: string;
  currency: string;
  customerAmount: number;
  description?: string;
  postedAt?: string;
  reversalOfJournalId?: string;
  postings: CustomerJournalPosting[];
};

export type NotificationType = "TRANSACTION_COMPLETED" | "TRANSACTION_FAILED" | "ACCOUNT_FROZEN" | "ACCOUNT_UNFROZEN" | "DISPUTE_CREATED" | "DISPUTE_STATUS_UPDATED" | "SCHEDULED_TRANSFER_CREATED" | "SCHEDULED_TRANSFER_PAUSED" | "SCHEDULED_TRANSFER_RESUMED" | "SCHEDULED_TRANSFER_CANCELED" | "SCHEDULED_TRANSFER_EXECUTED" | "SCHEDULED_TRANSFER_FAILED" | "OUTCOME_PROTECTION_AT_RISK" | "BALANCE_SHIELD_CONSENT_PENDING" | "BALANCE_SHIELD_ACTIVATED" | "BALANCE_SHIELD_SUSPENDED" | "BALANCE_SHIELD_RESUMED" | "BALANCE_SHIELD_REVOKED" | "BALANCE_SHIELD_ACTION_REQUIRES_MFA" | "BALANCE_SHIELD_ACTION_COMPLETED" | "BALANCE_SHIELD_ACTION_FAILED" | "SECURITY_ACTION_REQUIRED" | "SECURITY_ALERT" | "TRANSFER_AUTHORIZED";
export type NotificationSeverity = "INFO" | "SUCCESS" | "WARNING" | "CRITICAL";
export type NotificationStatus = "UNREAD" | "READ";
export type NotificationSourceType = "ACCOUNT" | "TRANSACTION" | "DISPUTE" | "SCHEDULED_TRANSFER" | "OUTCOME_PROTECTION";

export type Notification = {
  notificationId: number;
  userId: string;
  type: NotificationType;
  severity: NotificationSeverity;
  status: NotificationStatus;
  title: string;
  message: string;
  sourceType: NotificationSourceType;
  sourceId: string;
  dedupeKey: string;
  createdAt: string;
  readAt?: string;
};

export type NotificationSummary = {
  total: number;
  unread: number;
  bySeverity: Partial<Record<NotificationSeverity, number>>;
  byType: Partial<Record<NotificationType, number>>;
  bySourceType: Partial<Record<NotificationSourceType, number>>;
};

export type TransactionType = "DEPOSIT" | "WITHDRAWAL" | "TRANSFER" | "REVERSAL" | string;
export type TransactionStatus = "COMPLETED" | "PENDING" | "FAILED" | "REVERSED" | string;
export type DisputeStatus = "OPEN" | "IN_REVIEW" | "APPROVED" | "DENIED" | "CLOSED";
export type DisputeReasonCode = "UNAUTHORIZED" | "DUPLICATE" | "INCORRECT_AMOUNT" | "SERVICE_NOT_RECEIVED" | "OTHER";

export type Transaction = {
  transactionId: string;
  journalId?: string;
  fromAccountId?: string;
  toAccountId?: string;
  amount: number;
  currency: string;
  type: TransactionType;
  status: TransactionStatus;
  description?: string;
  reference?: string;
  createdAt: string;
  processedAt?: string;
  createdBy?: string;
  idempotencyKey?: string;
  processingState?: string;
  originalTransactionId?: string;
  reversalTransactionId?: string;
  reversedAt?: string;
  reversedBy?: string;
  reversalReason?: string;
  authorizationRequired?: boolean;
  authorizationChallengeId?: string;
  authorizationExpiresAt?: string;
  authorizationReasons?: string[];
};

export type MfaStatus = {
  enrolled: boolean;
  status: string;
  recoveryCodesRemaining: number;
};

export type MfaEnrollment = { secret: string; otpauthUri: string };
export type MfaConfirmation = { active: boolean; recoveryCodes: string[] };
export type ChallengeVerification = { challengeId: string; proof: string; proofExpiresAt: string };
export type SpendingLimit = {
  accountId: number;
  currency: string;
  transferDailyLimit: number;
  withdrawalDailyLimit: number;
  transferUsedToday: number;
  withdrawalUsedToday: number;
  pendingTransferDailyLimit?: number;
  pendingWithdrawalDailyLimit?: number;
  pendingEffectiveAt?: string;
};

export type TransactionStats = {
  accountId?: string;
  periodStart?: string;
  periodEnd?: string;
  totalTransactions?: number;
  completedTransactions?: number;
  pendingTransactions?: number;
  failedTransactions?: number;
  reversedTransactions?: number;
  totalAmount?: number;
  totalIncoming?: number;
  totalOutgoing?: number;
  totalDeposits?: number;
  totalWithdrawals?: number;
  totalTransfers?: number;
  averageTransactionAmount?: number;
  largestTransaction?: number;
  smallestTransaction?: number;
  transactionCountsByType?: Record<string, number>;
  transactionAmountsByType?: Record<string, number>;
  dailyTotal?: number;
  monthlyTotal?: number;
  dailyCount?: number;
  monthlyCount?: number;
  successRate?: number;
  currency?: string;
};

export type Limits = {
  dailyLimit: number;
  monthlyLimit: number;
  singleTransactionLimit: number;
  currency: string;
};

export type ScheduledTransferStatus = "ACTIVE" | "PAUSED" | "CANCELED" | "COMPLETED";
export type ScheduledTransferType = "ONE_TIME" | "RECURRING";
export type ScheduledTransferFrequency = "WEEKLY" | "BIWEEKLY" | "MONTHLY";
export type ScheduledTransferRunStatus = "PROCESSING" | "COMPLETED" | "FAILED" | "SKIPPED";

export type ScheduledTransfer = {
  scheduleId: string;
  userId: string;
  fromAccountId: string;
  toAccountId: string;
  amount: number;
  currency: string;
  description?: string;
  reference?: string;
  scheduleType: ScheduledTransferType;
  frequency?: ScheduledTransferFrequency;
  nextRunAt: string;
  endAt?: string;
  timeZone: string;
  sourceLocalDateTime: string;
  nextRunLocalDateTime: string;
  dstOverlapPolicy: "EARLIER" | "LATER";
  dstGapPolicy: "SHIFT_FORWARD" | "REJECT";
  status: ScheduledTransferStatus;
  lastRunAt?: string;
  lastRunStatus?: ScheduledTransferRunStatus;
  lastRunFailureReason?: string;
  lastTransactionId?: string;
  version: number;
};

export type ScheduledTransferRun = {
  runId: string;
  scheduleId: string;
  scheduledFor: string;
  startedAt: string;
  completedAt?: string;
  status: ScheduledTransferRunStatus;
  transactionId?: string;
  idempotencyKey: string;
  failureReason?: string;
};

export type OutcomeAssumptionType = "INCOME" | "EXPENSE" | "OTHER";
export type OutcomeShockType = "INCOME_DELAY" | "INCOME_REDUCTION" | "EXPENSE_SPIKE" | "PAYMENT_TIMING_SHIFT";

export type OutcomeAssumption = {
  id: string;
  date: string;
  amount: number;
  type: OutcomeAssumptionType;
  label: string;
  flexible: boolean;
  critical: boolean;
};

export type OutcomeShock = {
  id: string;
  type: OutcomeShockType;
  targetAssumptionId: string;
  days?: number;
  amount?: number;
  percentage?: number;
  label: string;
};

export type OutcomeScenarioRequest = {
  name: string;
  accountIds: string[];
  currency: string;
  timeZone: string;
  horizonStart: string;
  horizonDays: number;
  protectedMinimum: number;
  assumptions: OutcomeAssumption[];
  shocks: OutcomeShock[];
  outcomeType?: "BALANCE_FLOOR" | "SCHEDULED_OBLIGATION";
  protectedScheduleId?: string;
  protectedScheduleVersion?: number;
};

export type OutcomeTimelineEvent = {
  eventId: string;
  date: string;
  amount: number;
  source: "ASSUMPTION" | "SCHEDULED_TRANSFER" | string;
  label: string;
  flexible: boolean;
  critical: boolean;
  scheduleId?: string;
  protectedObligation?: boolean;
};

export type OutcomeTimelineDay = {
  date: string;
  openingBalance: number;
  events: OutcomeTimelineEvent[];
  closingBalance: number;
};

export type OutcomeForecast = {
  safe: boolean;
  startingBalance: number;
  protectedMinimum: number;
  failureDate?: string;
  lowestBalance: number;
  closingBalance: number;
  triggeringEvents: OutcomeTimelineEvent[];
  timeline: OutcomeTimelineDay[];
  balanceFloorSatisfied: boolean;
  protectedObligationSatisfied: boolean;
  invariantBreaches: Array<{
    type: "BALANCE_FLOOR_BREACH" | "PROTECTED_OBLIGATION_INSUFFICIENT_FUNDS" | "PROTECTED_OBLIGATION_MISSING_OR_CHANGED" | string;
    date: string;
    eventId?: string;
    scheduleId?: string;
    balanceBefore?: number;
    requiredAmount?: number;
    shortfall?: number;
    explanation: string;
  }>;
};

export type OutcomeRepairAction = {
  actionId: string;
  type: "RESERVE_BUFFER" | "SHIFT_OPTIONAL_SCHEDULE" | "REDUCE_OPTIONAL_SCHEDULE" | "TEMPORARY_SPENDING_LIMIT" | "REVIEW_FLEXIBLE_EXPENSES" | string;
  amount: number;
  affectedEventIds: string[];
  explanation: string;
  targetScheduleId?: string;
  effectiveDate?: string;
  originalAmount?: number;
  disruptionScore: number;
  previewOnly: boolean;
};

export type OutcomeRepairAlternative = {
  rank: number;
  alternativeId: string;
  actions: OutcomeRepairAction[];
  replay: OutcomeForecast;
  rankingFactors: {
    restoresAllInvariants: boolean;
    actionCount: number;
    disruptionScore: number;
    moneyMovedOrDeferred: number;
    stableActionIds: string[];
  };
  certificateHash: string;
  explanation: string;
};
export type OutcomeSimulation = {
  baseline: OutcomeForecast;
  reverseStress: {
    failureFound: boolean;
    baselineFailure: boolean;
    minimalShockCount?: number;
    appliedShocks: Array<{ shockId: string; type: OutcomeShockType; label: string; targetAssumptionId: string; severityScore: number }>;
    failureDate?: string;
    lowestBalance?: number;
    triggeringEvents: OutcomeTimelineEvent[];
    timeline: OutcomeTimelineDay[];
    minimalityExplanation: string;
  };
  repair: {
    maximumShortfall: number;
    selectedRepairs: OutcomeRepairAction[];
    verifiedInModel: boolean;
    minimalityExplanation: string;
    alternatives: OutcomeRepairAlternative[];
    rejectedCandidates: Array<{ candidateId: string; type: string; targetId?: string; reasonCode: string; explanation: string }>;
    evaluatedCombinations: number;
    searchCapped: boolean;
    maxCombinationSize: number;
    maxEvaluatedCombinations: number;
    engineVersion: string;
    certificateHash: string;
  };
  evaluatedCombinations: number;
  searchCapped: boolean;
};

export type OutcomeNotificationDelivery = {
  deliveryId: string;
  state: "PENDING" | "RETRY_SCHEDULED" | "DELIVERED" | "TERMINAL_FAILED";
  attemptCount: number;
  nextAttemptAt?: string;
  deliveredAt?: string;
  terminalAt?: string;
  slaEscalatedAt?: string;
  lastError?: string;
  dedupeKey: string;
};

export type OutcomeGuardrailControl = {
  executionEnabled: boolean;
  reason: string;
  changedBy: string;
  updatedAt: string;
};

export type OutcomeGuardrailOperatorPolicy = {
  userId: string;
  scenarioId: string;
  policy: OutcomeGuardrailPolicy;
};

export type OutcomeGuardrailControlEvent = {
  eventId: string;
  executionEnabled: boolean;
  reason: string;
  actor: string;
  createdAt: string;
};

export type OutcomeGuardrailTerms = {
  version: string;
  hash: string;
  title: string;
  summary: string;
  confirmations: string[];
  backgroundExecution: false;
};

export type OutcomeGuardrailPolicy = {
  policyId: string;
  guardrailId: string;
  fundingAccountId: string;
  protectedAccountId: string;
  currency: string;
  triggerThreshold: number;
  maxActionAmount: number;
  totalLimit: number;
  totalExecuted: number;
  totalReserved: number;
  maxExecutions: number;
  executionCount: number;
  termsVersion: string;
  termsHash: string;
  status: "CONSENT_PENDING" | "ACTIVE" | "SUSPENDED" | "REVOKED" | "EXPIRED";
  effectiveStatus: "CONSENT_PENDING" | "ACTIVE" | "SUSPENDED" | "REVOKED" | "EXPIRED";
  expiresAt: string;
  consentedAt: string;
  activatedAt?: string;
  suspendedAt?: string;
  suspensionReason?: string;
  revokedAt?: string;
  revocationReason?: string;
  activationChallengeId: string;
  activationChallengeExpiresAt: string;
  executionEnabled: boolean;
  executionControlReason: string;
  requiresReconsent: boolean;
  notificationDelivery?: OutcomeNotificationDelivery;
};

export type OutcomeGuardrailExecution = {
  executionId: string;
  guardrailId: string;
  policyId: string;
  amount: number;
  currency: string;
  status: "REQUESTED" | "AWAITING_AUTHORIZATION" | "COMPLETED" | "CANCELLED" | "FAILED";
  transactionId?: string;
  authorizationRequired: boolean;
  authorizationChallengeId?: string;
  authorizationExpiresAt?: string;
  lastError?: string;
  createdAt: string;
  completedAt?: string;
  notificationDelivery?: OutcomeNotificationDelivery;
};

export type OutcomeGuardrail = {
  guardrailId: string;
  type: string;
  thresholdAmount: number;
  currency: string;
  accountIds: string[];
  expiresAt: string;
  status: "DRAFT" | "ACCEPTED" | "EXPIRED";
  previewText: string;
  acceptedAt?: string;
  policy?: OutcomeGuardrailPolicy;
  alternativeRank?: number;
  candidateActions: OutcomeRepairAction[];
  replayCertificateHash?: string;
  rankingFactors?: OutcomeRepairAlternative["rankingFactors"];
  previewSelectedAt?: string;
};

export type OutcomeScenarioSummary = {
  scenarioId: string;
  name: string;
  version: number;
  status: string;
  currency: string;
  horizonStart: string;
  horizonDays: number;
  protectedMinimum: number;
  baselineSafe: boolean;
  updatedAt: string;
  outcomeType?: "BALANCE_FLOOR" | "SCHEDULED_OBLIGATION";
  protectedScheduleId?: string;
};

export type OutcomeFxQuote = {
  quoteCurrency: string;
  baseCurrency: string;
  rate: number;
  asOf: string;
  provider: string;
  provenance: string;
  stale: boolean;
  stalenessPolicy: "REJECT" | "WARN";
};
export type OutcomeScenario = OutcomeScenarioSummary & {
  timeZone: string;
  accountIds: string[];
  assumptions: OutcomeAssumption[];
  shocks: OutcomeShock[];
  sourceSnapshot: {
    startingAvailableBalance: number;
    baseCurrency: string;
    ledgerAccounts: Array<{ accountId: string; currency: string; availableBalance: number; projectionVersion: number; capturedAt: string; baseAvailableBalance: number; baseCurrency: string; fxQuote?: OutcomeFxQuote }>;
    scheduledCashflows: Array<{ eventId: string; scheduleId: string; scheduledFor: string; date: string; amount: number; currency: string; status: string; cadence: string; evaluationTimeZone: string; label: string; fromAccountId: string; toAccountId: string; sourceAmount: number; sourceCurrency: string; fxQuote?: OutcomeFxQuote; scheduleVersion: number; scheduleOwnerId: string; sourceTimeZone: string; dueLocalDate: string; sourceOwnedByCustomer: boolean; destinationOwnedByCustomer: boolean; repairEligible: boolean; repairIneligibilityReason?: string }>;
    protectedObligation?: { scheduleId: string; scheduleVersion: number; status: string; ownerId: string; fromAccountId: string; toAccountId: string; sourceOwnedByCustomer: boolean; destinationOwnedByCustomer: boolean; amount: number; currency: string; scheduleType: string; cadence: string; dueAt: string; dueLocalDate: string; sourceTimeZone: string; evaluationTimeZone: string; endAt?: string; sourceProjectionVersion: number; capturedAt: string; valid: boolean; invalidReason?: string };
    fxQuotes: OutcomeFxQuote[];
    executableFx: false;
    sourceFingerprint: string;
  };
  simulation: OutcomeSimulation;
  guardrails: OutcomeGuardrail[];
  createdAt: string;
  outcomeType: "BALANCE_FLOOR" | "SCHEDULED_OBLIGATION";
  protectedScheduleId?: string;
  protectedScheduleVersion?: number;
};

export type OutcomeDivergence = {
  scenarioId: string;
  previousSourceFingerprint: string;
  currentSourceFingerprint: string;
  diverged: boolean;
  protectionAtRisk: boolean;
  notificationEmitted: boolean;
  freshSimulation: OutcomeSimulation;
  freshSourceSnapshot?: OutcomeScenario["sourceSnapshot"];
  checkedAt: string;
  evaluationEventId: string;
  warningEventId?: string;
  warningAcknowledged: boolean;
  notificationDelivery?: { deliveryId: string; state: "PENDING" | "RETRY_SCHEDULED" | "DELIVERED" | "TERMINAL_FAILED"; attemptCount: number; nextAttemptAt: string; deliveredAt?: string; terminalAt?: string; slaEscalatedAt?: string; lastError?: string; dedupeKey: string };
};

export type AuditLogEntry = {
  eventId: string;
  eventType: string;
  action: string;
  outcome: string;
  userId?: string;
  transactionId?: string;
  fromAccountId?: string;
  toAccountId?: string;
  amount?: number;
  currency?: string;
  ipAddress?: string;
  details?: string;
  errorCode?: string;
  errorMessage?: string;
  createdAt: string;
  metadata?: string;
};

export type AuditSummary = {
  totalEvents: number;
  failureEvents: number;
  reversalEvents: number;
  securityEvents: number;
};

export type TransactionDisputeNote = {
  noteId: string;
  author: string;
  note: string;
  createdAt: string;
};

export type TransactionDispute = {
  disputeId: string;
  disputeNumber: string;
  transactionId: string;
  userId: string;
  status: DisputeStatus;
  reasonCode: DisputeReasonCode;
  description: string;
  assignedTo?: string;
  createdBy: string;
  createdAt: string;
  updatedAt?: string;
  claimedAt?: string;
  closedAt?: string;
  resolutionNote?: string;
  notes?: TransactionDisputeNote[];
};

export type DisputeSummary = {
  totalDisputes: number;
  openDisputes: number;
  inReviewDisputes: number;
  approvedDisputes: number;
  deniedDisputes: number;
  closedDisputes: number;
  unassignedDisputes: number;
};

export type RiskAlertStatus = "OPEN" | "REVIEWED" | "DISMISSED" | "ESCALATED";
export type RiskAlertSeverity = "MEDIUM" | "HIGH";
export type RiskAlertType = "HIGH_VALUE_TRANSFER" | "REPEATED_FAILURES" | "RAPID_TRANSFERS" | "REVERSAL_HEAVY_ACTIVITY";

export type RiskAlert = {
  alertId: string;
  alertType: RiskAlertType;
  severity: RiskAlertSeverity;
  status: RiskAlertStatus;
  userId?: string;
  transactionId?: string;
  fromAccountId?: string;
  toAccountId?: string;
  amount?: number;
  currency?: string;
  reason: string;
  recommendation?: string;
  dedupeKey?: string;
  metadata?: string;
  createdAt: string;
  updatedAt?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  resolutionNote?: string;
};

export type RiskSummary = {
  totalAlerts: number;
  openAlerts: number;
  highSeverityAlerts: number;
  escalatedAlerts: number;
};

export type RiskCaseStatus = "OPEN" | "IN_REVIEW" | "RESOLVED" | "CLOSED";
export type RiskCasePriority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export type RiskCaseNote = {
  noteId: string;
  author: string;
  note: string;
  createdAt: string;
};

export type RiskCase = {
  caseId: string;
  caseNumber: string;
  status: RiskCaseStatus;
  priority: RiskCasePriority;
  title: string;
  userId?: string;
  transactionId?: string;
  primaryAlertId?: string;
  assignedTo?: string;
  createdBy: string;
  createdAt: string;
  updatedAt?: string;
  claimedAt?: string;
  closedAt?: string;
  resolutionNote?: string;
  linkedAlerts?: RiskAlert[];
  notes?: RiskCaseNote[];
};

export type RiskCaseSummary = {
  totalCases: number;
  openCases: number;
  inReviewCases: number;
  resolvedCases: number;
  closedCases: number;
  unassignedCases: number;
};

export type InvestigationItemType = "TRANSACTION" | "AUDIT_EVENT" | "RISK_ALERT" | "RISK_CASE" | "CASE_NOTE" | "DISPUTE" | "DISPUTE_NOTE";

export type InvestigationTimelineItem = {
  itemId: string;
  itemType: InvestigationItemType;
  title: string;
  description?: string;
  severity?: string;
  status?: string;
  userId?: string;
  transactionId?: string;
  accountId?: string;
  alertId?: string;
  caseId?: string;
  amount?: string;
  currency?: string;
  createdAt: string;
  metadata?: Record<string, unknown>;
};

export type InvestigationSummary = {
  transactions: number;
  auditEvents: number;
  riskAlerts: number;
  riskCases: number;
  disputes?: number;
  disputeNotes?: number;
  failures: number;
  reversals: number;
  highSeverityItems: number;
};

export type ReconciliationRunStatus = "RUNNING" | "COMPLETED" | "COMPLETED_WITH_EXCEPTIONS" | "FAILED";
export type ReconciliationExceptionStatus = "OPEN" | "ACKNOWLEDGED" | "IN_PROGRESS" | "RESOLVED" | "WAIVED";
export type ReconciliationSeverity = "INFO" | "WARNING" | "HIGH" | "CRITICAL";

export type ReconciliationRun = {
  runId: string;
  type?: string;
  reconciliationType?: string;
  businessDate: string;
  status: ReconciliationRunStatus | string;
  startedAt: string;
  completedAt?: string;
  requestedBy?: string;
  totalExceptions: number;
  criticalExceptions: number;
};

export type ReconciliationException = {
  exceptionId: string;
  runId?: string;
  checkCode: string;
  severity: ReconciliationSeverity | string;
  status: ReconciliationExceptionStatus | string;
  fingerprint: string;
  title?: string;
  summary?: string;
  description?: string;
  journalId?: string;
  ledgerAccountId?: string;
  externalAccountId?: string;
  currency?: string;
  expectedAmount?: number;
  actualAmount?: number;
  deltaAmount?: number;
  detectedAt: string;
  resolvedBy?: string;
  resolvedAt?: string;
  resolutionNote?: string;
  assignedTo?: string;
  notes?: ReconciliationExceptionNote[];
  version: number;
};

export type ReconciliationExceptionNote = {
  noteId: string;
  author: string;
  note: string;
};

export type CustomerStatementLine = {
  lineId: string;
  journalId: string;
  lineSequence: number;
  effectiveDate: string;
  description?: string;
  amount: number;
  runningBalance: number;
  currency: string;
};

export type CustomerStatement = {
  statementId: string;
  externalAccountId: string;
  currency: string;
  periodStart: string;
  periodEnd: string;
  statementVersion: number;
  openingBalance: number;
  closingBalance: number;
  generatedAt: string;
  lines: CustomerStatementLine[];
};
