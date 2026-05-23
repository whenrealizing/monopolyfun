package com.monopolyfun.shared.observability.infra;

import com.monopolyfun.shared.observability.AuditEvent;

import java.util.List;

public interface AuditEventRepository {
    AuditEvent save(AuditEvent event);

    List<AuditEvent> findAll();

    List<AuditEvent> findRecent(int limit);
}
