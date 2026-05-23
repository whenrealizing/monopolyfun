package com.monopolyfun.modules.projectmemory.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record ProjectMemorySourceRequest(
        @Size(max = 80) String sourceId,
        @NotBlank @Size(max = 40) String kind,
        @NotBlank @Size(max = 500) String path,
        @Size(max = 120) String sha256,
        @Size(max = 40) String visibility,
        @Size(max = 40) String provider,
        @Size(max = 500) String externalUrl,
        @Size(max = 120) String externalFileId,
        @Size(max = 120) String externalRevisionId,
        Long externalSize,
        Map<String, Object> metadata
) {
}
