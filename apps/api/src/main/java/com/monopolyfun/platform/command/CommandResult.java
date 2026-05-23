package com.monopolyfun.platform.command;

import com.monopolyfun.platform.lifecycle.LifecycleTransition;

import java.util.List;
import java.util.Map;

public record CommandResult(
        String subjectId,
        String status,
        Map<String, Object> payload,
        List<LifecycleTransition<?, ?>> transitions
) {
    public CommandResult {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
    }
}
