package com.monopolyfun.modules.project.infra;

import com.monopolyfun.modules.project.domain.OrganizationEventEntity;

public interface OrganizationEventRepository {
    OrganizationEventEntity save(OrganizationEventEntity event);
}
