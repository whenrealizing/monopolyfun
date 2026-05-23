package com.monopolyfun.modules.identity.service.display;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.IdentityFactRepository;
import com.monopolyfun.modules.identity.service.view.AccountSummary;
import com.monopolyfun.modules.identity.service.view.PublicAccountSummary;
import org.springframework.stereotype.Service;

@Service
public class AccountSummaryProjector {
    private final IdentityFactRepository identityFactRepository;
    private final IdentityDisplaySkinProjector displaySkinProjector;

    public AccountSummaryProjector(
            IdentityFactRepository identityFactRepository,
            IdentityDisplaySkinProjector displaySkinProjector) {
        this.identityFactRepository = identityFactRepository;
        this.displaySkinProjector = displaySkinProjector;
    }

    public AccountSummary project(AccountEntity account) {
        // 中文注释：账号摘要统一从认证事实生成展示皮肤，保证会话、列表、订单卡片使用同一个读模型。
        var projection = displaySkinProjector.project(account, identityFactRepository.findByAccountId(account.id()));
        return com.monopolyfun.modules.identity.service.mapper.IdentityViewMapper.account(account, projection.selected());
    }

    public PublicAccountSummary publicProject(AccountEntity account) {
        // 中文注释：公开账号目录统一使用 handle 派生的公开 profile id，避免内部账号主键泄露到市场读模型。
        var projection = displaySkinProjector.project(account, identityFactRepository.findByAccountId(account.id()));
        return com.monopolyfun.modules.identity.service.mapper.IdentityViewMapper.publicAccount(account, projection.selected());
    }
}
