package com.monopolyfun.modules.workthread.service.view;

import java.util.List;

public record WorkThreadPacketView(
        String projectNo,
        String projectId,
        String workThreadId,
        String threadNo,
        String runtime,
        int taskValue,
        int bountyAmountMinor,
        String bountyToken,
        String title,
        String goal,
        List<String> deliverables,
        List<String> acceptanceCriteria,
        String repoRef,
        String issueUrl,
        String markdown
) {
}
