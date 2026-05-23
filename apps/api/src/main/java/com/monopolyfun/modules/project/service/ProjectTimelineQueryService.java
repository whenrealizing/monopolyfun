package com.monopolyfun.modules.project.service;

import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.infra.ProjectTimelineRepository;
import com.monopolyfun.modules.project.service.view.ProjectTimelineEventView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProjectTimelineQueryService {
    private final ProjectRepository projectRepository;
    private final ProjectTimelineRepository projectTimelineRepository;

    public ProjectTimelineQueryService(ProjectRepository projectRepository, ProjectTimelineRepository projectTimelineRepository) {
        this.projectRepository = projectRepository;
        this.projectTimelineRepository = projectTimelineRepository;
    }

    public List<ProjectTimelineEventView> getTimeline(String projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        // 中文注释：Timeline 的业务表达统一来自 project_timeline_events，UI/agent 拿到同一组事件语义。
        return projectTimelineRepository.findByProjectId(projectId);
    }
}
