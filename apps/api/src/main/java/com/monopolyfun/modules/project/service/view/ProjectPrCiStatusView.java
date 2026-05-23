package com.monopolyfun.modules.project.service.view;

import java.util.List;
import java.util.Map;

public record ProjectPrCiStatusView(
        List<Map<String, Object>> pullRequests,
        List<Map<String, Object>> checks
) {
}
