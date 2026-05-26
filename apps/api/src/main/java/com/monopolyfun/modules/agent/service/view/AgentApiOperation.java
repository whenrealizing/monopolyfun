package com.monopolyfun.modules.agent.service.view;

import java.util.Map;

public record AgentApiOperation(
        String operationId,
        String method,
        String path,
        Map<String, Object> pathParams,
        Map<String, Object> queryParams
) {
}
