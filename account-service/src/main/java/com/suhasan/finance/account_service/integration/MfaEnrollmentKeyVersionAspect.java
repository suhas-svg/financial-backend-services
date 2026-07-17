package com.suhasan.finance.account_service.integration;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class MfaEnrollmentKeyVersionAspect {
    private final JdbcTemplate jdbc;
    private final MfaSecretManager secrets;

    @AfterReturning("execution(* com.suhasan.finance.account_service.service.MfaService.enroll(..)) && args(username,..)")
    public void versionNewEnrollment(String username) {
        var rows = jdbc.queryForList("""
                SELECT id,secret_ciphertext,secret_key_id FROM user_mfa_methods
                WHERE user_id=? AND method_type='TOTP'
                """, username);
        if (rows.isEmpty()) return;
        var row = rows.getFirst();
        String currentKeyId = String.valueOf(row.get("secret_key_id"));
        if (secrets.health().activeKeyId().equals(currentKeyId)) return;
        String plaintext = secrets.decrypt(String.valueOf(row.get("secret_ciphertext")), currentKeyId);
        var encrypted = secrets.encrypt(plaintext);
        jdbc.update("UPDATE user_mfa_methods SET secret_ciphertext=?,secret_key_id=? WHERE id=?",
                encrypted.value(), encrypted.keyId(), row.get("id"));
    }
}
