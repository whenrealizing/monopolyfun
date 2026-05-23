package com.monopolyfun.modules.project.domain;

import java.util.Locale;

public enum ProjectLevel {
    ROOT("root"),
    CHILD("child");

    private final String code;

    ProjectLevel(String code) {
        this.code = code;
    }

    public static ProjectLevel fromCode(String code) {
        for (ProjectLevel level : values()) {
            if (level.code.equalsIgnoreCase(code)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown project level: " + code.toLowerCase(Locale.ROOT));
    }

    public String code() {
        return code;
    }
}
