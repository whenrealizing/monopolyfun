package com.monopolyfun.modules.repo.api.request;

import jakarta.validation.constraints.Size;

public record CreateRepoDeliverySessionRequest(
        @Size(max = 40) String projectNo,
        @Size(max = 40) String orderNo,
        @Size(max = 40) String runtime
) {
}
