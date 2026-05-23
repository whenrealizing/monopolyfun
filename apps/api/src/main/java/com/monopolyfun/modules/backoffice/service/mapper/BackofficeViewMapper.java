package com.monopolyfun.modules.backoffice.service.mapper;

import com.monopolyfun.modules.backoffice.service.view.AuditEventView;
import com.monopolyfun.shared.observability.AuditEvent;

public final class BackofficeViewMapper {
    private BackofficeViewMapper() {
    }

    public static AuditEventView audit(AuditEvent event) {
        if (event == null) return null;
        return new AuditEventView(
                event.id(),
                event.type(),
                event.subjectType(),
                event.subjectId(),
                event.actorAccountId(),
                event.traceId(),
                event.outcome(),
                event.payload(),
                event.createdAt());
    }
}
