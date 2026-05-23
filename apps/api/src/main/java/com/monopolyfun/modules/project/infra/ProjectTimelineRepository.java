package com.monopolyfun.modules.project.infra;

import com.monopolyfun.modules.project.service.view.ProjectTimelineEventView;

import java.util.List;

public interface ProjectTimelineRepository {
    List<ProjectTimelineEventView> findByProjectId(String projectId);
}
