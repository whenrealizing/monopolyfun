package com.monopolyfun.modules.workbench.service.query;

public final class WorkbenchItemKeys {
    private WorkbenchItemKeys() {
    }

    public static String itemKey(String reason, String subjectType, String subjectId) {
        return reason + ":" + subjectType + ":" + subjectId;
    }
}
