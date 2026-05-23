package com.monopolyfun.modules.upload.service.mapper;

import com.monopolyfun.modules.upload.domain.ProofAssetEntity;
import com.monopolyfun.modules.upload.service.view.ProofAssetView;

public final class UploadViewMapper {
    private UploadViewMapper() {
    }

    public static ProofAssetView proofAsset(ProofAssetEntity asset) {
        if (asset == null) return null;
        return new ProofAssetView(
                asset.id(),
                asset.orderId(),
                asset.artifactRef(),
                asset.objectKey(),
                asset.filename(),
                asset.contentType(),
                asset.contentLengthBytes(),
                asset.checksumSha256(),
                asset.storageProvider(),
                asset.bucket(),
                asset.status(),
                asset.uploadedByAccountId(),
                asset.purpose(),
                asset.visibility(),
                asset.metadata(),
                asset.createdAt(),
                asset.updatedAt());
    }
}
