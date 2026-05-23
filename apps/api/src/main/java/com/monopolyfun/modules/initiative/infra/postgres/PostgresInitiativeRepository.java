package com.monopolyfun.modules.initiative.infra.postgres;

import com.fasterxml.jackson.core.type.TypeReference;
import com.monopolyfun.modules.initiative.domain.AgentActionProposalEntity;
import com.monopolyfun.modules.initiative.domain.AgentActionRunEntity;
import com.monopolyfun.modules.initiative.domain.AgentMandateEntity;
import com.monopolyfun.modules.initiative.domain.AgentOpportunityEntity;
import com.monopolyfun.modules.initiative.domain.ProjectInitiativeRecommendationEntity;
import com.monopolyfun.modules.initiative.infra.InitiativeRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PostgresInitiativeRepository implements InitiativeRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };
    private static final Table<?> WORK_ITEMS = DSL.table(DSL.name("work_items"));
    private static final Table<?> PROJECT_RECOMMENDATIONS = DSL.table(DSL.name("project_initiative_recommendations"));

    private static final Field<String> ID = DSL.field(DSL.name("id"), String.class);
    private static final Field<String> ITEM_NO = DSL.field(DSL.name("item_no"), String.class);
    private static final Field<String> SOURCE_TYPE = DSL.field(DSL.name("source_type"), String.class);
    private static final Field<String> SOURCE_ID = DSL.field(DSL.name("source_id"), String.class);
    private static final Field<String> ACCOUNT_ID = DSL.field(DSL.name("account_id"), String.class);
    private static final Field<String> TITLE = DSL.field(DSL.name("title"), String.class);
    private static final Field<String> GOAL = DSL.field(DSL.name("goal"), String.class);
    private static final Field<JSONB> ACCEPTANCE = DSL.field(DSL.name("acceptance_criteria"), JSONB.class);
    private static final Field<JSONB> INPUT_REFS = DSL.field(DSL.name("input_refs"), JSONB.class);
    private static final Field<JSONB> OUTPUT_SCHEMA = DSL.field(DSL.name("output_schema"), JSONB.class);
    private static final Field<String> REQUIRED_ROLE = DSL.field(DSL.name("required_role"), String.class);
    private static final Field<String> URGENCY = DSL.field(DSL.name("urgency"), String.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<OffsetDateTime> READY_AT = DSL.field(DSL.name("ready_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> CREATED_AT = DSL.field(DSL.name("created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = DSL.field(DSL.name("updated_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public PostgresInitiativeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public AgentMandateEntity saveMandate(AgentMandateEntity mandate) {
        Map<String, Object> state = Map.of(
                "mandateNo", mandate.mandateNo(),
                "goal", mandate.goal(),
                "scope", mandate.scope(),
                "budget", mandate.budget(),
                "riskPolicy", mandate.riskPolicy(),
                "status", mandate.status());
        upsertWorkItem(mandate.id(), mandate.mandateNo(), "agent_mandate", mandate.mandateNo(), mandate.accountId(),
                title(mandate.goal()), mandate.goal(), mandate.scope(), List.of(), state, "agent", workStatus(mandate.status()),
                mandate.createdAt(), mandate.updatedAt());
        return mandate;
    }

    @Override
    public List<AgentMandateEntity> findMandatesByAccountId(String accountId) {
        return dsl.selectFrom(WORK_ITEMS)
                .where(SOURCE_TYPE.eq("agent_mandate"))
                .and(ACCOUNT_ID.eq(accountId))
                .orderBy(UPDATED_AT.desc())
                .fetch(this::mapMandate);
    }

    @Override
    public Optional<AgentMandateEntity> findMandateByNo(String mandateNo) {
        return dsl.selectFrom(WORK_ITEMS)
                .where(SOURCE_TYPE.eq("agent_mandate"))
                .and(ITEM_NO.eq(mandateNo))
                .fetchOptional(this::mapMandate);
    }

    @Override
    public AgentOpportunityEntity saveOpportunity(AgentOpportunityEntity opportunity) {
        AgentMandateEntity mandate = mandateById(opportunity.mandateId());
        Map<String, Object> state = Map.of(
                "opportunityNo", opportunity.opportunityNo(),
                "mandateId", opportunity.mandateId(),
                "mandateNo", mandate.mandateNo(),
                "type", opportunity.type(),
                "reason", opportunity.reason(),
                "targetType", opportunity.targetType(),
                "targetId", opportunity.targetId(),
                "suggestedAction", opportunity.suggestedAction(),
                "status", opportunity.status());
        upsertWorkItem(opportunity.id(), opportunity.opportunityNo(), "agent_opportunity", mandate.mandateNo(), mandate.accountId(),
                title(opportunity.reason()), opportunity.reason(), List.of(opportunity.suggestedAction()),
                List.of("mandate:" + mandate.mandateNo()), state, "agent", workStatus(opportunity.status()),
                opportunity.createdAt(), opportunity.updatedAt());
        return opportunity;
    }

    @Override
    public List<AgentOpportunityEntity> findOpportunitiesByMandateId(String mandateId) {
        return dsl.selectFrom(WORK_ITEMS)
                .where(SOURCE_TYPE.eq("agent_opportunity"))
                .and(jsonText("mandateId").eq(mandateId))
                .orderBy(CREATED_AT.desc())
                .fetch(this::mapOpportunity);
    }

    @Override
    public Optional<AgentOpportunityEntity> findOpportunityById(String opportunityId) {
        return dsl.selectFrom(WORK_ITEMS)
                .where(SOURCE_TYPE.eq("agent_opportunity"))
                .and(ID.eq(opportunityId))
                .fetchOptional(this::mapOpportunity);
    }

    @Override
    public Optional<AgentOpportunityEntity> findOpportunityByNo(String opportunityNo) {
        return dsl.selectFrom(WORK_ITEMS)
                .where(SOURCE_TYPE.eq("agent_opportunity"))
                .and(ITEM_NO.eq(opportunityNo))
                .fetchOptional(this::mapOpportunity);
    }

    @Override
    public AgentActionProposalEntity saveProposal(AgentActionProposalEntity proposal) {
        AgentMandateEntity mandate = mandateById(proposal.mandateId());
        String opportunityNo = opportunityNo(proposal.opportunityId());
        Map<String, Object> state = Map.ofEntries(
                Map.entry("proposalNo", proposal.proposalNo()),
                Map.entry("opportunityId", proposal.opportunityId() == null ? "" : proposal.opportunityId()),
                Map.entry("opportunityNo", opportunityNo == null ? "" : opportunityNo),
                Map.entry("mandateId", proposal.mandateId()),
                Map.entry("mandateNo", mandate.mandateNo()),
                Map.entry("actionId", proposal.actionId()),
                Map.entry("reason", proposal.reason()),
                Map.entry("risk", proposal.risk()),
                Map.entry("input", proposal.input()),
                Map.entry("expectedOutcome", proposal.expectedOutcome()),
                Map.entry("status", proposal.status()));
        upsertWorkItem(proposal.id(), proposal.proposalNo(), "agent_action_proposal", mandate.mandateNo(), mandate.accountId(),
                title(proposal.reason()), proposal.expectedOutcome(), List.of(), List.of("mandate:" + mandate.mandateNo()),
                state, "agent", workStatus(proposal.status()), proposal.createdAt(), proposal.updatedAt());
        return proposal;
    }

    @Override
    public List<AgentActionProposalEntity> findProposalsByMandateId(String mandateId) {
        return dsl.selectFrom(WORK_ITEMS)
                .where(SOURCE_TYPE.eq("agent_action_proposal"))
                .and(jsonText("mandateId").eq(mandateId))
                .orderBy(CREATED_AT.desc())
                .fetch(this::mapProposal);
    }

    @Override
    public Optional<AgentActionProposalEntity> findProposalByNo(String proposalNo) {
        return dsl.selectFrom(WORK_ITEMS)
                .where(SOURCE_TYPE.eq("agent_action_proposal"))
                .and(ITEM_NO.eq(proposalNo))
                .fetchOptional(this::mapProposal);
    }

    @Override
    public AgentActionRunEntity saveActionRun(AgentActionRunEntity run) {
        AgentActionProposalEntity proposal = proposalById(run.proposalId());
        AgentMandateEntity mandate = mandateById(proposal.mandateId());
        Map<String, Object> state = Map.of(
                "actionRunNo", run.actionRunNo(),
                "proposalId", run.proposalId(),
                "proposalNo", proposal.proposalNo(),
                "status", run.status(),
                "workItemId", run.workItemId() == null ? "" : run.workItemId(),
                "output", run.output(),
                "errorMessage", run.errorMessage() == null ? "" : run.errorMessage(),
                "completedAt", run.completedAt() == null ? "" : run.completedAt().toString());
        upsertWorkItem(run.id(), run.actionRunNo(), "agent_action_run", proposal.proposalNo(), mandate.accountId(),
                "Agent action run " + run.actionRunNo(), proposal.expectedOutcome(), List.of(), List.of("proposal:" + proposal.proposalNo()),
                state, "agent", "closed", run.createdAt(), run.completedAt() == null ? run.createdAt() : run.completedAt());
        return run;
    }

    @Override
    public List<AgentActionRunEntity> findActionRunsByProposalId(String proposalId) {
        return dsl.selectFrom(WORK_ITEMS)
                .where(SOURCE_TYPE.eq("agent_action_run"))
                .and(jsonText("proposalId").eq(proposalId))
                .orderBy(CREATED_AT.desc())
                .fetch(this::mapRun);
    }

    @Override
    public ProjectInitiativeRecommendationEntity saveProjectRecommendation(ProjectInitiativeRecommendationEntity recommendation) {
        dsl.insertInto(PROJECT_RECOMMENDATIONS)
                .set(ID, recommendation.id())
                .set(DSL.field(DSL.name("recommendation_no"), String.class), recommendation.recommendationNo())
                .set(ACCOUNT_ID, recommendation.accountId())
                .set(DSL.field(DSL.name("project_id"), String.class), recommendation.projectId())
                .set(DSL.field(DSL.name("project_no"), String.class), recommendation.projectNo())
                .set(DSL.field(DSL.name("recommendation_type"), String.class), recommendation.recommendationType())
                .set(DSL.field(DSL.name("target_key"), String.class), recommendation.targetKey())
                .set(DSL.field(DSL.name("target_role_code"), String.class), recommendation.targetRoleCode())
                .set(DSL.field(DSL.name("title"), String.class), recommendation.title())
                .set(DSL.field(DSL.name("reason"), String.class), recommendation.reason())
                .set(DSL.field(DSL.name("suggested_action"), String.class), recommendation.suggestedAction())
                .set(DSL.field(DSL.name("input"), JSONB.class), PostgresJson.jsonb(recommendation.input()))
                .set(STATUS, recommendation.status())
                .set(DSL.field(DSL.name("work_item_id"), String.class), recommendation.workItemId())
                .set(CREATED_AT, PostgresJson.offsetDateTime(recommendation.createdAt()))
                .set(UPDATED_AT, PostgresJson.offsetDateTime(recommendation.updatedAt()))
                .onConflict(DSL.field(DSL.name("project_id"), String.class), DSL.field(DSL.name("recommendation_type"), String.class), DSL.field(DSL.name("target_key"), String.class))
                .doUpdate()
                .set(DSL.field(DSL.name("title"), String.class), recommendation.title())
                .set(DSL.field(DSL.name("reason"), String.class), recommendation.reason())
                .set(DSL.field(DSL.name("suggested_action"), String.class), recommendation.suggestedAction())
                .set(DSL.field(DSL.name("input"), JSONB.class), PostgresJson.jsonb(recommendation.input()))
                .set(STATUS, recommendation.status())
                .set(DSL.field(DSL.name("work_item_id"), String.class), recommendation.workItemId())
                .set(UPDATED_AT, PostgresJson.offsetDateTime(recommendation.updatedAt()))
                .execute();
        return findProjectRecommendationByNo(recommendation.recommendationNo()).orElse(recommendation);
    }

    @Override
    public List<ProjectInitiativeRecommendationEntity> findProjectRecommendationsByProjectId(String projectId) {
        return dsl.selectFrom(PROJECT_RECOMMENDATIONS)
                .where(DSL.field(DSL.name("project_id"), String.class).eq(projectId))
                .orderBy(UPDATED_AT.desc())
                .fetch(this::mapProjectRecommendation);
    }

    @Override
    public List<ProjectInitiativeRecommendationEntity> findProjectRecommendationsByAccountId(String accountId) {
        return dsl.selectFrom(PROJECT_RECOMMENDATIONS)
                .where(ACCOUNT_ID.eq(accountId))
                .and(STATUS.in("open", "accepted"))
                .orderBy(UPDATED_AT.desc())
                .fetch(this::mapProjectRecommendation);
    }

    @Override
    public Optional<ProjectInitiativeRecommendationEntity> findProjectRecommendationByNo(String recommendationNo) {
        return dsl.selectFrom(PROJECT_RECOMMENDATIONS)
                .where(DSL.field(DSL.name("recommendation_no"), String.class).eq(recommendationNo))
                .fetchOptional(this::mapProjectRecommendation);
    }

    private void upsertWorkItem(
            String id,
            String itemNo,
            String sourceType,
            String sourceId,
            String accountId,
            String title,
            String goal,
            List<String> acceptance,
            List<String> inputRefs,
            Map<String, Object> output,
            String requiredRole,
            String status,
            Instant createdAt,
            Instant updatedAt) {
        // 中文注释：Initiative 使用 WorkItem 作为唯一事实容器，output_schema 保存原协议字段。
        dsl.insertInto(WORK_ITEMS)
                .set(ID, id)
                .set(ITEM_NO, itemNo)
                .set(SOURCE_TYPE, sourceType)
                .set(SOURCE_ID, sourceId)
                .set(ACCOUNT_ID, accountId)
                .set(TITLE, title)
                .set(GOAL, goal)
                .set(ACCEPTANCE, PostgresJson.jsonb(acceptance))
                .set(INPUT_REFS, PostgresJson.jsonb(inputRefs))
                .set(OUTPUT_SCHEMA, PostgresJson.jsonb(output))
                .set(REQUIRED_ROLE, requiredRole)
                .set(URGENCY, "attention")
                .set(STATUS, status)
                .set(READY_AT, PostgresJson.offsetDateTime(createdAt))
                .set(CREATED_AT, PostgresJson.offsetDateTime(createdAt))
                .set(UPDATED_AT, PostgresJson.offsetDateTime(updatedAt))
                .onConflict(ACCOUNT_ID, ITEM_NO)
                .doUpdate()
                .set(TITLE, title)
                .set(GOAL, goal)
                .set(ACCEPTANCE, PostgresJson.jsonb(acceptance))
                .set(INPUT_REFS, PostgresJson.jsonb(inputRefs))
                .set(OUTPUT_SCHEMA, PostgresJson.jsonb(output))
                .set(REQUIRED_ROLE, requiredRole)
                .set(STATUS, status)
                .set(UPDATED_AT, PostgresJson.offsetDateTime(updatedAt))
                .execute();
    }

    private AgentMandateEntity mapMandate(Record record) {
        Map<String, Object> state = state(record);
        return new AgentMandateEntity(
                record.get(ID),
                string(state, "mandateNo", record.get(ITEM_NO)),
                record.get(ACCOUNT_ID),
                string(state, "goal", record.get(GOAL)),
                stringList(state.get("scope")),
                map(state.get("budget")),
                map(state.get("riskPolicy")),
                string(state, "status", statusFromWork(record)),
                PostgresJson.instant(record.get(CREATED_AT)),
                PostgresJson.instant(record.get(UPDATED_AT)));
    }

    private AgentOpportunityEntity mapOpportunity(Record record) {
        Map<String, Object> state = state(record);
        return new AgentOpportunityEntity(
                record.get(ID),
                string(state, "opportunityNo", record.get(ITEM_NO)),
                string(state, "mandateId", ""),
                string(state, "type", "agent_signal"),
                string(state, "reason", record.get(GOAL)),
                string(state, "targetType", "mandate"),
                string(state, "targetId", record.get(SOURCE_ID)),
                string(state, "suggestedAction", ""),
                string(state, "status", statusFromWork(record)),
                PostgresJson.instant(record.get(CREATED_AT)),
                PostgresJson.instant(record.get(UPDATED_AT)));
    }

    private AgentActionProposalEntity mapProposal(Record record) {
        Map<String, Object> state = state(record);
        return new AgentActionProposalEntity(
                record.get(ID),
                string(state, "proposalNo", record.get(ITEM_NO)),
                blankToNull(string(state, "opportunityId", "")),
                string(state, "mandateId", ""),
                string(state, "actionId", ""),
                string(state, "reason", record.get(TITLE)),
                string(state, "risk", "low"),
                map(state.get("input")),
                string(state, "expectedOutcome", record.get(GOAL)),
                string(state, "status", statusFromWork(record)),
                PostgresJson.instant(record.get(CREATED_AT)),
                PostgresJson.instant(record.get(UPDATED_AT)));
    }

    private AgentActionRunEntity mapRun(Record record) {
        Map<String, Object> state = state(record);
        return new AgentActionRunEntity(
                record.get(ID),
                string(state, "actionRunNo", record.get(ITEM_NO)),
                string(state, "proposalId", ""),
                string(state, "status", "succeeded"),
                blankToNull(string(state, "workItemId", "")),
                map(state.get("output")),
                blankToNull(string(state, "errorMessage", "")),
                PostgresJson.instant(record.get(CREATED_AT)),
                instantValue(state.get("completedAt"), PostgresJson.instant(record.get(UPDATED_AT))));
    }

    private ProjectInitiativeRecommendationEntity mapProjectRecommendation(Record record) {
        return new ProjectInitiativeRecommendationEntity(
                record.get(ID),
                record.get(DSL.field(DSL.name("recommendation_no"), String.class)),
                record.get(ACCOUNT_ID),
                record.get(DSL.field(DSL.name("project_id"), String.class)),
                record.get(DSL.field(DSL.name("project_no"), String.class)),
                record.get(DSL.field(DSL.name("recommendation_type"), String.class)),
                record.get(DSL.field(DSL.name("target_key"), String.class)),
                record.get(DSL.field(DSL.name("target_role_code"), String.class)),
                record.get(DSL.field(DSL.name("title"), String.class)),
                record.get(DSL.field(DSL.name("reason"), String.class)),
                record.get(DSL.field(DSL.name("suggested_action"), String.class)),
                PostgresJson.jsonbValue(record.get(DSL.field(DSL.name("input"), JSONB.class)), OBJECT_MAP, Map.of()),
                record.get(STATUS),
                record.get(DSL.field(DSL.name("work_item_id"), String.class)),
                PostgresJson.instant(record.get(CREATED_AT)),
                PostgresJson.instant(record.get(UPDATED_AT)));
    }

    private AgentMandateEntity mandateById(String mandateId) {
        return dsl.selectFrom(WORK_ITEMS)
                .where(SOURCE_TYPE.eq("agent_mandate"))
                .and(ID.eq(mandateId))
                .fetchOptional(this::mapMandate)
                .orElseThrow(() -> new IllegalStateException("Agent mandate not found: " + mandateId));
    }

    private AgentActionProposalEntity proposalById(String proposalId) {
        return dsl.selectFrom(WORK_ITEMS)
                .where(SOURCE_TYPE.eq("agent_action_proposal"))
                .and(ID.eq(proposalId))
                .fetchOptional(this::mapProposal)
                .orElseThrow(() -> new IllegalStateException("Agent proposal not found: " + proposalId));
    }

    private String opportunityNo(String opportunityId) {
        if (opportunityId == null || opportunityId.isBlank()) {
            return null;
        }
        return findOpportunityById(opportunityId).map(AgentOpportunityEntity::opportunityNo).orElse(null);
    }

    private Map<String, Object> state(Record record) {
        return PostgresJson.jsonbValue(record.get(OUTPUT_SCHEMA), OBJECT_MAP, Map.of());
    }

    private Field<String> jsonText(String key) {
        return DSL.field("{0}->>{1}", String.class, OUTPUT_SCHEMA, DSL.inline(key));
    }

    private String statusFromWork(Record record) {
        return "closed".equals(record.get(STATUS)) ? "closed" : "active";
    }

    private String workStatus(String domainStatus) {
        if (List.of("closed", "dismissed", "rejected", "executed").contains(domainStatus)) {
            return "closed";
        }
        if ("approved".equals(domainStatus)) {
            return "submitted";
        }
        return "ready";
    }

    private String title(String value) {
        if (value == null || value.isBlank()) {
            return "Agent initiative";
        }
        String text = value.trim();
        return text.length() <= 160 ? text : text.substring(0, 160);
    }

    private String string(Map<String, Object> state, String key, String defaultValue) {
        Object value = state.get(key);
        return value instanceof String text && !text.isBlank() ? text : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        if (value instanceof JSONB jsonb) {
            return PostgresJson.jsonbValue(jsonb, STRING_LIST, List.of());
        }
        return List.of();
    }

    private Instant instantValue(Object value, Instant defaultValue) {
        if (value instanceof String text && !text.isBlank()) {
            return Instant.parse(text);
        }
        return defaultValue;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
