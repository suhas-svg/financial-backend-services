# Shared by backup-synthetic-sandbox.ps1 and restore-verify-synthetic-sandbox.ps1: the same
# queries describe a database at backup time and again after restore, so the drill can prove
# that nothing was lost. Values are text so sums and timestamps compare exactly.

$SyntheticDrillSnapshotQueries = [ordered]@{
    account = [ordered]@{
        users                    = "select count(*) from users"
        accounts                 = "select count(*) from accounts"
        accountBalanceTotal      = "select coalesce(sum(balance),0)::text from accounts"
        balanceOperations        = "select count(*) from account_balance_operations"
        flywaySuccessfulMigrations = "select count(*) from flyway_schema_history where success"
    }
    transaction = [ordered]@{
        transactions             = "select count(*) from transactions"
        journals                 = "select count(*) from journal_transactions"
        postings                 = "select count(*) from journal_postings"
        postedDebitTotal         = "select coalesce(sum(amount) filter (where direction='DEBIT'),0)::text from journal_postings"
        latestTransactionAt      = "select coalesce(to_char(max(created_at),'YYYY-MM-DD""T""HH24:MI:SS.US'),'') from transactions"
        flywaySuccessfulMigrations = "select count(*) from flyway_schema_history where success"
    }
}

# Must be zero after restore: every journal balances, in one currency, with two or more postings.
$SyntheticDrillUnbalancedJournalsQuery = @"
select count(*) from (
  select journal.journal_id
    from journal_transactions journal
    left join journal_postings posting on posting.journal_id = journal.journal_id
   group by journal.journal_id
  having count(posting.posting_id) < 2
      or coalesce(sum(posting.amount) filter (where posting.direction='DEBIT'),0)
         <> coalesce(sum(posting.amount) filter (where posting.direction='CREDIT'),0)
      or count(distinct posting.currency) > 1
) invalid
"@

# $SqlRunner is a scriptblock that takes SQL and returns the single value it selects.
# Parameter names are deliberately unusual: PowerShell resolves variables dynamically and
# case-insensitively, so a parameter called $Database would hide the caller's $database
# inside the scriptblock and send every query to the wrong database.
function Get-SyntheticDrillSnapshot([string]$SnapshotLabel, [scriptblock]$SqlRunner) {
    $snapshot = [ordered]@{}
    foreach ($entry in $SyntheticDrillSnapshotQueries[$SnapshotLabel].GetEnumerator()) {
        $snapshot[$entry.Key] = ([string](& $SqlRunner $entry.Value)).Trim()
    }
    return $snapshot
}
