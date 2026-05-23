package com.monopolyfun.modules.risk.service.mapper;

import com.monopolyfun.modules.risk.domain.RiskEventEntity;
import com.monopolyfun.modules.risk.service.view.RiskEventView;

public final class RiskViewMapper {
    private RiskViewMapper() {
    }

    public static RiskEventView risk(RiskEventEntity event) {
        if (event == null) return null;
        return new RiskEventView(
                event.id(),
                event.kind(),
                event.subjectType(),
                event.subjectId(),
                event.actorRef(),
                event.severity(),
                event.reason(),
                event.payload(),
                event.createdAt());
    }
}
