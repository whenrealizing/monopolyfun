package com.monopolyfun.modules.identity.api.request;

import java.util.Map;

public record CompleteIdentityVerificationRequest(
        Map<String, Object> input
) {
}
