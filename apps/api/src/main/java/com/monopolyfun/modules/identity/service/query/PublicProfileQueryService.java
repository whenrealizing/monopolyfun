package com.monopolyfun.modules.identity.service.query;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.identity.infra.IdentityBadgeRepository;
import com.monopolyfun.modules.identity.infra.IdentityFactRepository;
import com.monopolyfun.modules.identity.service.PublicIdentityRefs;
import com.monopolyfun.modules.identity.service.display.AccountSummaryProjector;
import com.monopolyfun.modules.identity.service.verification.IdentityFactStatus;
import com.monopolyfun.modules.identity.service.view.PublicProfileActivityView;
import com.monopolyfun.modules.identity.service.view.PublicProfileIdentityView;
import com.monopolyfun.modules.identity.service.view.PublicProfileView;
import com.monopolyfun.modules.post.infra.OfferRepository;
import com.monopolyfun.modules.post.infra.RequestPostRepository;
import com.monopolyfun.modules.post.service.mapper.PostViewMapper;
import com.monopolyfun.modules.post.service.query.PostQueryService;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.infra.ProjectRoleRepository;
import com.monopolyfun.modules.project.service.RootProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class PublicProfileQueryService {
    private static final int RECENT_LIMIT = 12;

    private final AccountRepository accountRepository;
    private final AccountSummaryProjector accountSummaryProjector;
    private final IdentityFactRepository identityFactRepository;
    private final IdentityBadgeRepository identityBadgeRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final RootProjectService rootProjectService;
    private final OfferRepository offerRepository;
    private final RequestPostRepository requestPostRepository;
    private final ProjectRepository projectRepository;
    private final PostQueryService postQueryService;

    public PublicProfileQueryService(
            AccountRepository accountRepository,
            AccountSummaryProjector accountSummaryProjector,
            IdentityFactRepository identityFactRepository,
            IdentityBadgeRepository identityBadgeRepository,
            ProjectRoleRepository projectRoleRepository,
            RootProjectService rootProjectService,
            OfferRepository offerRepository,
            RequestPostRepository requestPostRepository,
            ProjectRepository projectRepository,
            PostQueryService postQueryService) {
        this.accountRepository = accountRepository;
        this.accountSummaryProjector = accountSummaryProjector;
        this.identityFactRepository = identityFactRepository;
        this.identityBadgeRepository = identityBadgeRepository;
        this.projectRoleRepository = projectRoleRepository;
        this.rootProjectService = rootProjectService;
        this.offerRepository = offerRepository;
        this.requestPostRepository = requestPostRepository;
        this.projectRepository = projectRepository;
        this.postQueryService = postQueryService;
    }

    public PublicProfileView getByHandle(String handle) {
        String normalizedHandle = PublicIdentityRefs.accountId(handle);
        if (normalizedHandle == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Public profile not found");
        }

        AccountEntity account = findAccountByPublicHandle(normalizedHandle);
        var facts = identityFactRepository.findByAccountId(account.id());
        var now = Instant.now();
        var activeVerifiedFacts = facts.stream()
                .filter(fact -> "external_identity".equals(fact.factType()))
                .filter(fact -> IdentityFactStatus.isActiveVerified(fact, now))
                .toList();
        var rootProject = rootProjectService.ensureRootProject(null);
        var badges = IdentityBadgeAssembler.unify(
                identityBadgeRepository.findByAccountId(account.id()),
                IdentityRoleBadgeFactory.build(account.id(), projectRoleRepository.findAssignedRolesByAccountId(account.id()), rootProject.id()));

        // 中文注释：公开主页沿用身份中心的展示皮肤投影，让市场卡片、订单详情和主页保持同一公开身份外观。
        var publicAccount = accountSummaryProjector.publicProject(account);
        var profile = new PublicProfileIdentityView(
                publicAccount,
                !activeVerifiedFacts.isEmpty(),
                activeVerifiedFacts.size(),
                badges.stream().map(com.monopolyfun.modules.identity.service.mapper.IdentityViewMapper::identityBadge).toList(),
                activeVerifiedFacts.stream()
                        .map(com.monopolyfun.modules.identity.service.mapper.IdentityViewMapper::identityLinkedAccount)
                        .toList(),
                publicAccount.displaySkin());

        var offers = offerRepository.findPublicByActorAccountId(account.id(), RECENT_LIMIT).stream()
                .map(offer -> PostViewMapper.publicOffer(offer, account.handle()))
                .toList();
        var requests = requestPostRepository.findPublicByActorAccountId(account.id(), RECENT_LIMIT).stream()
                .map(request -> PostViewMapper.publicRequest(request, account.handle()))
                .toList();
        var projects = postQueryService.projectViews(projectRepository.findPublicByOwnerAccountId(account.id(), RECENT_LIMIT));

        return new PublicProfileView(
                profile,
                new PublicProfileActivityView(offers, requests, projects));
    }

    private AccountEntity findAccountByPublicHandle(String normalizedHandle) {
        // 中文注释：历史账号可能带 @ 前缀存储，公开主页统一先按公开 handle 规范化后回查双格式。
        return accountRepository.findByHandle(normalizedHandle)
                .or(() -> accountRepository.findByHandle("@" + normalizedHandle))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Public profile not found"));
    }
}
