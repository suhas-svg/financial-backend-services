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
export type AccountStatus = "ACTIVE" | "FROZEN";

export type Account = {
  id: number;
  ownerId: string;
  balance: number;
  ledgerBalance?: number;
  availableBalance?: number;
  createdAt: string;
  accountType: AccountType;
  status?: AccountStatus;
  statusReason?: string;
  statusUpdatedAt?: string;
  statusUpdatedBy?: string;
  interestRate?: number;
  creditLimit?: number;
  dueDate?: string;
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

export type NotificationType = "TRANSACTION_COMPLETED" | "TRANSACTION_FAILED" | "ACCOUNT_FROZEN" | "ACCOUNT_UNFROZEN" | "DISPUTE_CREATED" | "DISPUTE_STATUS_UPDATED" | "SCHEDULED_TRANSFER_CREATED" | "SCHEDULED_TRANSFER_PAUSED" | "SCHEDULED_TRANSFER_RESUMED" | "SCHEDULED_TRANSFER_CANCELED" | "SCHEDULED_TRANSFER_RUN_COMPLETED" | "SCHEDULED_TRANSFER_RUN_FAILED";
export type NotificationSeverity = "INFO" | "SUCCESS" | "WARNING" | "CRITICAL";
export type NotificationStatus = "UNREAD" | "READ";
export type NotificationSourceType = "ACCOUNT" | "TRANSACTION" | "DISPUTE" | "SCHEDULED_TRANSFER";

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
  status: ScheduledTransferStatus;
  lastRunAt?: string;
  lastRunStatus?: ScheduledTransferRunStatus;
  lastRunFailureReason?: string;
  lastTransactionId?: string;
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
