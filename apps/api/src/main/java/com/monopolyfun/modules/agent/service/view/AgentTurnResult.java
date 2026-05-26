package com.monopolyfun.modules.agent.service.view;

import com.monopolyfun.modules.agent.api.request.AgentSubject;
import com.monopolyfun.shared.command.CommandReceipt;

import java.util.List;
import java.util.Map;

public record AgentTurnResult(
        String turnId,
        String scene,
        AgentSubject subject,
        String state,
        List<AgentActionCard> actions,
        Map<String, Object> result,
        List<Map<String, Object>> effects,
        AgentTurnProjection projection,
        CommandReceipt receipt,
        AgentTurnPointer nextTurn
) {
}
