package com.monopolyfun.modules.work.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record WorkReceiptEntity(
        String id,
        String receiptNo,
        String workRunId,
        String summary,
        Map<String, Object> output,
        List<String> evidenceRefs,
        List<String> traceRefs,
        List<String> contentHashes,
        Instant createdAt
) {
    public WorkReceiptEntity {
        output = output == null ? Map.of() : Map.copyOf(output);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        traceRefs = traceRefs == null ? List.of() : List.copyOf(traceRefs);
        contentHashes = contentHashes == null ? List.of() : List.copyOf(contentHashes);
    }
}
