package com.monopolyfun.modules.identity.service.query;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.identity.infra.IdentityBadgeRepository;
import com.monopolyfun.modules.identity.infra.IdentityFactRepository;
import com.monopolyfun.modules.identity.infra.IdentityVerificationChallengeRepository;
import com.monopolyfun.modules.identity.service.display.IdentityDisplaySkinProjector;
import com.monopolyfun.modules.identity.service.verification.IdentityFactStatus;
import com.monopolyfun.modules.identity.service.verification.IdentityVerificationService;
import com.monopolyfun.modules.identity.service.view.IdentityPageView;
import com.monopolyfun.modules.identity.service.view.IdentityProfileView;
import com.monopolyfun.modules.project.infra.ProjectRoleRepository;
import com.monopolyfun.modules.project.service.RootProjectService;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class IdentityQueryService {
    private final CurrentAccountAccess currentAccountAccess;
    private final AccountRepository accountRepository;
    private final IdentityFactRepository identityFactRepository;
    private final IdentityBadgeRepository identityBadgeRepository;
    private final IdentityVerificationChallengeRepository challengeRepository;
    private final IdentityVerificationService identityVerificationService;
    private final IdentityDisplaySkinProjector identityDisplaySkinProjector;
    private final ProjectRoleRepository projectRoleRepository;
    private final RootProjectService rootProjectService;
    private final IdentityActivityQueryService identityActivityQueryService;

    public IdentityQueryService(
            CurrentAccountAccess currentAccountAccess,
            AccountRepository accountRepository,
            IdentityFactRepository identityFactRepository,
            IdentityBadgeRepository identityBadgeRepository,
            IdentityVerificationChallengeRepository challengeRepository,
            IdentityVerificationService identityVerificationService,
            IdentityDisplaySkinProjector identityDisplaySkinProjector,
            ProjectRoleRepository projectRoleRepository,
            RootProjectService rootProjectService,
            IdentityActivityQueryService identityActivityQueryService) {
        this.currentAccountAccess = currentAccountAccess;
        this.accountRepository = accountRepository;
        this.identityFactRepository = identityFactRepository;
        this.identityBadgeRepository = identityBadgeRepository;
        this.challengeRepository = challengeRepository;
        this.identityVerificationService = identityVerificationService;
        this.identityDisplaySkinProjector = identityDisplaySkinProjector;
        this.projectRoleRepository = projectRoleRepository;
        this.rootProjectService = rootProjectService;
        this.identityActivityQueryService = identityActivityQueryService;
    }

    public IdentityPageView getCurrentIdentity() {
        String accountId = currentAccountAccess.requireAccountId();
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));

        var facts = identityFactRepository.findByAccountId(accountId);
        var rootProject = rootProjectService.ensureRootProject(null);
        var badges = IdentityBadgeAssembler.unify(
                identityBadgeRepository.findByAccountId(accountId),
                IdentityRoleBadgeFactory.build(accountId, projectRoleRepository.findAssignedRolesByAccountId(accountId), rootProject.id()));
        var challenges = challengeRepository.findByAccountId(accountId, 8);
        var displayProjection = identityDisplaySkinProjector.project(account, facts);
        var now = Instant.now();
        var activeVerifiedFacts = facts.stream()
                .filter(fact -> IdentityFactStatus.isActiveVerified(fact, now))
                .toList();

        IdentityProfileView profile = new IdentityProfileView(
                com.monopolyfun.modules.identity.service.mapper.IdentityViewMapper.account(account, displayProjection.selected()),
                !activeVerifiedFacts.isEmpty(),
                activeVerifiedFacts.size(),
                badges.stream().map(com.monopolyfun.modules.identity.service.mapper.IdentityViewMapper::identityBadge).toList(),
                activeVerifiedFacts.stream()
                        .map(com.monopolyfun.modules.identity.service.mapper.IdentityViewMapper::identityLinkedAccount)
                        .toList(),
                displayProjection.selected(),
                displayProjection.candidates());

        return new IdentityPageView(
                profile,
                identityActivityQueryService.getActivity(accountId),
                identityVerificationService.listCertifiers().stream().map(com.monopolyfun.modules.identity.service.mapper.IdentityViewMapper::identityCertifier).toList(),
                challenges.stream().map(com.monopolyfun.modules.identity.service.mapper.IdentityViewMapper::identityChallenge).toList());
    }

    public com.monopolyfun.modules.identity.domain.IdentityVerificationChallengeEntity findChallengeByToken(String challengeToken) {
        return challengeRepository.findByChallengeToken(challengeToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identity verification challenge not found"));
    }
}
