package com.monopolyfun.modules.identity.service.verification;

import com.monopolyfun.modules.identity.domain.IdentityFactEntity;

public record IdentityVerificationCompleteResult(
        IdentityFactEntity fact
) {
}
