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

export type Account = {
  id: number;
  ownerId: string;
  balance: number;
  createdAt: string;
  accountType: AccountType;
  interestRate?: number;
  creditLimit?: number;
  dueDate?: string;
};

export type TransactionType = "DEPOSIT" | "WITHDRAWAL" | "TRANSFER" | "REVERSAL" | string;
export type TransactionStatus = "COMPLETED" | "PENDING" | "FAILED" | "REVERSED" | string;

export type Transaction = {
  transactionId: string;
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
