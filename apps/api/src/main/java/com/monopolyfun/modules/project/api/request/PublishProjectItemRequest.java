package com.monopolyfun.modules.project.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PublishProjectItemRequest(
        @Schema(description = "项目初始任务名称。")
        @NotBlank @Size(max = 120) String name,
        @Schema(description = "项目初始任务补充说明。")
        @Size(max = 1000) String description,
        @Schema(description = "任务交付标准。agentInstruction 由系统根据交付标准派生。")
        @NotBlank @Size(max = 1000) String deliveryStandard,
        @Schema(description = "任务验收标准列表。为空时系统回退到 deliveryStandard。")
        @Size(max = 20) List<@NotBlank @Size(max = 500) String> acceptanceCriteria,
        @Schema(description = "项目任务难度分，用于领取时计算 shares。范围 0.5 到 8。")
        Double difficultyScore
) {
}
