package com.monopolyfun.shared.observability;

import com.monopolyfun.platform.lifecycle.LifecycleTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Slf4jStructuredLogger implements StructuredLogger {
    private static final Logger log = LoggerFactory.getLogger(Slf4jStructuredLogger.class);

    @Override
    public void recordCommand(CommandObservation observation) {
        log.info(
                "command type={} subjectType={} subjectId={} actor={} traceId={} outcome={} durationMs={}",
                observation.commandType(),
                observation.subjectType(),
                observation.subjectId(),
                observation.actorAccountId(),
                observation.traceId(),
                observation.outcome(),
                observation.durationMs());
    }

    @Override
    public void recordLifecycleTransition(LifecycleTransition<?, ?> transition) {
        log.info(
                "lifecycle entityType={} entityId={} action={} fromStatus={} toStatus={} fromPhase={} toPhase={} actor={} traceId={} metadata={}",
                transition.entityType(),
                transition.entityId(),
                transition.action(),
                transition.fromStatus(),
                transition.toStatus(),
                transition.fromDisplayPhase(),
                transition.toDisplayPhase(),
                transition.actorAccountId(),
                transition.traceId(),
                transition.metadata());
    }
}
