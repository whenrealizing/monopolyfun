package com.monopolyfun;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PublicAccountLookupApiTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSchema() {
        jdbcTemplate.execute("truncate table spring_session_attributes, spring_session, identity_badges, identity_facts, accounts cascade");
        insertAccount("acct-lead", "@lead", "Lead Builder");
        insertAccount("acct-other", "other", "Other Builder");
    }

    @Test
    void lookupPublicAccountsSupportsSingleId() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/lookup").param("ids", "lead"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("lead"))
                .andExpect(jsonPath("$[0].handle").value("@lead"))
                .andExpect(jsonPath("$[0].displayName").value("Lead Builder"));
    }

    @Test
    void lookupPublicAccountsSupportsMultipleIdsAndPreservesRequestedOrder() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/lookup")
                        .param("ids", "@other", "missing", "lead"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("other"))
                .andExpect(jsonPath("$[0].handle").value("other"))
                .andExpect(jsonPath("$[1].id").value("lead"))
                .andExpect(jsonPath("$[1].handle").value("@lead"));
    }

    private void insertAccount(String id, String handle, String displayName) {
        jdbcTemplate.update("""
                        insert into accounts (id, handle, display_name, password_hash, metadata, created_at, updated_at)
                        values (?, ?, ?, null, '{}'::jsonb, ?, ?)
                        """,
                id,
                handle,
                displayName,
                timestamp("2026-05-08T00:00:00Z"),
                timestamp("2026-05-08T00:00:00Z"));
    }

    private Timestamp timestamp(String value) {
        return Timestamp.from(Instant.parse(value));
    }
}
