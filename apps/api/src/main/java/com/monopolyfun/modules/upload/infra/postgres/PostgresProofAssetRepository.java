package com.monopolyfun.modules.upload.infra.postgres;

import com.monopolyfun.modules.upload.domain.ProofAssetEntity;
import com.monopolyfun.modules.upload.domain.ProofAssetStatus;
import com.monopolyfun.modules.upload.infra.ProofAssetRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.monopolyfun.generated.jooq.Tables.PROOF_ASSETS;

@Repository
public class PostgresProofAssetRepository implements ProofAssetRepository {
    private static final Field<String> UPLOADED_BY_ACCOUNT_ID = DSL.field("uploaded_by_account_id", String.class);
    private static final Field<String> PURPOSE = DSL.field("purpose", String.class);
    private static final Field<String> VISIBILITY = DSL.field("visibility", String.class);

    private final DSLContext dsl;

    public PostgresProofAssetRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<ProofAssetEntity> findById(String id) {
        return dsl.selectFrom(PROOF_ASSETS)
                .where(PROOF_ASSETS.ID.eq(id))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<ProofAssetEntity> findByOrderIdAndArtifactRef(String orderId, String artifactRef) {
        return dsl.selectFrom(PROOF_ASSETS)
                .where(PROOF_ASSETS.ORDER_ID.eq(orderId))
                .and(PROOF_ASSETS.ARTIFACT_REF.eq(artifactRef))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public List<ProofAssetEntity> findRecent(int limit) {
        return dsl.selectFrom(PROOF_ASSETS)
                .orderBy(PROOF_ASSETS.CREATED_AT.desc())
                .limit(limit)
                .fetch(this::mapRecord);
    }

    @Override
    public Map<String, Long> countByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        dsl.select(PROOF_ASSETS.STATUS, org.jooq.impl.DSL.count())
                .from(PROOF_ASSETS)
                .groupBy(PROOF_ASSETS.STATUS)
                .fetch(record -> counts.put(String.valueOf(record.value1()).toLowerCase(), record.value2().longValue()));
        return counts;
    }

    @Override
    public List<ProofAssetEntity> findExceptions(int limit) {
        return dsl.selectFrom(PROOF_ASSETS)
                .where(PROOF_ASSETS.STATUS.in(
                        com.monopolyfun.generated.jooq.enums.ProofAssetStatus.failed,
                        com.monopolyfun.generated.jooq.enums.ProofAssetStatus.quarantined,
                        com.monopolyfun.generated.jooq.enums.ProofAssetStatus.cancelled))
                .orderBy(PROOF_ASSETS.CREATED_AT.desc())
                .limit(limit)
                .fetch(this::mapRecord);
    }

    @Override
    public ProofAssetEntity save(ProofAssetEntity asset) {
        dsl.insertInto(PROOF_ASSETS)
                .set(PROOF_ASSETS.ID, asset.id())
                .set(PROOF_ASSETS.ORDER_ID, asset.orderId())
                .set(PROOF_ASSETS.ARTIFACT_REF, asset.artifactRef())
                .set(PROOF_ASSETS.OBJECT_KEY, asset.objectKey())
                .set(PROOF_ASSETS.FILENAME, asset.filename())
                .set(PROOF_ASSETS.CONTENT_TYPE, asset.contentType())
                .set(PROOF_ASSETS.CONTENT_LENGTH_BYTES, asset.contentLengthBytes())
                .set(PROOF_ASSETS.CHECKSUM_SHA256, asset.checksumSha256())
                .set(PROOF_ASSETS.STORAGE_PROVIDER, asset.storageProvider())
                .set(PROOF_ASSETS.BUCKET, asset.bucket())
                .set(PROOF_ASSETS.STATUS, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.ProofAssetStatus.class, asset.status()))
                .set(UPLOADED_BY_ACCOUNT_ID, asset.uploadedByAccountId())
                .set(PURPOSE, asset.purpose())
                .set(VISIBILITY, asset.visibility())
                .set(PROOF_ASSETS.PUBLIC_URL, (String) null)
                .set(PROOF_ASSETS.METADATA, PostgresJson.jsonb(asset.metadata()))
                .set(PROOF_ASSETS.CREATED_AT, PostgresJson.offsetDateTime(asset.createdAt()))
                .set(PROOF_ASSETS.UPDATED_AT, PostgresJson.offsetDateTime(asset.updatedAt()))
                .onConflict(PROOF_ASSETS.ID)
                .doUpdate()
                .set(PROOF_ASSETS.ORDER_ID, asset.orderId())
                .set(PROOF_ASSETS.ARTIFACT_REF, asset.artifactRef())
                .set(PROOF_ASSETS.OBJECT_KEY, asset.objectKey())
                .set(PROOF_ASSETS.FILENAME, asset.filename())
                .set(PROOF_ASSETS.CONTENT_TYPE, asset.contentType())
                .set(PROOF_ASSETS.CONTENT_LENGTH_BYTES, asset.contentLengthBytes())
                .set(PROOF_ASSETS.CHECKSUM_SHA256, asset.checksumSha256())
                .set(PROOF_ASSETS.STORAGE_PROVIDER, asset.storageProvider())
                .set(PROOF_ASSETS.BUCKET, asset.bucket())
                .set(PROOF_ASSETS.STATUS, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.ProofAssetStatus.class, asset.status()))
                .set(UPLOADED_BY_ACCOUNT_ID, asset.uploadedByAccountId())
                .set(PURPOSE, asset.purpose())
                .set(VISIBILITY, asset.visibility())
                .set(PROOF_ASSETS.PUBLIC_URL, (String) null)
                .set(PROOF_ASSETS.METADATA, PostgresJson.jsonb(asset.metadata()))
                .set(PROOF_ASSETS.UPDATED_AT, PostgresJson.offsetDateTime(asset.updatedAt()))
                .execute();
        return asset;
    }

    private ProofAssetEntity mapRecord(Record record) {
        return new ProofAssetEntity(
                record.get(PROOF_ASSETS.ID),
                record.get(PROOF_ASSETS.ORDER_ID),
                record.get(PROOF_ASSETS.ARTIFACT_REF),
                record.get(PROOF_ASSETS.OBJECT_KEY),
                record.get(PROOF_ASSETS.FILENAME),
                record.get(PROOF_ASSETS.CONTENT_TYPE),
                record.get(PROOF_ASSETS.CONTENT_LENGTH_BYTES),
                record.get(PROOF_ASSETS.CHECKSUM_SHA256),
                record.get(PROOF_ASSETS.STORAGE_PROVIDER),
                record.get(PROOF_ASSETS.BUCKET),
                PostgresJson.modelEnum(ProofAssetStatus.class, record.get(PROOF_ASSETS.STATUS)),
                record.get(UPLOADED_BY_ACCOUNT_ID),
                record.get(PURPOSE) == null ? "proof" : record.get(PURPOSE),
                record.get(VISIBILITY) == null ? "participants" : record.get(VISIBILITY),
                PostgresJson.map(record.get(PROOF_ASSETS.METADATA)),
                PostgresJson.instant(record.get(PROOF_ASSETS.CREATED_AT)),
                PostgresJson.instant(record.get(PROOF_ASSETS.UPDATED_AT)));
    }
}
