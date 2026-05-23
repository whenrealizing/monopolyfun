package com.monopolyfun.modules.identity.infra;

import com.monopolyfun.modules.identity.domain.OAuthStateEntity;

import java.util.Optional;

public interface OAuthStateRepository {
    Optional<OAuthStateEntity> findByStateToken(String stateToken);

    OAuthStateEntity save(OAuthStateEntity state);
}
