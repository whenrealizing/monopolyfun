package com.monopolyfun.modules.work.infra;

import com.monopolyfun.modules.work.domain.WorkItemEntity;
import com.monopolyfun.modules.work.domain.WorkReviewEntity;
import com.monopolyfun.modules.work.domain.WorkRunEntity;

import java.util.Map;

public interface WorkCommerceTrustRepository {
    void savePaymentAuthorization(
            WorkRunEntity run,
            WorkItemEntity item,
            String actorAccountId,
            String status,
            Map<String, Object> input,
            Map<String, Object> output);

    void saveSettlementRecord(
            WorkRunEntity run,
            WorkReviewEntity review,
            WorkItemEntity item,
            String actorAccountId,
            String status,
            Map<String, Object> input,
            Map<String, Object> output);

    void saveAfterSaleCase(
            WorkRunEntity run,
            WorkReviewEntity review,
            WorkItemEntity item,
            String actorAccountId,
            String status,
            String reason,
            Map<String, Object> input);

    void saveArbitrationCase(
            WorkRunEntity run,
            WorkReviewEntity review,
            WorkItemEntity item,
            String actorAccountId,
            String status,
            String reason,
            Map<String, Object> input);
}
