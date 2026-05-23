package com.monopolyfun.modules.post.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CreatePostItemRequest(
        @Schema(description = "执行当前创建操作的账号 ID。")
        @NotBlank String actorAccountId,
        @Schema(description = "报价项或需求项名称。Project 任务请使用 /api/v1/projects/{projectNo}/items。")
        @NotBlank String name,
        @Schema(description = "任务补充说明。")
        String description,
        @Schema(description = "任务交付标准。")
        @NotBlank String deliveryStandard,
        @Schema(description = "任务验收标准列表。")
        List<@NotBlank String> acceptanceCriteria,
        @Schema(description = "报价项价格或需求项预算。Project 任务由系统定价，这个字段只用于 offer/request。")
        @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount,
        @Schema(description = "Project 任务难度分。offer/request 不使用这个字段。")
        Double difficultyScore,
        @Schema(description = "席位数量。Project 任务固定为 1，这个字段只用于 offer/request。")
        @NotNull @Min(1) Integer quantity,
        @Schema(description = "Agent 执行提示。Project 任务由系统根据 deliveryStandard 派生，这个字段只用于 offer/request。")
        String agentInstruction,
        String itemType,
        String mode
) {
}
