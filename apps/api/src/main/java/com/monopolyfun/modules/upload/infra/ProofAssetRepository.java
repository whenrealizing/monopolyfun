package com.monopolyfun.modules.upload.infra;

import com.monopolyfun.modules.upload.domain.ProofAssetEntity;

import java.util.List;
import java.util.Optional;

public interface ProofAssetRepository {
    Optional<ProofAssetEntity> findById(String id);

    Optional<ProofAssetEntity> findByOrderIdAndArtifactRef(String orderId, String artifactRef);

    List<ProofAssetEntity> findRecent(int limit);

    java.util.Map<String, Long> countByStatus();

    List<ProofAssetEntity> findExceptions(int limit);

    ProofAssetEntity save(ProofAssetEntity asset);
}
