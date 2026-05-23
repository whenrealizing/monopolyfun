package com.monopolyfun.modules.identity.infra;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccountRepository {
    List<AccountEntity> findAll();

    PageResult<AccountEntity> findPublic(PageQuery pageQuery);

    PageResult<AccountEntity> findRiskAccounts(String status, String riskLevel, String q, PageQuery pageQuery);

    List<AccountEntity> findByIds(Collection<String> ids);

    default List<AccountEntity> findByHandles(Collection<String> handles) {
        if (handles == null || handles.isEmpty()) {
            return List.of();
        }
        return handles.stream()
                .filter(handle -> handle != null && !handle.isBlank())
                .map(this::findByHandle)
                .flatMap(Optional::stream)
                .toList();
    }

    Optional<AccountEntity> findById(String id);

    Optional<AccountEntity> findByHandle(String handle);

    AccountEntity save(AccountEntity account);
}
