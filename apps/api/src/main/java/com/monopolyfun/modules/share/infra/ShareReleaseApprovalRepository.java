package com.monopolyfun.modules.share.infra;

import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.share.domain.ShareReleaseApprovalEntity;

import java.util.List;

public interface ShareReleaseApprovalRepository {
    List<ShareReleaseApprovalEntity> findByRequestId(String requestId);

    ShareReleaseApprovalEntity save(String requestId, ProjectRoleCode roleCode, String approverAccountId);
}
