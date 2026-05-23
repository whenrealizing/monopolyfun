package com.monopolyfun.modules.projectmemory.service.view;

import java.util.List;

public record ProjectMemoryOverviewView(
        ProjectMemoryRootView latestRoot,
        List<ProjectMemorySourceView> sources,
        List<ProjectMemoryEntryView> entries,
        List<ProjectMemorySyncEventView> events
) {
}
