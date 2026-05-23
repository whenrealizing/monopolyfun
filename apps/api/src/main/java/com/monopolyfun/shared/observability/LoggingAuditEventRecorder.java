package com.monopolyfun.shared.observability;

import com.monopolyfun.shared.observability.infra.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingAuditEventRecorder implements AuditEventRecorder {
    private static final Logger log = LoggerFactory.getLogger(LoggingAuditEventRecorder.class);
    private final AuditEventRepository auditEventRepository;

    public LoggingAuditEventRecorder(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    public void record(AuditEvent event) {
        auditEventRepository.save(event);
        log.info(
                "audit id={} type={} subjectType={} subjectId={} actor={} traceId={} outcome={} payload={}",
                event.id(),
                event.type(),
                event.subjectType(),
                event.subjectId(),
                event.actorAccountId(),
                event.traceId(),
                event.outcome(),
                event.payload());
    }
}
