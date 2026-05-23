package com.monopolyfun.platform.command;

import com.monopolyfun.shared.command.CommandReceipt;
import com.monopolyfun.shared.observability.AuditEvent;
import com.monopolyfun.shared.observability.AuditEventRecorder;
import com.monopolyfun.shared.observability.CommandObservation;
import com.monopolyfun.shared.observability.StructuredLogger;
import com.monopolyfun.shared.observability.TraceContextHolder;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
public class CommandKernel {
    private final CurrentAccountAccess currentAccountAccess;
    private final TraceContextHolder traceContextHolder;
    private final AuditEventRecorder auditEventRecorder;
    private final StructuredLogger structuredLogger;

    public CommandKernel(
            CurrentAccountAccess currentAccountAccess,
            TraceContextHolder traceContextHolder,
            AuditEventRecorder auditEventRecorder,
            StructuredLogger structuredLogger) {
        this.currentAccountAccess = currentAccountAccess;
        this.traceContextHolder = traceContextHolder;
        this.auditEventRecorder = auditEventRecorder;
        this.structuredLogger = structuredLogger;
    }

    public CommandReceipt execute(CommandMetadata metadata, Function<CommandContext, CommandResult> handler) {
        Instant startedAt = Instant.now();
        String actorAccountId = currentAccountAccess.requireAccountId();
        String traceId = traceContextHolder.requireTraceId();
        CommandContext context = new CommandContext(actorAccountId, traceId, startedAt);

        try {
            CommandResult result = handler.apply(context);
            String subjectId = result.subjectId() == null ? metadata.subjectId() : result.subjectId();
            String auditId = recordAudit(metadata, subjectId, actorAccountId, traceId, "success", result.payload(), startedAt);
            structuredLogger.recordCommand(new CommandObservation(
                    metadata.commandType(),
                    metadata.subjectType(),
                    subjectId,
                    actorAccountId,
                    traceId,
                    "success",
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    Instant.now()));
            result.transitions().forEach(structuredLogger::recordLifecycleTransition);
            return new CommandReceipt(
                    "receipt-" + UUID.randomUUID(),
                    metadata.commandType(),
                    subjectId,
                    result.status(),
                    result.payload(),
                    actorAccountId,
                    traceId,
                    auditId,
                    Instant.now());
        } catch (RuntimeException exception) {
            recordFailure(metadata, actorAccountId, traceId, startedAt, exception);
            throw exception;
        }
    }

    private void recordFailure(
            CommandMetadata metadata,
            String actorAccountId,
            String traceId,
            Instant startedAt,
            RuntimeException exception) {
        recordAudit(metadata, metadata.subjectId(), actorAccountId, traceId, "failed", Map.of(
                "error", errorMessage(exception)), startedAt);
        structuredLogger.recordCommand(new CommandObservation(
                metadata.commandType(),
                metadata.subjectType(),
                metadata.subjectId(),
                actorAccountId,
                traceId,
                "failed",
                Duration.between(startedAt, Instant.now()).toMillis(),
                Instant.now()));
    }

    private String recordAudit(
            CommandMetadata metadata,
            String subjectId,
            String actorAccountId,
            String traceId,
            String outcome,
            Map<String, Object> payload,
            Instant createdAt) {
        String auditId = "audit-" + UUID.randomUUID();
        auditEventRecorder.record(new AuditEvent(
                auditId,
                metadata.commandType(),
                metadata.subjectType(),
                subjectId,
                actorAccountId,
                traceId,
                outcome,
                payload,
                createdAt));
        return auditId;
    }

    private String errorMessage(RuntimeException exception) {
        if (exception instanceof ResponseStatusException responseStatusException) {
            return responseStatusException.getReason() == null
                    ? responseStatusException.getStatusCode().toString()
                    : responseStatusException.getReason();
        }
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
