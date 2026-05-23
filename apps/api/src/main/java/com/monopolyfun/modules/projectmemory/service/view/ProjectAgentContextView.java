package com.monopolyfun.modules.projectmemory.service.view;

import java.util.List;
import java.util.Map;

public record ProjectAgentContextView(
        Map<String, Object> project,
        Map<String, List<ProjectMemoryEntryView>> memory,
        Map<String, Object> validation,
        Map<String, Object> workbench,
        Map<String, Object> toolContracts,
        Map<String, Object> memorySource
) {
}
