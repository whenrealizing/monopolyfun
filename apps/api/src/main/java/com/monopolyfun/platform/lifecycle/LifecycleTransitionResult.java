package com.monopolyfun.platform.lifecycle;

public record LifecycleTransitionResult<E extends LifecycleEntity<S>, S extends Enum<S>, A extends Enum<A>>(
        E entity,
        LifecycleTransition<S, A> transition
) {
}
