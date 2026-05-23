package com.monopolyfun.shared.observability;

import com.monopolyfun.platform.lifecycle.LifecycleTransition;

public interface StructuredLogger {
    void recordCommand(CommandObservation observation);

    void recordLifecycleTransition(LifecycleTransition<?, ?> transition);
}
