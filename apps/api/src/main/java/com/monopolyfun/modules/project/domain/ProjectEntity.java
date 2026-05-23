package com.monopolyfun.modules.project.domain;

import com.monopolyfun.modules.post.domain.InventoryPolicy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record ProjectEntity(
        // 项目主键。
        String id,
        // 用户可见项目编号，用于页面展示、搜索和对账。
        String projectNo,
        // 发起人账号 id。
        String ownerAccountId,
        // 两层项目模型中的层级，root 承接系统治理，child 承接独立项目治理。
        ProjectLevel projectLevel,
        // child 项目挂在 root 下，root 自身保持为空。
        String parentProjectId,
        // 项目标题，通常由 oneSentence 截断生成。
        String title,
        // 项目摘要，列表页和详情页概览区直接读取。
        String summary,
        // 一句话项目描述，保留发布时最原始的核心表达。
        String oneSentence,
        // 库存策略，决定席位的开放方式。
        InventoryPolicy inventoryPolicy,
        // 项目可同时开放的参与席位上限，single 会固定成 1，unlimited 会为空。
        Integer stockTotal,
        // 已售出或已占用的席位数量。
        int stockSold,
        // 项目状态。
        ProjectStatus status,
        // 扩展业务字段容器，承接 goal、deliverables、referenceLinks 等长文本信息。
        Map<String, Object> metadata,
        // 创建时间。
        Instant createdAt,
        // 最近更新时间。
        Instant updatedAt
) {
    public ProjectEntity withOwner(String nextOwnerAccountId, ProjectStatus nextStatus, Map<String, Object> nextMetadata) {
        return new ProjectEntity(id, projectNo, nextOwnerAccountId, projectLevel, parentProjectId, title, summary, oneSentence, inventoryPolicy, stockTotal, stockSold,
                nextStatus, nextMetadata, createdAt, Instant.now());
    }

    public ProjectEntity withStatus(ProjectStatus nextStatus, Map<String, Object> nextMetadata) {
        return new ProjectEntity(id, projectNo, ownerAccountId, projectLevel, parentProjectId, title, summary, oneSentence, inventoryPolicy, stockTotal, stockSold,
                nextStatus, nextMetadata, createdAt, Instant.now());
    }

    public ProjectEntity withMetadata(Map<String, Object> nextMetadata) {
        return new ProjectEntity(id, projectNo, ownerAccountId, projectLevel, parentProjectId, title, summary, oneSentence, inventoryPolicy, stockTotal, stockSold,
                status, nextMetadata, createdAt, Instant.now());
    }

    public Map<String, Object> mutableMetadata() {
        return new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
    }
}
