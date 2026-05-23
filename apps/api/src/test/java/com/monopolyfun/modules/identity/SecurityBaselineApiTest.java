package com.monopolyfun;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SecurityBaselineApiTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedAccounts() {
        jdbcTemplate.execute("truncate table spring_session_attributes, spring_session, audit_events, risk_events, password_reset_tokens, accounts cascade");
        insertAccount("acct-lead", "lead", "Lead");
        insertAccount("acct-basic", "basic", "Basic");
    }

    @Test
    void loginIssuesSpringSessionCookieAndPersistsPrincipalInPostgres() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "handle": "lead",
                                  "password": "password-123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("MONOPOLYFUN_SESSION", true))
                .andExpect(cookie().exists("MONOPOLYFUN_CSRF"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.tokenType").value("Cookie"))
                .andReturn();

        Integer sessionCount = jdbcTemplate.queryForObject(
                "select count(*) from spring_session where principal_name = ?",
                Integer.class,
                "acct-lead");
        assertTrue(sessionCount != null && sessionCount == 1);

        Cookie sessionCookie = result.getResponse().getCookie("MONOPOLYFUN_SESSION");
        mockMvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.id").value("acct-lead"));
    }

    @Test
    void logoutInvalidatesSpringSession() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "handle": "lead",
                                  "password": "password-123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        Cookie sessionCookie = login.getResponse().getCookie("MONOPOLYFUN_SESSION");
        Cookie csrfCookie = login.getResponse().getCookie("MONOPOLYFUN_CSRF");
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(sessionCookie, csrfCookie)
                        .header("X-CSRF-Token", csrfCookie.getValue()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accountHeaderDoesNotAuthenticateRequest() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("X-Account-Id", "acct-lead"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void backofficeRejectsAnonymousAndBasicSessions() throws Exception {
        mockMvc.perform(get("/api/v1/backoffice"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/backoffice").with(SecurityTestSupport.session(jdbcTemplate, "acct-basic")))
                .andExpect(status().isForbidden());
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
