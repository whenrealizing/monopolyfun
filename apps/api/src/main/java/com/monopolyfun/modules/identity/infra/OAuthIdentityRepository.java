package com.monopolyfun.modules.identity.infra;

import com.monopolyfun.modules.identity.domain.OAuthIdentityEntity;

import java.util.Optional;

public interface OAuthIdentityRepository {
    Optional<OAuthIdentityEntity> findByProviderAndExternalUserId(String provider, String externalUserId);

    OAuthIdentityEntity save(OAuthIdentityEntity identity);
}
