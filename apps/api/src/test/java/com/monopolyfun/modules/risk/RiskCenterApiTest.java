package com.monopolyfun;

import com.monopolyfun.modules.project.service.RootProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RiskCenterApiTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RootProjectService rootProjectService;

    @BeforeEach
    void seedAccounts() {
        jdbcTemplate.execute("""
                truncate table spring_session_attributes, spring_session, audit_events, risk_events,
                project_roles, projects, markets, password_reset_tokens, accounts cascade
                """);
        insertAccount("acct-admin", "admin", "Admin");
        insertAccount("acct-target", "target", "Target");
        insertAccount("acct-bruteforce", "bruteforce", "Bruteforce");
        rootProjectService.ensureRootProject("acct-admin");
    }

    @Test
    void manualFreezeBlocksRiskyAccountLoginAndRecordsEvents() throws Exception {
        mockMvc.perform(post("/api/v1/backoffice/risk/accounts/acct-target/freeze")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "manual risk review",
                                  "freezeHours": 24
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("frozen"))
                .andExpect(jsonPath("$.riskLevel").value("high"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "handle": "target",
                                  "password": "password-123"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("risk.account.frozen"));

        Integer eventCount = jdbcTemplate.queryForObject(
                "select count(*) from risk_events where subject_id = 'acct-target' and kind = 'manual_freeze'",
                Integer.class);
        assertTrue(eventCount != null && eventCount == 1);
    }

    @Test
    void repeatedLoginFailuresFreezeAccount() throws Exception {
        for (int index = 0; index < 7; index++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "handle": "bruteforce",
                                      "password": "wrong-password"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "handle": "bruteforce",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("risk.account.frozen"));

        String status = jdbcTemplate.queryForObject(
                "select status from accounts where id = 'acct-bruteforce'",
                String.class);
        assertEquals("frozen", status);
    }

    private void insertAccount(String id, String handle, String displayName) {
        jdbcTemplate.update("""
                        insert into accounts (id, handle, display_name, password_hash, metadata, created_at, updated_at)
                        values (?, ?, ?, ?, '{}'::jsonb, ?, ?)
                        """,
                id,
                handle,
                displayName,
                passwordEncoder.encode("password-123"),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
    }
}
