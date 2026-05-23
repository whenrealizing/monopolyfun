package com.monopolyfun.platform.command;

public record CommandMetadata(
        String commandType,
        String subjectType,
        String subjectId
) {
}
