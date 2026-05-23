package com.monopolyfun.modules.project.infra;

import java.util.List;
import java.util.Map;

public interface ProjectAgentProtocolRepository {
    void saveProposalPack(String projectId, String packId, String actorAccountId, Map<String, Object> pack);

    void savePackEvent(String projectId, String packId, String actorAccountId, String subjectType, String eventType, String actionId, Map<String, Object> output);

    boolean saveShareLedgerEntry(
            String projectId,
            String packId,
            String accountId,
            String role,
            int amount,
            int curveSlot);

    List<Map<String, Object>> findProposalPacks(String projectId);

    List<Map<String, Object>> findPackEvents(String projectId, String subjectType);

    boolean hasShareAllocation(String packId);
}
