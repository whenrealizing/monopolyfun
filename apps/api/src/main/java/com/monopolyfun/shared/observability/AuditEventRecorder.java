package com.monopolyfun.shared.observability;

public interface AuditEventRecorder {
    void record(AuditEvent event);
}
