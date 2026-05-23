package com.monopolyfun.modules.identity.service.verification;

import java.util.List;

public record PublicProofSpec(
        String certifierId,
        String provider,
        List<String> allowedPlacements,
        IdentityCertifierManifest manifest
) {
}
