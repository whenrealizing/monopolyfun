package com.monopolyfun.modules.project.api.response;

import com.monopolyfun.modules.project.service.view.ProjectView;

public record ProjectCreateResponse(
        ProjectView project
) {
}
