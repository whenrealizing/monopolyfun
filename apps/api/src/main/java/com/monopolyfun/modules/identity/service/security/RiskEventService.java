package com.monopolyfun.modules.identity.service.security;

import com.monopolyfun.modules.risk.domain.RiskEventEntity;
import com.monopolyfun.modules.risk.infra.RiskEventRepository;
import com.monopolyfun.shared.observability.TraceContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class RiskEventService {
    private final RiskEventRepository riskEventRepository;
    private final TraceContextHolder traceContextHolder;

    public RiskEventService(RiskEventRepository riskEventRepository, TraceContextHolder traceContextHolder) {
        this.riskEventRepository = riskEventRepository;
        this.traceContextHolder = traceContextHolder;
    }

    public void record(
            String kind,
            String subjectType,
            String subjectId,
            String actorRef,
            String severity,
            String reason,
            Map<String, Object> payload) {
        riskEventRepository.save(new RiskEventEntity(
                "risk-" + UUID.randomUUID(),
                kind,
                subjectType,
                subjectId,
                actorRef,
                severity,
                reason,
                mergeTrace(payload),
                Instant.now()));
    }

    private Map<String, Object> mergeTrace(Map<String, Object> payload) {
        String traceId = traceContextHolder.currentTraceId().orElse(null);
        if (traceId == null) {
            return payload == null ? Map.of() : Map.copyOf(payload);
        }
        java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>();
        if (payload != null) {
            merged.putAll(payload);
        }
        merged.put("traceId", traceId);
        return Map.copyOf(merged);
    }
}
