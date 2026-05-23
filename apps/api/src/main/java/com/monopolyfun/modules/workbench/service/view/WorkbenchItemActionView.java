package com.monopolyfun.modules.workbench.service.view;

import java.util.List;

public record WorkbenchItemActionView(
        String id,
        String label,
        String mode,
        List<String> requiredInputs,
        String targetHref,
        boolean destructive
) {
}
