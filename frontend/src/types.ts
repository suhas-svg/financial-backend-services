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

export type NotificationType = "TRANSACTION_COMPLETED" | "TRANSACTION_FAILED" | "ACCOUNT_FROZEN" | "ACCOUNT_UNFROZEN" | "DISPUTE_CREATED" | "DISPUTE_STATUS_UPDATED";
export type NotificationSeverity = "INFO" | "SUCCESS" | "WARNING" | "CRITICAL";
export type NotificationStatus = "UNREAD" | "READ";
export type NotificationSourceType = "ACCOUNT" | "TRANSACTION" | "DISPUTE";

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
