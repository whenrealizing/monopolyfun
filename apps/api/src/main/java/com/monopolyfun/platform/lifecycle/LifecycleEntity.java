package com.monopolyfun.platform.lifecycle;

public interface LifecycleEntity<S extends Enum<S>> {
    String lifecycleId();

    String lifecycleType();

    S lifecycleStatus();

    String lifecycleDisplayPhase();
}
