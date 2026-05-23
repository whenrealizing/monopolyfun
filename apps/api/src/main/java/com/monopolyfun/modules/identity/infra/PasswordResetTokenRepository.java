package com.monopolyfun.modules.identity.infra;

import com.monopolyfun.modules.identity.domain.PasswordResetTokenEntity;

import java.util.Optional;

public interface PasswordResetTokenRepository {
    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    PasswordResetTokenEntity save(PasswordResetTokenEntity token);
}
