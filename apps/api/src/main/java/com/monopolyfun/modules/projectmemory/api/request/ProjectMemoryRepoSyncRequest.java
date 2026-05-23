package com.monopolyfun.modules.projectmemory.api.request;

import jakarta.validation.constraints.Size;

import java.util.Map;

public record ProjectMemoryRepoSyncRequest(
        @Size(max = 120) String repoBindingId,
        @Size(max = 80) String commitSha,
        @Size(max = 120) String rootHash,
        @Size(max = 500) String latestPath,
        Map<String, Object> rawRoot
) {
}
