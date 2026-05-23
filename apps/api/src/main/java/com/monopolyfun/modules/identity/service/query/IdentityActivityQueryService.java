package com.monopolyfun.modules.identity.service.query;

import com.monopolyfun.modules.identity.service.view.IdentityActivityView;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.post.infra.OfferRepository;
import com.monopolyfun.modules.post.infra.RequestPostRepository;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.share.infra.ShareSettlementHoldRepository;
import com.monopolyfun.modules.share.infra.SharesLedgerRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class IdentityActivityQueryService {
    private final OfferRepository offerRepository;
    private final RequestPostRepository requestPostRepository;
    private final ProjectRepository projectRepository;
    private final OrderRepository orderRepository;
    private final SharesLedgerRepository sharesLedgerRepository;
    private final ShareSettlementHoldRepository shareSettlementHoldRepository;

    public IdentityActivityQueryService(
            OfferRepository offerRepository,
            RequestPostRepository requestPostRepository,
            ProjectRepository projectRepository,
            OrderRepository orderRepository,
            SharesLedgerRepository sharesLedgerRepository,
            ShareSettlementHoldRepository shareSettlementHoldRepository) {
        this.offerRepository = offerRepository;
        this.requestPostRepository = requestPostRepository;
        this.projectRepository = projectRepository;
        this.orderRepository = orderRepository;
        this.sharesLedgerRepository = sharesLedgerRepository;
        this.shareSettlementHoldRepository = shareSettlementHoldRepository;
    }

    public IdentityActivityView getActivity(String accountId) {
        // 中文注释：个人活动页集中读取交易和资产读模型，IdentityProfile 查询保持账号/认证职责。
        var myOffers = offerRepository.findByActorAccountId(accountId, 200).stream()
                .map(com.monopolyfun.modules.post.service.mapper.PostViewMapper::offer)
                .toList();
        var myRequests = requestPostRepository.findByActorAccountId(accountId, 200).stream()
                .map(com.monopolyfun.modules.post.service.mapper.PostViewMapper::request)
                .toList();
        var myProjects = projectRepository.findByOwnerAccountId(accountId, 200).stream()
                .map(com.monopolyfun.modules.project.service.mapper.ProjectViewMapper::project)
                .toList();
        var myOrders = orderRepository.findByParticipantAccountId(accountId, 300).stream()
                .map(order -> com.monopolyfun.modules.order.service.mapper.OrderViewMapper.order(order, accountId))
                .toList();
        var sharesLedger = sharesLedgerRepository.findByAccountId(accountId);
        var shareSettlementHolds = shareSettlementHoldRepository.findByAccountId(accountId).stream()
                .map(hold -> {
                    var order = myOrders.stream().filter(item -> item.id().equals(hold.orderId())).findFirst().orElse(null);
                    return com.monopolyfun.modules.share.service.mapper.ShareViewMapper.shareSettlementHold(hold, order);
                })
                .toList();
        return new IdentityActivityView(
                myOffers,
                myRequests,
                myProjects,
                myOrders,
                sharesLedger,
                shareSettlementHolds,
                Map.of(
                        "openOffers", myOffers.stream().filter(offer -> "open".equals(offer.status())).count(),
                        "openRequests", myRequests.stream().filter(request -> "open".equals(request.status())).count(),
                        "activeProjects", myProjects.stream().filter(project -> "active".equals(project.status())).count(),
                        "activeOrders", myOrders.stream().filter(order -> !"final_accepted".equals(order.status()) && !"final_closed".equals(order.status())).count(),
                        "sharesEntries", sharesLedger.size(),
                        "shareHolds", shareSettlementHolds.size()));
    }
}
