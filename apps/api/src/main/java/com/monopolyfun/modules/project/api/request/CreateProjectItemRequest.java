package com.monopolyfun.modules.project.api.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@JsonDeserialize(using = CreateProjectItemRequestDeserializer.class)
public record CreateProjectItemRequest(
        @Schema(description = "执行当前创建操作的账号 ID。")
        @NotBlank String actorAccountId,
        @Schema(description = "项目任务名称。")
        @NotBlank String name,
        @Schema(description = "项目任务补充说明。")
        String description,
        @Schema(description = "任务交付标准。agentInstruction 由系统根据交付标准派生。")
        @NotBlank String deliveryStandard,
        @Schema(description = "任务验收标准列表。为空时系统回退到 deliveryStandard。")
        List<@NotBlank String> acceptanceCriteria,
        @Schema(description = "项目任务难度分，用于领取时计算 shares。范围 0.5 到 8。")
        Double difficultyScore,
        @Schema(description = "项目任务类型。可选值 normal、bug、review、dispute。")
        String itemType,
        @Schema(description = "项目任务交付模式。可选值 reviewed_delivery、instant_fulfillment。")
        String mode
) {
}
