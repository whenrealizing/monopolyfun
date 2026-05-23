package com.monopolyfun.modules.project.api.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@JsonDeserialize(using = PublishProjectRequestDeserializer.class)
public record PublishProjectRequest(
        // 项目标题属于 post 共用字段，item 只描述可接单的具体任务。
        @NotBlank @Size(max = 80) String title,
        // 项目详情属于 post 共用字段，列表和详情页都从这里建立上下文。
        @NotBlank @Size(max = 1000) String description,
        // 项目目标保留为 project 顶层补充说明，具体 shares 价格由系统 difficultyScore 和 curve 计算。
        @Size(max = 2000) String goal,
        // 两层模型固定 child 只能挂 root；为空时使用当前 root project。
        String parentProjectId,
        // 发起人介绍，帮助参与者判断责任人与可信度。
        @Size(max = 2000) String ownerIntro,
        // 平台预创建仓库时返回的会话 id，publish 阶段据此绑定 project 与 repo。
        @Size(max = 120) String provisionSessionId,
        // 发布时同步创建的初始任务列表，项目任务的 shares 定价由系统曲线统一计算。
        @ArraySchema(
                minItems = 1,
                maxItems = 20,
                schema = @Schema(implementation = PublishProjectItemRequest.class),
                arraySchema = @Schema(description = "项目初始任务列表。创建 project 时必须同步初始化至少一个初始任务；amount、quantity、agentInstruction 由系统统一处理，所以请求体里不出现这些字段。"))
        @NotEmpty @Size(min = 1, max = 20) List<@Valid PublishProjectItemRequest> items
) {
}
