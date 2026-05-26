package com.monopolyfun;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AgentTurnApiTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSchema() {
        jdbcTemplate.execute("truncate table business_id_sequences, work_runs, work_receipts, work_reviews, work_events, work_items, workbench_dismissals, accounts cascade");
        jdbcTemplate.update("""
                insert into accounts (id, handle, display_name, metadata)
                 values ('acct-agent', '@agent', 'Agent Runner', '{}'::jsonb)
                """);
    }

    @Test
    void homeTurnReturnsRuntimeEntryProjection() throws Exception {
        mockMvc.perform(post("/api/v1/agent/turn")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-agent"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "intent": "view",
                                  "scene": "home"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scene").value("home"))
                .andExpect(jsonPath("$.state").value("ready"))
                .andExpect(jsonPath("$.projection.summary.accountId").value("acct-agent"))
                .andExpect(jsonPath("$.projection.refs.workbench").value("/api/v1/workbench"))
                .andExpect(jsonPath("$.actions[0].id").value("open_workbench"))
                .andExpect(jsonPath("$.nextTurn.scene").value("workbench"));
    }

    @Test
    void workbenchTurnReturnsCurrentItemActionsAndApiOperation() throws Exception {
        seedWorkbenchItem("wb-delivery-result-MF260525ORD000001X");

        mockMvc.perform(post("/api/v1/agent/turn")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-agent"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "intent": "view",
                                  "scene": "workbench"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scene").value("workbench"))
                .andExpect(jsonPath("$.state").value("ready"))
                .andExpect(jsonPath("$.subject.type").value("workbench_item"))
                .andExpect(jsonPath("$.projection.counts.items").value(1))
                .andExpect(jsonPath("$.projection.current.id").value("wb-delivery-result-MF260525ORD000001X"))
                .andExpect(jsonPath("$.actions", hasSize(2)))
                .andExpect(jsonPath("$.actions[0].id").value("open"))
                .andExpect(jsonPath("$.actions[1].id").value("claim_work_item"))
                .andExpect(jsonPath("$.actions[1].apiOperation.operationId").value("claimWorkItem"))
                .andExpect(jsonPath("$.actions[1].apiOperation.pathParams.itemId").value("wb-delivery-result-MF260525ORD000001X"))
                .andExpect(jsonPath("$.actions[1].nextTurn.scene").value("workbench"));
    }

    private void seedWorkbenchItem(String itemNo) {
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);
        jdbcTemplate.update("""
                insert into work_items (
                    id, item_no, source_type, source_id, account_id, title, goal,
                    acceptance_criteria, input_refs, output_schema, required_role, required_capability,
                    urgency, status, claim_expires_at, ready_at, created_at, updated_at
                )
                values (?, ?, 'order', 'MF260525ORD000001X', 'acct-agent', '提交交付结果',
                    '买方付款已确认，请提交交付内容供验收。', '[]'::jsonb, '[]'::jsonb,
                    '{"action":"delivery_result_due"}'::jsonb, 'fulfiller', null,
                    'attention', 'ready', null, ?, ?, ?)
                """, "wi-" + itemNo, itemNo, timestamp, timestamp, timestamp);
    }
}
