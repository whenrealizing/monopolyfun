package com.monopolyfun;

import com.monopolyfun.modules.identity.domain.IdentityBadgeEntity;
import com.monopolyfun.modules.identity.service.query.IdentityRoleBadgeFactory;
import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.project.domain.ProjectRoleEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityRoleBadgeFactoryTest {
    @Test
    void buildsOneBadgePerSystemProjectRole() {
        Instant now = Instant.now();
        List<ProjectRoleEntity> roles = List.of(
                role("project-root", ProjectRoleCode.SYSTEM_CEO, now.minusSeconds(20)),
                role("project-child", ProjectRoleCode.SYSTEM_CTO, now.minusSeconds(10)));

        List<IdentityBadgeEntity> badges = IdentityRoleBadgeFactory.build("acct-1", roles, "project-root");

        assertEquals(1, badges.size());
        assertEquals("role", badges.getFirst().kind());
        assertEquals("system_ceo", badges.getFirst().code());
        assertEquals("协议维护", badges.getFirst().label());
        assertEquals("crown", badges.getFirst().icon());
    }

    private ProjectRoleEntity role(String projectId, ProjectRoleCode roleCode, Instant assignedAt) {
        return new ProjectRoleEntity(
                "role-%s-%s".formatted(projectId, roleCode.code()),
                projectId,
                roleCode,
                "acct-1",
                "actor-1",
                assignedAt,
                Map.of(),
                assignedAt,
                assignedAt);
    }
}
