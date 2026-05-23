package com.monopolyfun.modules.repo.service;

import com.monopolyfun.modules.repo.domain.RepoDeliverySessionEntity;
import com.monopolyfun.modules.repo.infra.RepoProviderClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class RepoProofVerifier {
    private final RepoProviderClient repoProviderClient;

    public RepoProofVerifier(RepoProviderClient repoProviderClient) {
        this.repoProviderClient = repoProviderClient;
    }

    public RepoProviderClient.PullRequestInspection inspectPullRequest(
            RepoDeliverySessionEntity session,
            String repoOwner,
            String repoName,
            String prUrl,
            String expectedHeadCommit) {
        RepoProviderClient.PullRequestInspection inspection = repoProviderClient.inspectPullRequest(
                new RepoProviderClient.InspectPullRequestCommand(repoOwner, repoName, prUrl, expectedHeadCommit));
        if (inspection.draft()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pull request is still draft");
        }
        if (!isAllowedPrState(inspection.state(), inspection.merged())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pull request must be open or merged");
        }
        if (!isPassingStatus(inspection.ciStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pull request CI is not successful");
        }
        if (!session.repoUrl().equals(inspection.repoUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pull request repository does not match current project repository");
        }
        return inspection;
    }

    private boolean isAllowedPrState(String value, boolean merged) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "open".equals(normalized) || ("closed".equals(normalized) && merged);
    }

    private boolean isPassingStatus(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        // 中文注释：平台托管仓库可以没有 CI，真实 PR 绑定、分支和 commit 校验继续提供安全边界。
        return "success".equals(normalized) || "passed".equals(normalized) || "not_required".equals(normalized);
    }
}
