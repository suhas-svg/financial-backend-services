# Spending Reservation Compile Diagnostics

- Source commit: `6c779bb37346639e90f176b6704ef1c33108ec75`
- Workflow run: https://github.com/suhas-svg/financial-backend-services/actions/runs/30299675764
- account-service compile exit: 1
- transaction-service compile exit: 0

## account-service compiler tail

```text
[ERROR] COMPILATION ERROR : 
[ERROR] /home/runner/work/financial-backend-services/financial-backend-services/account-service/src/main/java/com/suhasan/finance/account_service/service/SpendingLimitService.java:[4,47] cannot find symbol
  symbol:   class NotificationSeverity
  location: package com.suhasan.finance.account_service.dto
[ERROR] /home/runner/work/financial-backend-services/financial-backend-services/account-service/src/main/java/com/suhasan/finance/account_service/service/SpendingLimitService.java:[5,47] cannot find symbol
  symbol:   class NotificationSourceType
  location: package com.suhasan.finance.account_service.dto
[ERROR] /home/runner/work/financial-backend-services/financial-backend-services/account-service/src/main/java/com/suhasan/finance/account_service/service/SpendingLimitService.java:[6,47] cannot find symbol
  symbol:   class NotificationType
  location: package com.suhasan.finance.account_service.dto
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.11.0:compile (default-compile) on project account-service: Compilation failure: Compilation failure: 
[ERROR] /home/runner/work/financial-backend-services/financial-backend-services/account-service/src/main/java/com/suhasan/finance/account_service/service/SpendingLimitService.java:[4,47] cannot find symbol
[ERROR]   symbol:   class NotificationSeverity
[ERROR]   location: package com.suhasan.finance.account_service.dto
[ERROR] /home/runner/work/financial-backend-services/financial-backend-services/account-service/src/main/java/com/suhasan/finance/account_service/service/SpendingLimitService.java:[5,47] cannot find symbol
[ERROR]   symbol:   class NotificationSourceType
[ERROR]   location: package com.suhasan.finance.account_service.dto
[ERROR] /home/runner/work/financial-backend-services/financial-backend-services/account-service/src/main/java/com/suhasan/finance/account_service/service/SpendingLimitService.java:[6,47] cannot find symbol
[ERROR]   symbol:   class NotificationType
[ERROR]   location: package com.suhasan.finance.account_service.dto
[ERROR] -> [Help 1]
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException
```

## transaction-service compiler tail

```text
```
